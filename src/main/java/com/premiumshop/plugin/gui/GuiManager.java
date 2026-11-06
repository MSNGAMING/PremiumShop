package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.editor.InGameEditorGui;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager tracking active GUI sessions, dispatching interactions,
 * and opening interface menus.
 */
public class GuiManager {

    private final PremiumShopPlugin plugin;
    private final Map<UUID, AbstractGui> activeGuis = new ConcurrentHashMap<>();

    public GuiManager(PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main shop selection menu or a specific shop's category menu.
     */
    public void openShop(Player player, String shopId) {
        if (player == null) return;

        if (shopId == null || shopId.isEmpty()) {
            Shop defaultShop = plugin.getShopManager().getDefaultShop();
            if (defaultShop != null) {
                new ShopCategoryGui(plugin, player, defaultShop).open();
            } else {
                new ShopMainGui(plugin, player, new java.util.ArrayList<>(plugin.getShopManager().getAllShops())).open();
            }
            return;
        }

        Shop shop = plugin.getShopManager().getShop(shopId);
        if (shop == null) {
            String msg = plugin.getConfigManager().getString("messages.shop-not-found", "&cShop '{shop}' not found.");
            player.sendMessage(ColorUtil.format(msg, "shop", shopId));
            return;
        }

        new ShopCategoryGui(plugin, player, shop).open();
    }

    /**
     * Opens the default shop.
     */
    public void openShop(Player player) {
        openShop(player, null);
    }

    /**
     * Opens the item list for a specific category.
     */
    public void openCategory(Player player, Shop shop, ShopCategory category) {
        if (player == null || shop == null || category == null) return;
        new ShopItemGui(plugin, player, shop, category).open();
    }

    /**
     * Opens the main auction house browsing menu.
     */
    public void openAuction(Player player) {
        if (player == null) return;
        new AuctionMainGui(plugin, player).open();
    }

    /**
     * Opens the auction creation menu for an item.
     */
    public void openAuctionCreate(Player player, ItemStack item) {
        if (player == null) return;
        new AuctionCreateGui(plugin, player, item).open();
    }

    /**
     * Opens the claims menu for collected items and earnings.
     */
    public void openClaims(Player player) {
        if (player == null) return;
        new ClaimsGui(plugin, player).open();
    }

    /**
     * Opens the in-game administrative GUI editor.
     */
    public void openEditor(Player player, Shop shop, ShopCategory category) {
        if (player == null) return;
        if (!player.hasPermission("premiumshop.admin.editor")) {
            player.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getString("messages.no-permission", "&cNo permission.")));
            return;
        }
        new InGameEditorGui(plugin, player, shop, category).open();
    }

    /**
     * Tracks an open GUI for a player.
     */
    public void trackGui(Player player, AbstractGui gui) {
        if (player == null || gui == null) return;
        activeGuis.put(player.getUniqueId(), gui);
    }

    public void openGui(Player player, AbstractGui gui) {
        if (player == null || gui == null) return;
        trackGui(player, gui);
        gui.open();
    }

    public boolean shopExists(String shopId) {
        return plugin.getShopManager() != null && plugin.getShopManager().getShop(shopId) != null;
    }

    public java.util.List<String> getRegisteredShopIds() {
        if (plugin.getShopManager() == null) return java.util.Collections.emptyList();
        return plugin.getShopManager().getAllShops().stream().map(Shop::getId).toList();
    }

    public boolean openEditorGui(Player player, String shopId, String categoryId) {
        if (player == null) return false;
        Shop shop = plugin.getShopManager().getShop(shopId);
        if (shop != null) {
            ShopCategory cat = shop.getCategory(categoryId);
            if (cat != null) {
                openEditor(player, shop, cat);
                return true;
            }
        }
        return false;
    }

    /**
     * Untracks a GUI when closed.
     */
    public void untrackGui(Player player) {
        if (player == null) return;
        activeGuis.remove(player.getUniqueId());
    }

    /**
     * Gets the currently active GUI for a player.
     */
    public AbstractGui getOpenGui(Player player) {
        if (player == null) return null;
        return activeGuis.get(player.getUniqueId());
    }

    /**
     * Handles an inventory click event.
     */
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        AbstractGui gui = activeGuis.get(player.getUniqueId());
        if (gui != null) {
            gui.handleClick(event);
        }
    }

    /**
     * Handles inventory close cleanup.
     */
    public void handleClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            activeGuis.remove(player.getUniqueId());
        }
    }

    /**
     * Checks if the inventory belongs to our plugin.
     */
    public boolean isPluginInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof AbstractGui;
    }

    /**
     * Closes all active plugin GUIs across all online players (used on disable/reload).
     */
    public void closeAll() {
        for (UUID uuid : activeGuis.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }
        activeGuis.clear();
    }
}
