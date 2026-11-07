package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import com.premiumshop.plugin.shop.ShopManager.SellAllSummary;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Re-confirmation GUI for selling all sellable items in the player's entire inventory.
 */
public class SellAllConfirmGui extends AbstractGui {

    private final Shop shop;
    private final ShopCategory category;
    private boolean processing = false;

    public SellAllConfirmGui(PremiumShopPlugin plugin, Player viewer, Shop shop, ShopCategory category) {
        super(plugin, viewer, "&4&lConfirm Sell All (Inventory)", 27);
        this.shop = shop;
        this.category = category;
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        String shopId = shop != null ? shop.getId() : "main";
        SellAllSummary summary = plugin.getShopManager().calculateSellAllSummary(viewer, shopId);

        if (summary.totalItemCount() <= 0) {
            setItem(13, createItem(
                    Material.CHEST,
                    "&c&lNo Sellable Items",
                    "&7You do not have any sellable survival",
                    "&7items in your inventory.",
                    "",
                    "&eClick back to return to shop."
            ), event -> returnToShop());
            setItem(22, createItem(Material.ARROW, "&c&l← Back"), event -> returnToShop());
            return;
        }

        // Summary item in slot 4
        List<String> summaryLore = new ArrayList<>();
        summaryLore.add("&8─────────────────");
        summaryLore.add("&7Total Items: &e" + summary.totalItemCount());
        summaryLore.add("&7Total Payout: &a$" + plugin.getEconomyManager().formatAmount(summary.totalPayout()));
        summaryLore.add("&8─────────────────");
        summaryLore.add("&e&lItems to be sold:");

        int listed = 0;
        for (Map.Entry<ShopItem, Integer> entry : summary.itemCounts().entrySet()) {
            if (listed >= 6) {
                int remainingTypes = summary.itemCounts().size() - listed;
                summaryLore.add("&7... and &e" + remainingTypes + " &7more item types");
                break;
            }
            ShopItem item = entry.getKey();
            int count = entry.getValue();
            double subtotal = plugin.getShopManager().getDynamicSellPrice(shopId, item) * count;
            summaryLore.add("&7 • &f" + item.getDisplayName() + ": &ex" + count + " &a($" + plugin.getEconomyManager().formatAmount(subtotal) + ")");
            listed++;
        }

        summaryLore.add("&8─────────────────");
        summaryLore.add("&c&lWARNING: &7This sells all matching items");
        summaryLore.add("&7from your entire inventory!");

        setItem(4, createItem(
                Material.CHEST,
                "&6&lSell Entire Inventory",
                summaryLore.toArray(new String[0])
        ));

        // Confirm button at slot 11
        setItem(11, createItem(
                Material.LIME_CONCRETE,
                "&a&l✔ CONFIRM SELL ALL",
                "&7Click to sell all &e" + summary.totalItemCount() + " items",
                "&7for &a$" + plugin.getEconomyManager().formatAmount(summary.totalPayout()) + "&7!",
                "",
                "&a&lPROCEED"
        ), event -> {
            if (processing) return;
            processing = true;

            double earned = plugin.getShopManager().sellAllEntireInventory(viewer, shopId);
            viewer.closeInventory();
            if (earned > 0) {
                viewer.sendMessage(ColorUtil.colorize("&a[Shop] Successfully sold entire inventory for &e$"
                        + plugin.getEconomyManager().formatAmount(earned) + "&a!"));
            } else {
                viewer.sendMessage(ColorUtil.colorize("&c[Shop] No items could be sold."));
            }
        });

        // Cancel button at slot 15
        setItem(15, createItem(
                Material.RED_CONCRETE,
                "&c&l✘ CANCEL",
                "&7Click to cancel and keep your items.",
                "",
                "&c&lRETURN"
        ), event -> returnToShop());
    }

    private void returnToShop() {
        if (shop != null && category != null) {
            new ShopItemGui(plugin, viewer, shop, category).open();
        } else if (shop != null) {
            new ShopCategoryGui(plugin, viewer, shop).open();
        } else {
            viewer.closeInventory();
        }
    }
}
