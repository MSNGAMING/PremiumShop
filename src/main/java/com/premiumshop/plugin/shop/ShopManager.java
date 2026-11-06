package com.premiumshop.plugin.shop;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * High-performance, thread-safe manager for all shop instances, categories,
 * and player shop transactions.
 */
public class ShopManager {

    private final PremiumShopPlugin plugin;
    private final Map<String, Shop> shops = new ConcurrentHashMap<>();

    public ShopManager(PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all shops from shops.yml asynchronously, then updates memory.
     */
    public void loadShops() {
        shops.clear();
        FileConfiguration config = plugin.getConfigManager().getShopsConfig();
        if (config == null) {
            plugin.getLogger().warning("shops.yml configuration is not loaded.");
            return;
        }

        ConfigurationSection shopsSection = config.getConfigurationSection("shops");
        if (shopsSection == null) {
            plugin.getLogger().warning("No 'shops' root section found in shops.yml.");
            return;
        }

        for (String shopId : shopsSection.getKeys(false)) {
            ConfigurationSection sec = shopsSection.getConfigurationSection(shopId);
            if (sec != null) {
                try {
                    Shop shop = Shop.fromConfig(shopId, sec, Bukkit.getItemFactory());
                    shops.put(shopId.toLowerCase(Locale.ROOT), shop);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load shop '" + shopId + "'", ex);
                }
            }
        }
        plugin.getLogger().info("Successfully loaded " + shops.size() + " shop(s).");
    }

    /**
     * Serializes all in-memory shops and writes to shops.yml asynchronously.
     */
    public void saveShops() {
        FileConfiguration config = plugin.getConfigManager().getShopsConfig();
        if (config == null) return;

        ConfigurationSection shopsSection = config.createSection("shops");
        for (Shop shop : shops.values()) {
            ConfigurationSection sec = shopsSection.createSection(shop.getId());
            shop.toConfig(sec);
        }

        plugin.getConfigManager().saveShopsConfig();
    }

    /**
     * Reloads shops from disk.
     */
    public void reloadShops() {
        plugin.getConfigManager().reloadAll();
        loadShops();
    }

    /**
     * Retrieves a shop by its unique identifier.
     */
    public Shop getShop(String id) {
        if (id == null) return null;
        return shops.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Retrieves the configured default shop.
     */
    public Shop getDefaultShop() {
        String defaultId = plugin.getConfigManager().getString("shop.default-shop", "main");
        Shop shop = getShop(defaultId);
        if (shop == null && !shops.isEmpty()) {
            return shops.values().iterator().next();
        }
        return shop;
    }

    /**
     * Returns an unmodifiable collection of all loaded shops.
     */
    public Collection<Shop> getAllShops() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public List<Shop> getShops() {
        return new ArrayList<>(shops.values());
    }

    public double getDynamicBuyPrice(String shopId, ShopItem item) {
        if (item == null) return 0.0;
        if (plugin.getPriceEngine() != null) {
            return plugin.getPriceEngine().getDynamicBuyPrice(shopId, item.getId(), item.getBaseBuyPrice());
        }
        return item.getBaseBuyPrice();
    }

    public double getDynamicSellPrice(String shopId, ShopItem item) {
        if (item == null) return 0.0;
        if (plugin.getPriceEngine() != null) {
            return plugin.getPriceEngine().getDynamicSellPrice(shopId, item.getId(), item.getBaseSellPrice());
        }
        return item.getBaseSellPrice();
    }

    /**
     * Creates a new shop in memory.
     */
    public Shop createShop(String id, String displayName) {
        String key = id.toLowerCase(Locale.ROOT);
        if (shops.containsKey(key)) {
            return shops.get(key);
        }
        Shop shop = new Shop(id, displayName);
        shops.put(key, shop);
        saveShops();
        return shop;
    }

    /**
     * Deletes a shop.
     */
    public boolean deleteShop(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Shop removed = shops.remove(key);
        if (removed != null) {
            saveShops();
            return true;
        }
        return false;
    }

    /**
     * Executes a purchase transaction for a player.
     *
     * @param player   the player purchasing
     * @param shop     the shop context
     * @param category the category context
     * @param item     the shop item being purchased
     * @param amount   the amount to purchase
     * @return true if purchase succeeded
     */
    public boolean executePurchase(Player player, Shop shop, ShopCategory category, ShopItem item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return false;
        }

        if (!item.isBuyable()) {
            player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getString("messages.not-buyable", "&cThis item is not for sale.")));
            return false;
        }

        // Check stock
        if (item.getMaxStock() > 0 && item.getCurrentStock() < amount) {
            player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getString("messages.out-of-stock", "&cNot enough stock available.")));
            return false;
        }

        double unitPrice = plugin.getPriceEngine() != null
                ? plugin.getPriceEngine().getDynamicBuyPrice(shop.getId(), item.getId(), item.getBaseBuyPrice())
                : item.getBaseBuyPrice();

        double totalPrice = unitPrice * amount;

        if (!plugin.getEconomyManager().hasBalance(player, totalPrice)) {
            String msg = plugin.getConfigManager().getString("messages.insufficient-funds", "&cYou do not have enough funds.");
            player.sendMessage(ColorUtil.format(msg, "price", plugin.getEconomyManager().formatAmount(totalPrice)));
            return false;
        }

        // Deduct money
        if (!plugin.getEconomyManager().withdraw(player, totalPrice)) {
            player.sendMessage(ColorUtil.colorize("&cFailed to process payment."));
            return false;
        }

        // Adjust stock
        if (item.getMaxStock() > 0) {
            item.adjustStock(-amount);
        }

        // Record transaction velocity for dynamic pricing
        if (plugin.getPriceEngine() != null) {
            plugin.getPriceEngine().recordTransaction(shop.getId(), item.getId());
        }

        // Give item safely respecting max stack sizes
        ItemStack template = item.getDisplayItem().clone();
        int maxStack = template.getMaxStackSize();
        int remainingToGive = amount;
        while (remainingToGive > 0) {
            int chunk = Math.min(remainingToGive, maxStack);
            ItemStack giveItem = template.clone();
            giveItem.setAmount(chunk);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(giveItem);
            for (ItemStack dropped : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            remainingToGive -= chunk;
        }

        // Async log transaction to database
        recordTransactionAsync(player.getUniqueId(), shop.getId(), item.getId(), "BUY", amount, totalPrice);

        String successMsg = plugin.getConfigManager().getString("messages.purchase-success", "&aSuccessfully purchased &e{amount}x {item}&a for &e{price}&a.");
        String itemName = item.getDisplayItem().hasItemMeta() && item.getDisplayItem().getItemMeta().hasDisplayName()
                ? item.getDisplayItem().getItemMeta().getDisplayName()
                : item.getDisplayItem().getType().name().replace('_', ' ');

        player.sendMessage(ColorUtil.format(successMsg,
                "amount", String.valueOf(amount),
                "item", itemName,
                "price", plugin.getEconomyManager().formatAmount(totalPrice)));

        return true;
    }

    /**
     * Sells all matching items in the player's inventory for the given category.
     *
     * @param player   the player selling
     * @param shopId   the shop identifier
     * @param category the shop category
     * @return the total currency earned
     */
    public double sellAll(Player player, String shopId, ShopCategory category) {
        if (player == null || category == null) return 0.0;
        double totalEarned = 0.0;
        Shop shop = getShop(shopId);
        String finalShopId = shop != null ? shop.getId() : (shopId != null ? shopId : "main");

        for (ShopItem shopItem : category.getItems()) {
            if (!shopItem.isSellable()) continue;

            ItemStack match = shopItem.getDisplayItem();
            int countInInventory = 0;
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack slotItem : contents) {
                if (slotItem != null && slotItem.isSimilar(match)) {
                    countInInventory += slotItem.getAmount();
                }
            }

            if (countInInventory <= 0) continue;

            // Remove items
            int remaining = countInInventory;
            for (int i = 0; i < contents.length; i++) {
                ItemStack slotItem = contents[i];
                if (slotItem != null && slotItem.isSimilar(match)) {
                    int stackAmount = slotItem.getAmount();
                    if (stackAmount <= remaining) {
                        remaining -= stackAmount;
                        player.getInventory().setItem(i, null);
                    } else {
                        slotItem.setAmount(stackAmount - remaining);
                        player.getInventory().setItem(i, slotItem);
                        remaining = 0;
                    }
                    if (remaining <= 0) break;
                }
            }

            double unitPrice = getDynamicSellPrice(finalShopId, shopItem);
            double totalItemPrice = unitPrice * countInInventory;
            totalEarned += totalItemPrice;

            plugin.getEconomyManager().deposit(player, totalItemPrice);
            if (shopItem.getMaxStock() > 0) {
                shopItem.adjustStock(countInInventory);
            }
            if (plugin.getPriceEngine() != null) {
                plugin.getPriceEngine().recordTransaction(finalShopId, shopItem.getId());
            }
            recordTransactionAsync(player.getUniqueId(), finalShopId, shopItem.getId(), "SELL", countInInventory, totalItemPrice);
        }
        return totalEarned;
    }

    /**
     * Executes a sell transaction for a player.
     *
     * @param player   the player selling
     * @param shop     the shop context
     * @param category the category context
     * @param item     the shop item being sold
     * @param amount   the amount to sell
     * @return true if sell succeeded
     */
    public boolean executeSale(Player player, Shop shop, ShopCategory category, ShopItem item, int amount) {
        if (player == null || item == null || amount <= 0) {
            return false;
        }

        if (!item.isSellable()) {
            player.sendMessage(ColorUtil.colorize("&cThis item cannot be sold."));
            return false;
        }

        ItemStack match = item.getDisplayItem();
        int countInInventory = 0;
        for (ItemStack slotItem : player.getInventory().getContents()) {
            if (slotItem != null && slotItem.isSimilar(match)) {
                countInInventory += slotItem.getAmount();
            }
        }

        if (countInInventory < amount) {
            String msg = plugin.getConfigManager().getString("messages.not-enough-items", "&cYou don't have enough items.");
            player.sendMessage(ColorUtil.format(msg, "amount", String.valueOf(amount)));
            return false;
        }

        double unitPrice = plugin.getPriceEngine() != null
                ? plugin.getPriceEngine().getDynamicSellPrice(shop.getId(), item.getId(), item.getBaseSellPrice())
                : item.getBaseSellPrice();

        double totalPrice = unitPrice * amount;

        // Remove items from player inventory
        int remainingToRemove = amount;
        for (ItemStack slotItem : player.getInventory().getContents()) {
            if (slotItem != null && slotItem.isSimilar(match)) {
                int stackAmount = slotItem.getAmount();
                if (stackAmount <= remainingToRemove) {
                    remainingToRemove -= stackAmount;
                    slotItem.setAmount(0);
                } else {
                    slotItem.setAmount(stackAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
                if (remainingToRemove <= 0) break;
            }
        }

        // Add funds
        plugin.getEconomyManager().deposit(player, totalPrice);

        // Adjust stock upwards on sell
        if (item.getMaxStock() > 0) {
            item.adjustStock(amount);
        }

        if (plugin.getPriceEngine() != null) {
            plugin.getPriceEngine().recordTransaction(shop.getId(), item.getId());
        }

        // Async log transaction
        recordTransactionAsync(player.getUniqueId(), shop.getId(), item.getId(), "SELL", amount, totalPrice);

        String successMsg = plugin.getConfigManager().getString("messages.sell-success", "&aSuccessfully sold &e{amount}x {item}&a for &e{price}&a.");
        String itemName = item.getDisplayItem().hasItemMeta() && item.getDisplayItem().getItemMeta().hasDisplayName()
                ? item.getDisplayItem().getItemMeta().getDisplayName()
                : item.getDisplayItem().getType().name().replace('_', ' ');

        player.sendMessage(ColorUtil.format(successMsg,
                "amount", String.valueOf(amount),
                "item", itemName,
                "price", plugin.getEconomyManager().formatAmount(totalPrice)));

        return true;
    }

    private void recordTransactionAsync(UUID playerUuid, String shopId, String itemId, String type, int amount, double price) {
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "INSERT INTO shop_transactions (player_uuid, shop_id, item_id, transaction_type, amount, price) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, shopId);
                ps.setString(3, itemId);
                ps.setString(4, type);
                ps.setInt(5, amount);
                ps.setDouble(6, price);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to log shop transaction to database", e);
            }
        });
    }

    public void reload() {
        loadShops();
    }

    public int getActiveShopCount() {
        return shops.size();
    }

    public List<String> getRegisteredShopIds() {
        return new ArrayList<>(shops.keySet());
    }

    public List<String> getCategoryIds(String shopId) {
        Shop shop = shops.get(shopId);
        if (shop == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (ShopCategory cat : shop.getCategories()) {
            result.add(cat.getId());
        }
        return result;
    }

    public List<String> getItemIds(String shopId, String categoryId) {
        Shop shop = shops.get(shopId);
        if (shop == null) return Collections.emptyList();
        ShopCategory cat = shop.getCategory(categoryId);
        if (cat == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (ShopItem item : cat.getItems()) {
            result.add(item.getId());
        }
        return result;
    }

    public boolean setBasePrice(String shopId, String categoryId, String itemId, String type, double price) {
        Shop shop = shops.get(shopId);
        if (shop == null) return false;
        ShopCategory cat = shop.getCategory(categoryId);
        if (cat == null) return false;
        ShopItem item = cat.getItem(itemId);
        if (item == null) return false;
        if ("buy".equalsIgnoreCase(type)) {
            item.setBaseBuyPrice(price);
            return true;
        } else if ("sell".equalsIgnoreCase(type)) {
            item.setBaseSellPrice(price);
            return true;
        }
        return false;
    }

    public boolean setStock(String shopId, String categoryId, String itemId, int stock) {
        Shop shop = shops.get(shopId);
        if (shop == null) return false;
        ShopCategory cat = shop.getCategory(categoryId);
        if (cat == null) return false;
        ShopItem item = cat.getItem(itemId);
        if (item == null) return false;
        item.setCurrentStock(stock);
        return true;
    }

    public int resetDynamicPrices(String shopId) {
        Shop shop = shops.get(shopId);
        if (shop == null) return 0;
        int count = 0;
        for (ShopCategory cat : shop.getCategories()) {
            for (ShopItem item : cat.getItems()) {
                item.setTransactionVelocity(0);
                count++;
            }
        }
        return count;
    }
}
