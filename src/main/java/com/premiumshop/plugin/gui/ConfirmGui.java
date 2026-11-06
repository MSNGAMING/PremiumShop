package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Confirmation dialog GUI for shop purchases and sales.
 * Provides quantity adjustments (+1, +8, +16, +64, -1, etc.) and visual price feedback.
 */
public class ConfirmGui extends AbstractGui {

    private final Shop shop;
    private final ShopCategory category;
    private final ShopItem shopItem;
    private final boolean isBuying;
    private int amount;
    private final double customUnitPrice;
    private boolean processing = false;

    public ConfirmGui(PremiumShopPlugin plugin, Player viewer,
                      Shop shop, ShopCategory category, ShopItem shopItem,
                      int amount, boolean isBuying) {
        super(plugin, viewer, isBuying ? "&2&lConfirm Purchase" : "&6&lConfirm Sale", 27);
        this.shop = shop;
        this.category = category;
        this.shopItem = shopItem;
        this.amount = Math.max(1, amount);
        this.isBuying = isBuying;
        this.customUnitPrice = -1.0;
    }

    public ConfirmGui(PremiumShopPlugin plugin, Player viewer,
                      ShopItem shopItem, String shopId, int amount,
                      boolean isBuying, double unitPrice) {
        super(plugin, viewer, isBuying ? "&2&lConfirm Purchase" : "&6&lConfirm Sale", 27);
        this.shop = plugin.getShopManager().getShop(shopId);
        this.category = (this.shop != null && !this.shop.getCategories().isEmpty()) ? this.shop.getCategories().get(0) : null;
        this.shopItem = shopItem;
        this.amount = Math.max(1, amount);
        this.isBuying = isBuying;
        this.customUnitPrice = unitPrice;
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        double unitPrice;
        if (customUnitPrice >= 0) {
            unitPrice = customUnitPrice;
        } else if (shop != null) {
            unitPrice = isBuying
                    ? (plugin.getPriceEngine() != null ? plugin.getPriceEngine().getDynamicBuyPrice(shop.getId(), shopItem.getId(), shopItem.getBaseBuyPrice()) : shopItem.getBaseBuyPrice())
                    : (plugin.getPriceEngine() != null ? plugin.getPriceEngine().getDynamicSellPrice(shop.getId(), shopItem.getId(), shopItem.getBaseSellPrice()) : shopItem.getBaseSellPrice());
        } else {
            unitPrice = isBuying ? shopItem.getBaseBuyPrice() : shopItem.getBaseSellPrice();
        }

        double total = unitPrice * amount;
        String formattedTotal = plugin.getEconomyManager().formatAmount(total);

        // Preview item at slot 13
        ItemStack preview = shopItem.getDisplayItem().clone();
        preview.setAmount(Math.min(64, Math.max(1, amount)));
        setItem(13, preview);

        // Summary item at slot 4
        setItem(4, createItem(
                Material.PAPER,
                "&e&lTransaction Details",
                "&7Type: " + (isBuying ? "&aBuy" : "&6Sell"),
                "&7Amount: &e" + amount,
                "&7Unit Price: &e" + plugin.getEconomyManager().formatAmount(unitPrice),
                "&7Total Price: &e" + formattedTotal,
                "",
                "&7Stock Available: &f" + (shopItem.getMaxStock() > 0 ? shopItem.getCurrentStock() : "Unlimited")
        ));

        // Confirm button at slot 11
        setItem(11, createItem(
                Material.LIME_CONCRETE,
                "&a&lCONFIRM " + (isBuying ? "BUY" : "SELL"),
                "&7Click to finalize the transaction",
                "&7Total: &e" + formattedTotal
        ), event -> {
            if (processing) return;
            processing = true;

            boolean success;
            if (isBuying) {
                success = plugin.getShopManager().executePurchase(viewer, shop, category, shopItem, amount);
            } else {
                success = plugin.getShopManager().executeSale(viewer, shop, category, shopItem, amount);
            }
            if (success) {
                viewer.closeInventory();
            } else {
                processing = false;
                refresh();
            }
        });

        // Cancel button at slot 15
        setItem(15, createItem(
                Material.RED_CONCRETE,
                "&c&lCANCEL",
                "&7Click to return to category items"
        ), event -> {
            if (shop != null && category != null) {
                new ShopItemGui(plugin, viewer, shop, category).open();
            } else {
                viewer.closeInventory();
            }
        });

        // Quantity modifier buttons
        setItem(18, createItem(Material.RED_STAINED_GLASS_PANE, "&c-10"), event -> adjustAmount(-10));
        setItem(19, createItem(Material.RED_STAINED_GLASS_PANE, "&c-1"), event -> adjustAmount(-1));
        setItem(25, createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1"), event -> adjustAmount(1));
        setItem(26, createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10"), event -> adjustAmount(10));
    }

    private void adjustAmount(int delta) {
        this.amount = Math.max(1, Math.min(2304, this.amount + delta));
        refresh();
    }
}
