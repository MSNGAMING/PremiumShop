package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.auction.AuctionListing;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interface where players collect expired listings, unsold items,
 * and auction sale revenues / outbid refunds.
 */
public class ClaimsGui extends PaginatedGui {

    public ClaimsGui(PremiumShopPlugin plugin, Player viewer) {
        super(plugin, viewer, "&5&lPending Claims & Earnings", 54);
    }

    @Override
    protected List<Map.Entry<ItemStack, GuiAction>> getPageItems() {
        List<Map.Entry<ItemStack, GuiAction>> items = new ArrayList<>();
        List<AuctionListing> claims = plugin.getAuctionManager().getClaims(viewer.getUniqueId());

        for (AuctionListing claim : claims) {
            ItemStack icon;
            boolean isItem = !claim.getId().startsWith("refund-");

            if (isItem) {
                icon = claim.getItem().clone();
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(ColorUtil.colorize("&8&m------------------------"));
                    lore.add(ColorUtil.colorize("&7Claim Type: &eUnsold/Won Item"));
                    lore.add(ColorUtil.colorize("&7Reason: &f" + claim.getStatus().name()));
                    lore.add(ColorUtil.colorize("&aClick to claim this item!"));
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
            } else {
                icon = new ItemStack(Material.GOLD_INGOT);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.colorize("&6&lRevenue / Refund Claim"));
                    List<String> lore = new ArrayList<>();
                    lore.add(ColorUtil.colorize("&7Amount: &e" + plugin.getEconomyManager().formatAmount(claim.getCurrentBid())));
                    lore.add(ColorUtil.colorize("&7From: &fAuction #" + claim.getSellerName()));
                    lore.add(ColorUtil.colorize("&aClick to deposit funds!"));
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
            }

            items.add(new AbstractMap.SimpleEntry<>(icon, event -> {
                // Claim single entry
                plugin.getAuctionManager().claimItems(viewer);
                refresh();
            }));
        }

        return items;
    }

    @Override
    protected void populateInventory() {
        super.populateInventory();

        // Back button at slot 45
        setItem(45, createItem(Material.ARROW, "&c&lBack to Auction House"), event -> {
            new AuctionMainGui(plugin, viewer).open();
        });

        // Claim all button at slot 51
        setItem(51, createItem(
                Material.CHEST_MINECART,
                "&a&lClaim Everything",
                "&7Collect all pending items and deposits at once!"
        ), event -> {
            plugin.getAuctionManager().claimItems(viewer);
            refresh();
        });
    }
}
