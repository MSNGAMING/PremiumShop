package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interactive Item Transaction GUI.
 * Displays BUY options on the left (Buy 1, Buy 16, Buy 64, Buy Max)
 * and SELL options on the right (Sell 1, Sell 16, Sell 64, Sell All) side-by-side.
 */
public class ConfirmGui extends AbstractGui {

    private final Shop shop;
    private final ShopCategory category;
    private final ShopItem shopItem;
    private boolean processing = false;

    public ConfirmGui(PremiumShopPlugin plugin, Player viewer,
                      Shop shop, ShopCategory category, ShopItem shopItem) {
        super(plugin, viewer, "&8Shop » &6" + (shopItem != null ? shopItem.getDisplayName() : "Item"), 27);
        this.shop = shop;
        this.category = category;
        this.shopItem = shopItem;
    }

    public ConfirmGui(PremiumShopPlugin plugin, Player viewer,
                      Shop shop, ShopCategory category, ShopItem shopItem,
                      int amount, boolean isBuying) {
        this(plugin, viewer, shop, category, shopItem);
    }

    public ConfirmGui(PremiumShopPlugin plugin, Player viewer,
                      ShopItem shopItem, String shopId, int amount,
                      boolean isBuying, double unitPrice) {
        super(plugin, viewer, "&8Shop » &6" + (shopItem != null ? shopItem.getDisplayName() : "Item"), 27);
        this.shop = plugin.getShopManager().getShop(shopId);
        this.category = (this.shop != null && !this.shop.getCategories().isEmpty())
                ? this.shop.getCategories().get(0)
                : null;
        this.shopItem = shopItem;
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        if (shopItem == null) {
            setItem(13, createItem(Material.PAPER, "&cError: Item not found."));
            setItem(22, createItem(Material.ARROW, "&c&l← Back"), event -> returnToShop());
            return;
        }

        String shopId = shop != null ? shop.getId() : "main";
        double buyPrice = plugin.getShopManager().getDynamicBuyPrice(shopId, shopItem);
        double sellPrice = plugin.getShopManager().getDynamicSellPrice(shopId, shopItem);
        int inInventory = plugin.getShopManager().countItemsInInventory(viewer, shopItem);
        double balance = plugin.getEconomyManager().getBalance(viewer);

        // Header accents
        setItem(1, createItem(Material.LIME_STAINED_GLASS_PANE, "&a&lBUY OPTIONS"));
        setItem(7, createItem(Material.ORANGE_STAINED_GLASS_PANE, "&6&lSELL OPTIONS"));

        // Slot 4: Transaction Summary Details
        setItem(4, createItem(
                Material.PAPER,
                "&e&lTransaction Details",
                "&7Item: &f" + shopItem.getDisplayName(),
                "&8─────────────────",
                shopItem.isBuyable() ? "&7Buy Price:  &a$" + formatPrice(buyPrice) + " &7each" : "&7Buy Price:  &cNot for sale",
                shopItem.isSellable() ? "&7Sell Price: &e$" + formatPrice(sellPrice) + " &7each" : "&7Sell Price: &cCannot be sold",
                "&8─────────────────",
                "&7Your Balance: &6$" + formatPrice(balance),
                "&7In Inventory: &b" + inInventory + "x"
        ));

        // Slot 13: Preview of the shop item
        ItemStack preview = shopItem.getDisplayItem().clone();
        preview.setAmount(1);
        setItem(13, preview);

        // ---------------------------------------------------------------------
        // BUY OPTIONS (Left side: slots 10, 11, 12, 19)
        // ---------------------------------------------------------------------
        if (shopItem.isBuyable()) {
            // Buy 1 (Slot 10)
            setItem(10, createItem(
                    Material.LIME_STAINED_GLASS,
                    "&a&lBuy 1",
                    "&7Total Price: &a$" + formatPrice(buyPrice),
                    "",
                    "&eClick to purchase 1x!"
            ), event -> handleBuy(1));

            // Buy 16 (Slot 11)
            setItem(11, createItem(
                    Material.LIME_CONCRETE,
                    "&a&lBuy 16",
                    "&7Total Price: &a$" + formatPrice(buyPrice * 16),
                    "",
                    "&eClick to purchase 16x!"
            ), event -> handleBuy(16));

            // Buy 64 (Slot 12)
            setItem(12, createItem(
                    Material.EMERALD_BLOCK,
                    "&a&lBuy 64",
                    "&7Total Price: &a$" + formatPrice(buyPrice * 64),
                    "",
                    "&eClick to purchase 64x!"
            ), event -> handleBuy(64));

            // Buy Max (Slot 19)
            int maxAffordable = buyPrice > 0 ? (int) (balance / buyPrice) : 2304;
            maxAffordable = Math.max(0, Math.min(2304, maxAffordable));
            setItem(19, createItem(
                    Material.LIME_GLAZED_TERRACOTTA,
                    "&a&lBuy Max (" + maxAffordable + ")",
                    "&7Buy as many as you can afford",
                    "&7Total Cost: &a$" + formatPrice(buyPrice * maxAffordable),
                    "",
                    maxAffordable > 0 ? "&eClick to buy " + maxAffordable + "x!" : "&cCannot afford any!"
            ), event -> handleBuyMax());
        } else {
            setItem(10, createItem(Material.GRAY_DYE, "&cNot for sale"));
            setItem(11, createItem(Material.GRAY_DYE, "&cNot for sale"));
            setItem(12, createItem(Material.GRAY_DYE, "&cNot for sale"));
            setItem(19, createItem(Material.GRAY_DYE, "&cNot for sale"));
        }

        // ---------------------------------------------------------------------
        // SELL OPTIONS (Right side: slots 14, 15, 16, 25)
        // ---------------------------------------------------------------------
        if (shopItem.isSellable()) {
            // Sell 1 (Slot 14)
            setItem(14, createItem(
                    Material.ORANGE_STAINED_GLASS,
                    "&6&lSell 1",
                    "&7Total Earn: &e$" + formatPrice(sellPrice),
                    "&7In Inventory: &f" + inInventory,
                    "",
                    inInventory >= 1 ? "&eClick to sell 1x!" : "&cYou don't have enough!"
            ), event -> handleSell(1));

            // Sell 16 (Slot 15)
            setItem(15, createItem(
                    Material.ORANGE_CONCRETE,
                    "&6&lSell 16",
                    "&7Total Earn: &e$" + formatPrice(sellPrice * 16),
                    "&7In Inventory: &f" + inInventory,
                    "",
                    inInventory >= 16 ? "&eClick to sell 16x!" : "&cYou don't have enough!"
            ), event -> handleSell(16));

            // Sell 64 (Slot 16)
            setItem(16, createItem(
                    Material.GOLD_BLOCK,
                    "&6&lSell 64",
                    "&7Total Earn: &e$" + formatPrice(sellPrice * 64),
                    "&7In Inventory: &f" + inInventory,
                    "",
                    inInventory >= 64 ? "&eClick to sell 64x!" : "&cYou don't have enough!"
            ), event -> handleSell(64));

            // Sell All of this item (Slot 25)
            setItem(25, createItem(
                    Material.HOPPER,
                    "&6&lSell All This Item (" + inInventory + ")",
                    "&7Sell every " + shopItem.getDisplayName() + " in your inventory",
                    "&7Total Earn: &a$" + formatPrice(sellPrice * inInventory),
                    "",
                    inInventory > 0 ? "&eClick to sell all " + inInventory + "x!" : "&cYou have none to sell!"
            ), event -> handleSell(inInventory));
        } else {
            setItem(14, createItem(Material.GRAY_DYE, "&cCannot be sold"));
            setItem(15, createItem(Material.GRAY_DYE, "&cCannot be sold"));
            setItem(16, createItem(Material.GRAY_DYE, "&cCannot be sold"));
            setItem(25, createItem(Material.GRAY_DYE, "&cCannot be sold"));
        }

        // Return button at slot 22
        setItem(22, createItem(
                Material.ARROW,
                "&c&l← Return to Shop",
                "&7Click to go back to category"
        ), event -> returnToShop());
    }

    private void handleBuy(int amount) {
        if (processing || amount <= 0) return;
        processing = true;

        plugin.getShopManager().executePurchase(viewer, shop, category, shopItem, amount);
        processing = false;
        refresh();
    }

    private void handleBuyMax() {
        if (processing) return;
        String shopId = shop != null ? shop.getId() : "main";
        double buyPrice = plugin.getShopManager().getDynamicBuyPrice(shopId, shopItem);
        double balance = plugin.getEconomyManager().getBalance(viewer);
        if (buyPrice <= 0) return;

        int max = (int) (balance / buyPrice);
        if (max <= 0) {
            viewer.sendMessage(colorizeComponent("&cYou cannot afford any of this item."));
            return;
        }
        handleBuy(Math.min(2304, max));
    }

    private void handleSell(int amount) {
        if (processing || amount <= 0) return;
        processing = true;

        plugin.getShopManager().executeSale(viewer, shop, category, shopItem, amount);
        processing = false;
        refresh();
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

    private static String formatPrice(double price) {
        if (price == Math.floor(price) && !Double.isInfinite(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }
}
