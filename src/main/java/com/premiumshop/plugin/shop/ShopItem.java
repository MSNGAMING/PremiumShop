package com.premiumshop.plugin.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Represents a single purchasable or sellable item within a {@link ShopCategory}.
 *
 * <p>A {@code ShopItem} tracks both static configuration (base prices, stock
 * limits, buy/sell flags) and dynamic runtime state (current stock level,
 * transaction velocity) used by the {@code DynamicPriceEngine}.
 *
 * <p>All mutable state is updated through dedicated setters or helper methods
 * ({@link #adjustStock(int)}, {@link #incrementVelocity()},
 * {@link #resetVelocity()}) to allow for future synchronisation hooks.
 *
 * @author PremiumShop Development Team
 */
public final class ShopItem {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Unique identifier for this item within its parent category. */
    private final String id;

    /** The ItemStack shown in the GUI and given to the buyer. */
    private final ItemStack displayItem;

    /** Base buy price before dynamic adjustments. */
    private double baseBuyPrice;

    /** Base sell price before dynamic adjustments. */
    private double baseSellPrice;

    /** Maximum units of stock this item can hold; {@code -1} means unlimited. */
    private int maxStock;

    /** Current units available for purchase. */
    private int currentStock;

    /** Epoch-millisecond timestamp of the most recent buy or sell transaction. */
    private long lastTransactionTime;

    /**
     * Number of transactions executed within the current velocity-window.
     * Reset periodically by the {@code DynamicPriceEngine}.
     */
    private int transactionVelocity;

    /** Whether players can buy this item from the shop. */
    private boolean buyable;

    /** Whether players can sell this item to the shop. */
    private boolean sellable;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Full constructor used both by the factory method and programmatically.
     *
     * @param id                  unique item ID within its category; must not be {@code null}
     * @param displayItem         the representative {@link ItemStack}; must not be {@code null}
     * @param baseBuyPrice        buy price in the server's economy currency (≥ 0)
     * @param baseSellPrice       sell price in the server's economy currency (≥ 0)
     * @param maxStock            maximum stock units; use {@code -1} for unlimited
     * @param currentStock        initial stock level; clamped to [{@code 0}, {@code maxStock}] automatically
     * @param lastTransactionTime epoch-ms of the last transaction (may be {@code 0} for new items)
     * @param transactionVelocity initial velocity counter (typically {@code 0})
     * @param buyable             whether this item can be bought
     * @param sellable            whether this item can be sold
     */
    public ShopItem(
            final String    id,
            final ItemStack displayItem,
            final double    baseBuyPrice,
            final double    baseSellPrice,
            final int       maxStock,
            final int       currentStock,
            final long      lastTransactionTime,
            final int       transactionVelocity,
            final boolean   buyable,
            final boolean   sellable) {

        Objects.requireNonNull(id,          "ShopItem id must not be null");
        Objects.requireNonNull(displayItem, "ShopItem displayItem must not be null");

        this.id                  = id;
        this.displayItem         = displayItem.clone();   // defensive copy
        this.baseBuyPrice        = Math.max(0, baseBuyPrice);
        this.baseSellPrice       = Math.max(0, baseSellPrice);
        this.maxStock            = maxStock;
        this.lastTransactionTime = lastTransactionTime;
        this.transactionVelocity = Math.max(0, transactionVelocity);
        this.buyable             = buyable;
        this.sellable            = sellable;

        // Clamp supplied currentStock
        this.currentStock = clamp(currentStock, 0, maxStock >= 0 ? maxStock : Integer.MAX_VALUE);
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** @return the item's unique identifier */
    public String getId() { return id; }

    /**
     * Returns a defensive copy of the display {@link ItemStack}.
     *
     * @return a clone of the internal item stack
     */
    public ItemStack getDisplayItem() { return displayItem.clone(); }

    /** @return the base buy price (before dynamic adjustment) */
    public double getBaseBuyPrice()  { return baseBuyPrice;  }

    public double getBuyPrice() { return baseBuyPrice; }

    /** @return the base sell price (before dynamic adjustment) */
    public double getBaseSellPrice() { return baseSellPrice; }

    public double getSellPrice() { return baseSellPrice; }

    public org.bukkit.Material getMaterial() {
        return displayItem != null ? displayItem.getType() : org.bukkit.Material.PAPER;
    }

    public int getAmount() {
        return displayItem != null ? displayItem.getAmount() : 1;
    }

    public String getDisplayName() {
        if (displayItem != null && displayItem.hasItemMeta() && displayItem.getItemMeta().hasDisplayName()) {
            return displayItem.getItemMeta().getDisplayName();
        }
        return displayItem != null ? ShopCatalogGenerator.formatDisplayName(displayItem.getType()) : id;
    }

    /** @return the maximum stock level, or {@code -1} for unlimited */
    public int getMaxStock() { return maxStock; }

    /** @return the current stock level */
    public int getCurrentStock() { return currentStock; }

    /** @return epoch-ms timestamp of the most recent transaction */
    public long getLastTransactionTime() { return lastTransactionTime; }

    /** @return the transaction velocity count within the current window */
    public int getTransactionVelocity() { return transactionVelocity; }

    /** @return {@code true} if players may buy this item */
    public boolean isBuyable() { return buyable; }

    /** @return {@code true} if players may sell this item */
    public boolean isSellable() { return sellable; }

    // -----------------------------------------------------------------------
    // Setters (mutable runtime state only)
    // -----------------------------------------------------------------------

    /**
     * Sets the current stock level, clamping to [0, maxStock].
     *
     * @param currentStock the new stock level
     */
    public void setCurrentStock(final int currentStock) {
        this.currentStock = clamp(currentStock, 0, maxStock >= 0 ? maxStock : Integer.MAX_VALUE);
    }

    public void setBaseBuyPrice(final double price) {
        this.baseBuyPrice = Math.max(0.0, price);
    }

    public void setBaseSellPrice(final double price) {
        this.baseSellPrice = Math.max(0.0, price);
    }

    public void setMaxStock(final int stock) {
        this.maxStock = stock;
    }

    public void setBuyable(final boolean buyable) {
        this.buyable = buyable;
    }

    public void setSellable(final boolean sellable) {
        this.sellable = sellable;
    }

    /**
     * Records the epoch-ms timestamp of the most recent transaction.
     *
     * @param lastTransactionTime epoch-millisecond value
     */
    public void setLastTransactionTime(final long lastTransactionTime) {
        this.lastTransactionTime = lastTransactionTime;
    }

    /**
     * Directly sets the transaction velocity counter.
     * Prefer {@link #incrementVelocity()} and {@link #resetVelocity()} for
     * normal engine use.
     *
     * @param transactionVelocity the new velocity count (clamped to ≥ 0)
     */
    public void setTransactionVelocity(final int transactionVelocity) {
        this.transactionVelocity = Math.max(0, transactionVelocity);
    }

    // -----------------------------------------------------------------------
    // Stock helpers
    // -----------------------------------------------------------------------

    /**
     * Adjusts the current stock by {@code delta} (positive = restock,
     * negative = consume) and clamps the result to [0, maxStock].
     *
     * @param delta the amount to add (may be negative)
     */
    public void adjustStock(final int delta) {
        int max = maxStock >= 0 ? maxStock : Integer.MAX_VALUE;
        currentStock = clamp(currentStock + delta, 0, max);
    }

    // -----------------------------------------------------------------------
    // Velocity helpers
    // -----------------------------------------------------------------------

    /**
     * Increments the transaction velocity counter by one and updates the
     * {@link #lastTransactionTime} to the current wall-clock time.
     */
    public void incrementVelocity() {
        transactionVelocity++;
        lastTransactionTime = System.currentTimeMillis();
    }

    /**
     * Resets the transaction velocity counter to zero.
     * Called by the {@code DynamicPriceEngine} at the end of each scoring window.
     */
    public void resetVelocity() {
        transactionVelocity = 0;
    }

    // -----------------------------------------------------------------------
    // Serialisation
    // -----------------------------------------------------------------------

    /**
     * Serialises this {@code ShopItem} into the provided
     * {@link ConfigurationSection}.
     *
     * @param section the YAML section to write into; must not be {@code null}
     */
    public void toConfigSection(final ConfigurationSection section) {
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        section.set("display-item",          displayItem.serialize());
        section.set("material",              displayItem.getType().name());
        section.set("base-buy-price",        baseBuyPrice);
        section.set("base-sell-price",       baseSellPrice);
        section.set("max-stock",             maxStock);
        section.set("current-stock",         currentStock);
        section.set("last-transaction-time", lastTransactionTime);
        section.set("transaction-velocity",  transactionVelocity);
        section.set("buyable",               buyable);
        section.set("sellable",              sellable);
    }

    /**
     * Deserialises a {@code ShopItem} from a {@link ConfigurationSection}.
     *
     * @param id      the item ID (taken from the section key, not stored inside the section)
     * @param section the YAML section to read from; must not be {@code null}
     * @return the constructed {@code ShopItem}
     * @throws IllegalArgumentException if the section is missing required fields
     */
    public static ShopItem fromConfigSection(final String id, final ConfigurationSection section) {
        Objects.requireNonNull(id,      "Item id must not be null");
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        ItemStack displayItem = null;
        if (section.isItemStack("display-item")) {
            displayItem = section.getItemStack("display-item");
        } else if (section.isConfigurationSection("display-item")) {
            ConfigurationSection disSec = section.getConfigurationSection("display-item");
            if (disSec != null) {
                try {
                    displayItem = ItemStack.deserialize(disSec.getValues(false));
                } catch (Exception ignored) {}
            }
        } else {
            Object rawItemObj = section.get("display-item");
            if (rawItemObj instanceof ItemStack is) {
                displayItem = is;
            } else if (rawItemObj instanceof java.util.Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> castMap = (java.util.Map<String, Object>) map;
                try {
                    displayItem = ItemStack.deserialize(castMap);
                } catch (Exception ignored) {}
            }
        }

        if (displayItem == null || displayItem.getType().isAir()) {
            String matName = section.getString("material");
            org.bukkit.Material mat = null;
            if (matName != null && !matName.isBlank()) {
                mat = org.bukkit.Material.matchMaterial(matName);
            }
            if (mat == null) {
                mat = org.bukkit.Material.matchMaterial(id.toUpperCase(java.util.Locale.ROOT));
            }
            if (mat == null || mat.isAir()) {
                mat = org.bukkit.Material.STONE;
            }
            displayItem = new ItemStack(mat);

            org.bukkit.inventory.meta.ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                if (section.contains("display-name")) {
                    meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            section.getString("display-name")));
                }
                if (section.contains("lore")) {
                    java.util.List<String> rawLore = section.getStringList("lore");
                    java.util.List<String> coloredLore = new java.util.ArrayList<>();
                    for (String line : rawLore) {
                        coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
                    }
                    meta.setLore(coloredLore);
                }
                displayItem.setItemMeta(meta);
            }
        }

        double  baseBuyPrice        = section.getDouble("base-buy-price",        0.0);
        double  baseSellPrice       = section.getDouble("base-sell-price",       0.0);
        int     maxStock            = section.getInt   ("max-stock",             -1);
        int     currentStock        = section.getInt   ("current-stock",          0);
        long    lastTransactionTime = section.getLong  ("last-transaction-time",  0L);
        int     transactionVelocity = section.getInt   ("transaction-velocity",   0);
        boolean buyable             = section.getBoolean("buyable",              true);
        boolean sellable            = section.getBoolean("sellable",             true);

        return new ShopItem(
                id,
                displayItem,
                baseBuyPrice,
                baseSellPrice,
                maxStock,
                currentStock,
                lastTransactionTime,
                transactionVelocity,
                buyable,
                sellable);
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "ShopItem{id='" + id + "', buy=" + baseBuyPrice
                + ", sell=" + baseSellPrice + ", stock=" + currentStock + "/" + maxStock + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopItem other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // -----------------------------------------------------------------------
    // Private utilities
    // -----------------------------------------------------------------------

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
