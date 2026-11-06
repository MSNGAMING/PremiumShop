package com.premiumshop.plugin.gui.editor;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.AbstractGui;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Visual in-game layout and price editor for server administrators.
 * Enables modification of pricing structures, stock levels, and flags.
 */
public class InGameEditorGui extends AbstractGui {

    public enum EditorMode {
        OVERVIEW,
        ITEM_EDIT
    }

    private final Shop shop;
    private final ShopCategory category;
    private ShopItem selectedItem;
    private EditorMode mode = EditorMode.OVERVIEW;

    public InGameEditorGui(PremiumShopPlugin plugin, Player viewer, Shop shop, ShopCategory category) {
        super(plugin, viewer, "&4&lShop Admin Editor &8» &e" + category.getDisplayName(), 54);
        this.shop = shop;
        this.category = category;
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        if (mode == EditorMode.OVERVIEW) {
            populateOverview();
        } else {
            populateItemEdit();
        }
    }

    private void populateOverview() {
        List<ShopItem> items = category.getItems();
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ShopItem item = items.get(i);
            ItemStack icon = item.getDisplayItem().clone();
            setItem(i, icon, event -> {
                this.selectedItem = item;
                this.mode = EditorMode.ITEM_EDIT;
                refresh();
            });
        }

        // Add item button at slot 48
        setItem(48, createItem(
                Material.NETHER_STAR,
                "&a&lAdd Item From Hand",
                "&7Click to add the item in your main hand to this category."
        ), event -> {
            ItemStack hand = viewer.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                viewer.sendMessage(ColorUtil.colorize("&cPlease hold the item you wish to add in your main hand!"));
                return;
            }

            String newId = hand.getType().name().toLowerCase() + "_" + System.currentTimeMillis() % 10000;
            ShopItem newItem = new ShopItem(
                    newId,
                    hand.clone(),
                    100.0,
                    50.0,
                    100,
                    50,
                    System.currentTimeMillis(),
                    0,
                    true,
                    true
            );
            category.addItem(newItem);
            plugin.getShopManager().saveShops();
            viewer.sendMessage(ColorUtil.colorize("&aAdded item &e" + newId + "&a to category."));
            refresh();
        });

        // Close/Exit button at slot 49
        setItem(49, createItem(Material.BARRIER, "&c&lClose Editor"), event -> {
            viewer.closeInventory();
        });
    }

    private void populateItemEdit() {
        if (selectedItem == null) {
            mode = EditorMode.OVERVIEW;
            populateOverview();
            return;
        }

        // Slot 10: Display item preview
        setItem(10, selectedItem.getDisplayItem());

        // Slot 12: Base Buy Price
        setItem(12, createItem(
                Material.EMERALD,
                "&a&lBase Buy Price: &e" + selectedItem.getBaseBuyPrice(),
                "&7Left-click: &a+10",
                "&7Right-click: &c-10",
                "&7Shift-Left: &a+100",
                "&7Shift-Right: &c-100"
        ), event -> {
            double current = selectedItem.getBaseBuyPrice();
            if (event.isShiftClick() && event.isLeftClick()) current += 100;
            else if (event.isShiftClick() && event.isRightClick()) current = Math.max(0, current - 100);
            else if (event.isLeftClick()) current += 10;
            else if (event.isRightClick()) current = Math.max(0, current - 10);
            selectedItem.setBaseBuyPrice(current);
            refresh();
        });

        // Slot 14: Base Sell Price
        setItem(14, createItem(
                Material.GOLD_INGOT,
                "&6&lBase Sell Price: &e" + selectedItem.getBaseSellPrice(),
                "&7Left-click: &a+5",
                "&7Right-click: &c-5",
                "&7Shift-Left: &a+50",
                "&7Shift-Right: &c-50"
        ), event -> {
            double current = selectedItem.getBaseSellPrice();
            if (event.isShiftClick() && event.isLeftClick()) current += 50;
            else if (event.isShiftClick() && event.isRightClick()) current = Math.max(0, current - 50);
            else if (event.isLeftClick()) current += 5;
            else if (event.isRightClick()) current = Math.max(0, current - 5);
            selectedItem.setBaseSellPrice(current);
            refresh();
        });

        // Slot 16: Max Stock
        setItem(16, createItem(
                Material.CHEST,
                "&e&lMax Stock: &f" + (selectedItem.getMaxStock() > 0 ? selectedItem.getMaxStock() : "Unlimited (-1)"),
                "&7Left-click: &a+10",
                "&7Right-click: &c-10",
                "&7Shift-click: &eToggle Unlimited"
        ), event -> {
            if (event.isShiftClick()) {
                selectedItem.setMaxStock(selectedItem.getMaxStock() > 0 ? -1 : 100);
            } else if (event.isLeftClick()) {
                selectedItem.setMaxStock(Math.max(1, selectedItem.getMaxStock() + 10));
            } else if (event.isRightClick()) {
                selectedItem.setMaxStock(Math.max(-1, selectedItem.getMaxStock() - 10));
            }
            refresh();
        });

        // Slot 20: Current Stock
        setItem(20, createItem(
                Material.BARREL,
                "&b&lCurrent Stock: &f" + selectedItem.getCurrentStock(),
                "&7Left-click: &a+10",
                "&7Right-click: &c-10"
        ), event -> {
            if (event.isLeftClick()) selectedItem.adjustStock(10);
            else if (event.isRightClick()) selectedItem.adjustStock(-10);
            refresh();
        });

        // Slot 22: Toggle Buyable
        Material buyMat = selectedItem.isBuyable() ? Material.LIME_WOOL : Material.RED_WOOL;
        setItem(22, createItem(
                buyMat,
                "&fBuyable: " + (selectedItem.isBuyable() ? "&aENABLED" : "&cDISABLED"),
                "&7Click to toggle"
        ), event -> {
            selectedItem.setBuyable(!selectedItem.isBuyable());
            refresh();
        });

        // Slot 24: Toggle Sellable
        Material sellMat = selectedItem.isSellable() ? Material.LIME_WOOL : Material.RED_WOOL;
        setItem(24, createItem(
                sellMat,
                "&fSellable: " + (selectedItem.isSellable() ? "&aENABLED" : "&cDISABLED"),
                "&7Click to toggle"
        ), event -> {
            selectedItem.setSellable(!selectedItem.isSellable());
            refresh();
        });

        // Slot 40: Delete item
        setItem(40, createItem(
                Material.LAVA_BUCKET,
                "&4&lDelete Item",
                "&7Click to remove this item permanently from the category."
        ), event -> {
            category.removeItem(selectedItem.getId());
            plugin.getShopManager().saveShops();
            viewer.sendMessage(ColorUtil.colorize("&cItem deleted."));
            mode = EditorMode.OVERVIEW;
            refresh();
        });

        // Slot 49: Save & Return
        setItem(49, createItem(
                Material.NETHER_STAR,
                "&a&lSave Changes & Return",
                "&7Click to commit changes to shops.yml."
        ), event -> {
            plugin.getShopManager().saveShops();
            viewer.sendMessage(ColorUtil.colorize("&aAll changes committed successfully."));
            mode = EditorMode.OVERVIEW;
            refresh();
        });
    }
}
