package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Paginated GUI that lists all {@link ShopItem}s belonging to a single
 * {@link ShopCategory}.
 *
 * <p>Each item card shows dynamic buy/sell prices, a visual stock bar, and a
 * price-trend indicator.  Left-clicking an item opens the buy
 * {@link ConfirmGui}; right-clicking opens the sell {@link ConfirmGui}.</p>
 *
 * <p>An optional "Sell All" button is placed at slot 48 when the category
 * has sell-enabled items and the feature is switched on in config.</p>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
public class ShopItemGui extends PaginatedGui {

    /** The shop that owns this category. */
    private final Shop shop;

    /** The category whose items are being displayed. */
    private final ShopCategory category;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code ShopItemGui}.
     *
     * @param plugin   the owning plugin instance
     * @param viewer   the player viewing the GUI
     * @param shop     the parent shop
     * @param category the category to display
     */
    public ShopItemGui(PremiumShopPlugin plugin, Player viewer, Shop shop, ShopCategory category) {
        super(plugin, viewer,
                "&6&l" + (category != null ? category.getDisplayName() : "Items"),
                54);
        if (shop == null) throw new IllegalArgumentException("shop must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        this.shop = shop;
        this.category = category;

        // Content slots: rows 0-4, columns 0-8 — slots 0-44
        // Navigation row is row 5 (slots 45-53)
        int[] content = new int[45];
        for (int i = 0; i < 45; i++) content[i] = i;
        setContentSlots(content);
        setNavSlots(45, 53, 49);
    }

    // -------------------------------------------------------------------------
    // PaginatedGui implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Builds one display entry per {@link ShopItem} in the category.
     * Left-click → buy confirm; right-click → sell confirm.</p>
     */
    @Override
    public List<Map.Entry<ItemStack, GuiAction>> getPageItems() {
        List<Map.Entry<ItemStack, GuiAction>> entries = new ArrayList<>();
        List<ShopItem> items = category.getItems();
        if (items == null) return entries;

        for (ShopItem shopItem : items) {
            ItemStack display = buildDisplayItem(shopItem);
            GuiAction action = event -> {
                boolean rightClick = event.isRightClick();
                if (rightClick) {
                    // Sell flow
                    if (!shopItem.isSellable()) {
                        viewer.sendMessage(colorizeComponent(
                                "&cThis item cannot be sold here."));
                        return;
                    }
                    double sellPrice = plugin.getShopManager()
                            .getDynamicSellPrice(shop.getId(), shopItem);
                    ConfirmGui confirmGui = new ConfirmGui(
                            plugin, viewer, shopItem, shop.getId(), 1, false, sellPrice);
                    confirmGui.open();
                } else {
                    // Buy flow
                    if (!shopItem.isBuyable()) {
                        viewer.sendMessage(colorizeComponent(
                                "&cThis item is not for sale."));
                        return;
                    }
                    double buyPrice = plugin.getShopManager()
                            .getDynamicBuyPrice(shop.getId(), shopItem);
                    ConfirmGui confirmGui = new ConfirmGui(
                            plugin, viewer, shopItem, shop.getId(), 1, true, buyPrice);
                    confirmGui.open();
                }
            };
            entries.add(new AbstractMap.SimpleImmutableEntry<>(display, action));
        }
        return entries;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls super to render page content, then optionally adds the
     * "Sell All" button at slot 48.</p>
     */
    @Override
    protected void populateInventory() {
        super.populateInventory();

        // "Sell All" button — slot 48 (between nav buttons)
        if (plugin.getConfigManager().isSellAllEnabled() && hasSellableItems()) {
            ItemStack sellAll = createItem(
                    Material.HOPPER,
                    "&c&lSell All",
                    "&7Sell all matching items in",
                    "&7your inventory at current prices.",
                    "&8─────────────────",
                    "&eClick to sell all!"
            );
            setItem(48, sellAll, event -> handleSellAll());
        }

        // Back button — slot 45's left neighbour / slot 46
        ItemStack back = createItem(
                Material.BARRIER,
                "&c&l← Back",
                "&7Return to categories"
        );
        setItem(46, back, event -> {
            ShopCategoryGui catGui = new ShopCategoryGui(plugin, viewer, shop);
            catGui.open();
        });
    }

    // -------------------------------------------------------------------------
    // Item display helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the display {@link ItemStack} for a shop item card, including
     * dynamic pricing, stock bar, and trend indicator in the lore.
     *
     * @param shopItem the shop item to build the card for
     * @return the fully decorated display item
     */
    private ItemStack buildDisplayItem(ShopItem shopItem) {
        List<String> lore = buildItemLore(shopItem, shop.getId());
        Material mat = shopItem.getMaterial() != null ? shopItem.getMaterial() : Material.PAPER;
        ItemStack icon = new ItemStack(mat, Math.max(1, shopItem.getAmount()));
        org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(colorizeComponent("&f&l" + shopItem.getDisplayName()));
            List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(colorizeComponent(line));
            }
            meta.lore(loreComponents);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /**
     * Constructs the rich lore string list for a shop item.
     *
     * <p>Lore sections:</p>
     * <ol>
     *   <li>Separator line</li>
     *   <li>Buy price (or "Not for sale")</li>
     *   <li>Sell price (or "Cannot sell")</li>
     *   <li>Stock bar</li>
     *   <li>Price trend indicator</li>
     *   <li>Usage hint</li>
     * </ol>
     *
     * @param item   the shop item
     * @param shopId the ID of the parent shop (used for dynamic pricing lookups)
     * @return an ordered list of lore lines with {@code &} colour codes
     */
    public List<String> buildItemLore(ShopItem item, String shopId) {
        List<String> lore = new ArrayList<>();
        lore.add("&8─────────────────");

        // Buy price
        if (item.isBuyable()) {
            double buyPrice = plugin.getShopManager().getDynamicBuyPrice(shopId, item);
            lore.add("&7Buy:  &a$" + formatPrice(buyPrice));
        } else {
            lore.add("&7Buy:  &cNot for sale");
        }

        // Sell price
        if (item.isSellable()) {
            double sellPrice = plugin.getShopManager().getDynamicSellPrice(shopId, item);
            lore.add("&7Sell: &e$" + formatPrice(sellPrice));
        } else {
            lore.add("&7Sell: &cCannot sell");
        }

        lore.add("&8─────────────────");

        // Stock bar
        lore.add("&7Stock: " + stockBar(item));

        // Price trend
        lore.add("&7Trend: " + priceTrendIndicator(shopId, item));

        lore.add("&8─────────────────");
        lore.add("&eLeft-click &7to &abuy");
        lore.add("&eRight-click &7to &csell");

        return lore;
    }

    /**
     * Renders a 10-character Unicode block stock bar representing how full the
     * item's stock is relative to its configured maximum.
     *
     * <p>Example output: {@code &a▓▓▓▓▓░░░░░ &750/100}</p>
     *
     * @param item the shop item whose stock level should be visualised
     * @return a colour-coded stock bar string
     */
    public String stockBar(ShopItem item) {
        int stock    = item.getCurrentStock();
        int maxStock = item.getMaxStock();

        if (maxStock <= 0) {
            // Unlimited stock
            return "&a▓▓▓▓▓▓▓▓▓▓ &7∞";
        }

        double ratio = (double) stock / maxStock;
        int filled   = (int) Math.round(ratio * 10);
        filled = Math.max(0, Math.min(10, filled));

        StringBuilder bar = new StringBuilder();
        String fillColor  = ratio > 0.5 ? "&a" : (ratio > 0.2 ? "&e" : "&c");
        bar.append(fillColor);
        for (int i = 0; i < filled; i++)      bar.append("▓");
        bar.append("&8");
        for (int i = filled; i < 10; i++)     bar.append("░");
        bar.append(" &7").append(stock).append("/").append(maxStock);
        return bar.toString();
    }

    /**
     * Returns a coloured trend arrow indicating whether this item's price has
     * risen, fallen, or remained stable relative to its base price.
     *
     * @param shopId the shop ID for the dynamic pricing lookup
     * @param item   the shop item to evaluate
     * @return a colour-coded trend string, e.g. {@code &a▲ Rising"} or
     *         {@code &c▼ Falling"}
     */
    private String priceTrendIndicator(String shopId, ShopItem item) {
        if (!item.isBuyable()) return "&7─ N/A";
        double base    = item.getBuyPrice();
        double current = plugin.getShopManager().getDynamicBuyPrice(shopId, item);
        double diff    = current - base;
        double pct     = base > 0 ? (diff / base) * 100.0 : 0;

        if (pct > 2.0)       return "&a▲ Rising (" + String.format("%.1f", pct) + "%)";
        else if (pct < -2.0) return "&c▼ Falling (" + String.format("%.1f", Math.abs(pct)) + "%)";
        else                 return "&7─ Stable";
    }

    /**
     * Sells all items in the viewer's inventory that are present in this
     * category at their current dynamic sell price, depositing the total
     * into the player's economy balance.
     */
    private void handleSellAll() {
        if (!hasSellableItems()) {
            viewer.sendMessage(colorizeComponent(
                    "&cThere are no sellable items in this category."));
            return;
        }
        double totalEarned = plugin.getShopManager()
                .sellAll(viewer, shop.getId(), category);
        if (totalEarned <= 0) {
            viewer.sendMessage(colorizeComponent(
                    "&cYou have no sellable items from this category in your inventory."));
        } else {
            viewer.sendMessage(colorizeComponent(
                    "&aSold all items for &e$" + formatPrice(totalEarned) + "&a!"));
        }
        // Refresh the GUI to reflect updated stock
        refresh();
    }

    /**
     * Returns {@code true} if this category contains at least one item that
     * can be sold by players.
     *
     * @return {@code true} when any item in the category is sellable
     */
    private boolean hasSellableItems() {
        List<ShopItem> items = category.getItems();
        if (items == null) return false;
        return items.stream().anyMatch(ShopItem::isSellable);
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    /**
     * Formats a price value as a human-readable string.
     *
     * <p>Values with no fractional part are shown without decimals;
     * otherwise two decimal places are used.</p>
     *
     * @param price the price to format
     * @return a formatted price string
     */
    private static String formatPrice(double price) {
        if (price == Math.floor(price) && !Double.isInfinite(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }

    /**
     * Resolves a material name string to a {@link Material}, falling back to
     * {@code fallback} when the name is invalid or {@code null}.
     *
     * @param materialName the material name from config
     * @param fallback     the fallback material
     * @return a valid, non-{@code AIR} {@link Material}
     */
    private Material resolveMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isEmpty()) return fallback;
        try {
            Material m = Material.valueOf(materialName.toUpperCase());
            return m == Material.AIR ? fallback : m;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
