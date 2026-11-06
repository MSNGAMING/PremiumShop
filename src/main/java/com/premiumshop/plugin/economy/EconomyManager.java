package com.premiumshop.plugin.economy;

import com.premiumshop.plugin.PremiumShopPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Facade over all economy operations performed by PremiumShop.
 *
 * <p>Supports two currency backends:
 * <ul>
 *   <li><b>VAULT</b> – delegates to whatever Economy provider is registered
 *       with the Bukkit {@link org.bukkit.plugin.ServicesManager}.</li>
 *   <li><b>ITEM</b> – treats a configurable {@link ItemStack} as the currency
 *       unit.  Balances are computed by scanning the player's inventory.</li>
 * </ul>
 *
 * <p>Call {@link #initialize()} once during plugin enable, before any other
 * method is used.
 */
public final class EconomyManager {

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Economy backend types supported by this manager.
     */
    public enum CurrencyType {
        VAULT,
        ITEM
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PremiumShopPlugin plugin;

    private Economy        vaultEconomy;
    private CurrencyType   type;
    private Material       itemCurrencyMaterial;
    private String         itemCurrencyName;
    private int            itemCurrencyCustomModelData;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs an {@code EconomyManager} bound to the given plugin.
     *
     * @param plugin the owning {@link PremiumShopPlugin}
     */
    public EconomyManager(final PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Reads configuration and sets up the chosen economy backend.
     *
     * @throws IllegalStateException if VAULT is requested but no Economy
     *                               provider is registered, or if the configured
     *                               item-currency material is unknown
     */
    public void initialize() throws IllegalStateException {
        final String typeName = plugin.getConfigManager()
                .getString("economy.type", "VAULT")
                .toUpperCase();

        try {
            type = CurrencyType.valueOf(typeName);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown economy.type '" + typeName
                    + "', defaulting to VAULT.");
            type = CurrencyType.VAULT;
        }

        switch (type) {
            case VAULT -> setupVault();
            case ITEM  -> setupItemCurrency();
        }

        plugin.getLogger().info("Economy initialised with backend: " + type);
    }

    /**
     * Alias for {@link #initialize()} called by the plugin lifecycle.
     */
    public void hook() throws IllegalStateException {
        initialize();
    }

    /**
     * Hooks into the Vault Economy service provider.
     *
     * @throws IllegalStateException if Vault is not installed or no Economy
     *                               provider is registered
     */
    private void setupVault() throws IllegalStateException {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            throw new IllegalStateException(
                    "Vault plugin not found. Install Vault or switch to ITEM economy.");
        }

        final RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            throw new IllegalStateException(
                    "No Vault Economy provider is registered. "
                    + "Install an economy plugin (e.g. EssentialsX) or switch to ITEM economy.");
        }

        vaultEconomy = rsp.getProvider();
        plugin.getLogger().info("Vault economy provider: " + vaultEconomy.getName());
    }

    /**
     * Reads and validates item-currency configuration.
     *
     * @throws IllegalStateException if the configured material is unknown
     */
    private void setupItemCurrency() throws IllegalStateException {
        final String matName = plugin.getConfigManager()
                .getString("economy.item-currency.material", "DIAMOND")
                .toUpperCase();

        itemCurrencyMaterial = Material.matchMaterial(matName);
        if (itemCurrencyMaterial == null) {
            throw new IllegalStateException(
                    "Unknown item-currency material '" + matName + "' in config.");
        }

        itemCurrencyName = plugin.getConfigManager()
                .getString("economy.item-currency.name", "Diamond");
        itemCurrencyCustomModelData = plugin.getConfigManager()
                .getInt("economy.item-currency.custom-model-data", 0);
    }

    // -------------------------------------------------------------------------
    // Balance queries
    // -------------------------------------------------------------------------

    /**
     * Returns the current balance of {@code player}.
     *
     * <ul>
     *   <li>VAULT – delegates to {@link Economy#getBalance(org.bukkit.OfflinePlayer)}</li>
     *   <li>ITEM  – counts matching currency items in the player's inventory</li>
     * </ul>
     *
     * @param player the target player
     * @return the player's current balance
     */
    public double getBalance(final Player player) {
        return switch (type) {
            case VAULT -> vaultEconomy.getBalance(player);
            case ITEM  -> countCurrencyItems(player.getInventory());
        };
    }

    /**
     * Returns {@code true} if the player has at least {@code amount} units.
     *
     * @param player the player to check
     * @param amount the required amount (must be positive)
     * @return {@code true} if the player can afford this amount
     */
    public boolean hasBalance(final Player player, final double amount) {
        if (amount <= 0) {
            return true;
        }
        return getBalance(player) >= amount;
    }

    public boolean has(final Player player, final double amount) {
        return hasBalance(player, amount);
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    /**
     * Withdraws {@code amount} from the player's balance.
     *
     * <ul>
     *   <li>VAULT – delegates to {@link Economy#withdrawPlayer}</li>
     *   <li>ITEM  – removes that many currency items from the inventory</li>
     * </ul>
     *
     * @param player the player to charge
     * @param amount the amount to deduct (must be positive)
     * @return {@code true} if the withdrawal succeeded; {@code false} if the
     *         player had insufficient funds
     */
    public boolean withdraw(final Player player, final double amount) {
        if (amount <= 0) {
            return true;
        }

        return switch (type) {
            case VAULT -> {
                if (!vaultEconomy.has(player, amount)) {
                    yield false;
                }
                final EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);
                if (!response.transactionSuccess()) {
                    plugin.getLogger().log(Level.WARNING,
                            "Vault withdrawal failed for " + player.getName()
                            + " (amount=" + amount + "): " + response.errorMessage);
                    yield false;
                }
                yield true;
            }
            case ITEM -> removeCurrencyItems(player, (int) Math.ceil(amount));
        };
    }

    /**
     * Deposits {@code amount} into the player's balance.
     *
     * <ul>
     *   <li>VAULT – delegates to {@link Economy#depositPlayer}</li>
     *   <li>ITEM  – adds currency items to the inventory, dropping any items
     *       that do not fit at the player's feet</li>
     * </ul>
     *
     * @param player the player to pay
     * @param amount the amount to credit (must be positive)
     */
    public void deposit(final Player player, final double amount) {
        if (amount <= 0) {
            return;
        }

        switch (type) {
            case VAULT -> {
                final EconomyResponse response = vaultEconomy.depositPlayer(player, amount);
                if (!response.transactionSuccess()) {
                    plugin.getLogger().log(Level.WARNING,
                            "Vault deposit failed for " + player.getName()
                            + " (amount=" + amount + "): " + response.errorMessage);
                }
            }
            case ITEM -> giveCurrencyItems(player, (int) Math.ceil(amount));
        }
    }

    /**
     * Deposits an amount to a player by UUID, supporting offline players via Vault.
     *
     * @param playerUuid the target player's UUID
     * @param amount     the amount to credit
     */
    public void depositByUuid(final java.util.UUID playerUuid, final double amount) {
        if (playerUuid == null || amount <= 0) return;
        Player online = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (online != null && online.isOnline()) {
            deposit(online, amount);
            return;
        }

        if (type == CurrencyType.VAULT && vaultEconomy != null) {
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(playerUuid);
            vaultEconomy.depositPlayer(offline, amount);
        }
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable representation of {@code amount}.
     *
     * <ul>
     *   <li>VAULT – delegates to {@link Economy#format(double)}</li>
     *   <li>ITEM  – formats as {@code "1,234 Diamonds"}</li>
     * </ul>
     *
     * @param amount the value to format
     * @return formatted string
     */
    public String formatAmount(final double amount) {
        return switch (type) {
            case VAULT -> vaultEconomy.format(amount);
            case ITEM  -> {
                final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
                yield nf.format((long) amount) + " " + itemCurrencyName
                        + (amount != 1.0 ? "s" : "");
            }
        };
    }

    // -------------------------------------------------------------------------
    // Item-currency helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code item} matches the configured currency item.
     *
     * <p>Always returns {@code false} when the backend is {@link CurrencyType#VAULT}.
     *
     * @param item the item to test; {@code null} safe
     * @return {@code true} if the item is a valid currency unit
     */
    public boolean isItemCurrency(final ItemStack item) {
        if (type != CurrencyType.ITEM || item == null) {
            return false;
        }
        if (item.getType() != itemCurrencyMaterial) {
            return false;
        }
        if (itemCurrencyCustomModelData != 0) {
            if (!item.hasItemMeta()) {
                return false;
            }
            final ItemMeta meta = item.getItemMeta();
            return meta.hasCustomModelData()
                    && meta.getCustomModelData() == itemCurrencyCustomModelData;
        }
        return true;
    }

    public boolean isCurrencyItem(final ItemStack item) {
        return isItemCurrency(item);
    }

    /**
     * Creates a currency {@link ItemStack} with the configured material, display
     * name, and custom model data.
     *
     * @param amount the stack size (clamped to [1, 64])
     * @return the constructed currency item
     * @throws IllegalStateException if the economy backend is not ITEM
     */
    public ItemStack createCurrencyItem(final int amount) {
        if (type != CurrencyType.ITEM) {
            throw new IllegalStateException("createCurrencyItem() called for non-ITEM economy.");
        }

        final int clamped = Math.max(1, Math.min(64, amount));
        final ItemStack item = new ItemStack(itemCurrencyMaterial, clamped);
        final ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(itemCurrencyName));
            if (itemCurrencyCustomModelData != 0) {
                meta.setCustomModelData(itemCurrencyCustomModelData);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the active currency backend type.
     *
     * @return {@link CurrencyType#VAULT} or {@link CurrencyType#ITEM}
     */
    public CurrencyType getType() {
        return type;
    }

    /**
     * Returns the Vault {@link Economy} provider, or {@code null} when the
     * backend is {@link CurrencyType#ITEM}.
     *
     * @return the Vault economy provider
     */
    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Counts how many currency items the player currently holds.
     *
     * @param inventory the inventory to scan
     * @return total count of currency items
     */
    private int countCurrencyItems(final PlayerInventory inventory) {
        int total = 0;
        for (final ItemStack stack : inventory.getStorageContents()) {
            if (isItemCurrency(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} currency items from the player's inventory.
     *
     * @param player the target player
     * @param amount the number of items to remove
     * @return {@code true} if the player had enough items and they were removed;
     *         {@code false} if insufficient items are present (inventory is not
     *         modified in that case)
     */
    private boolean removeCurrencyItems(final Player player, final int amount) {
        final PlayerInventory inv = player.getInventory();
        if (countCurrencyItems(inv) < amount) {
            return false;
        }

        int remaining = amount;
        final ItemStack[] contents = inv.getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            final ItemStack stack = contents[i];
            if (!isItemCurrency(stack)) {
                continue;
            }
            final int stackSize = stack.getAmount();
            if (stackSize <= remaining) {
                contents[i] = null;
                remaining -= stackSize;
            } else {
                stack.setAmount(stackSize - remaining);
                remaining = 0;
            }
        }

        inv.setStorageContents(contents);
        return true;
    }

    /**
     * Adds currency items to the player's inventory, dropping overflow items at
     * the player's current location.
     *
     * @param player the recipient
     * @param amount the total number of currency items to give
     */
    private void giveCurrencyItems(final Player player, final int amount) {
        int remaining = amount;
        final List<ItemStack> toGive = new ArrayList<>();

        while (remaining > 0) {
            final int stackSize = Math.min(remaining, itemCurrencyMaterial.getMaxStackSize());
            toGive.add(createCurrencyItem(stackSize));
            remaining -= stackSize;
        }

        for (final ItemStack stack : toGive) {
            // addItem returns a map of items that could not be added
            player.getInventory().addItem(stack).values().forEach(overflow ->
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow)
            );
        }
    }
}
