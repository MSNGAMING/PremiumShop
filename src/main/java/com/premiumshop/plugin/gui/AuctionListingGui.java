package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.auction.AuctionListing;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Inspection view for a single auction listing.
 * Allows placing bids, performing immediate buyout, or cancelling (by seller).
 */
public class AuctionListingGui extends AbstractGui {

    private final AuctionListing listing;

    public AuctionListingGui(PremiumShopPlugin plugin, Player viewer, AuctionListing listing) {
        super(plugin, viewer, "&5&lAuction Details", 54);
        this.listing = listing;
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        boolean isSeller = viewer.getUniqueId().equals(listing.getSellerUuid());
        double minIncrementPercent = plugin.getConfigManager().getDouble("auction.min-bid-increment-percent", 5.0);
        double minNextBid = listing.hasBids()
                ? listing.getCurrentBid() * (1.0 + minIncrementPercent / 100.0)
                : listing.getStartBid();
        minNextBid = Math.round(minNextBid * 100.0) / 100.0;

        // Display listed item at slot 13
        setItem(13, listing.getItem());

        // Seller and timing info at slot 29
        setItem(29, createItem(
                Material.BOOK,
                "&e&lListing Information",
                "&7Seller: &f" + listing.getSellerName(),
                "&7Time Remaining: &e" + listing.getFormattedTimeRemaining(),
                "&7Status: &a" + listing.getStatus().name(),
                "&7Listing ID: &8" + listing.getId().substring(0, 8) + "..."
        ));

        // Bid status at slot 31
        setItem(31, createItem(
                Material.GOLD_BLOCK,
                "&6&lBid Status",
                "&7Current Bid: &e" + plugin.getEconomyManager().formatAmount(listing.getCurrentBid()),
                "&7Highest Bidder: &b" + (listing.hasBids() ? listing.getCurrentBidderName() : "None"),
                "&7Minimum Next Bid: &a" + plugin.getEconomyManager().formatAmount(minNextBid)
        ));

        // Bid button at slot 11
        if (!isSeller) {
            final double nextBidAmount = minNextBid;
            setItem(11, createItem(
                    Material.EMERALD,
                    "&a&lPlace Minimum Bid",
                    "&7Bid Amount: &e" + plugin.getEconomyManager().formatAmount(nextBidAmount),
                    "&eClick to bid!"
            ), event -> {
                plugin.getAuctionManager().placeBid(viewer, listing.getId(), nextBidAmount).thenAccept(success -> {
                    if (success) {
                        viewer.sendMessage(ColorUtil.colorize("&aYour bid was successfully placed!"));
                    }
                    Bukkit.getScheduler().runTask(plugin, this::refresh);
                });
            });
        } else {
            // Seller cancellation option (only if no bids placed)
            if (!listing.hasBids()) {
                setItem(11, createItem(
                        Material.RED_CONCRETE,
                        "&c&lCancel Listing",
                        "&7Click to cancel this auction and reclaim your item."
                ), event -> {
                    plugin.getAuctionManager().cancelListing(viewer, listing.getId()).thenAccept(success -> {
                        if (success) {
                            viewer.sendMessage(ColorUtil.colorize("&aListing cancelled and item returned."));
                            Bukkit.getScheduler().runTask(plugin, () -> new AuctionMainGui(plugin, viewer).open());
                        }
                    });
                });
            } else {
                setItem(11, createItem(
                        Material.GRAY_DYE,
                        "&c&lCannot Cancel",
                        "&7This listing already has bids and cannot be cancelled."
                ));
            }
        }

        // Buyout button at slot 15
        if (listing.hasBuyout() && !isSeller) {
            setItem(15, createItem(
                    Material.DIAMOND,
                    "&b&lInstant Buyout",
                    "&7Buyout Price: &e" + plugin.getEconomyManager().formatAmount(listing.getBuyoutPrice()),
                    "&eClick to purchase immediately!"
            ), event -> {
                plugin.getAuctionManager().buyout(viewer, listing.getId()).thenAccept(success -> {
                    if (success) {
                        viewer.sendMessage(ColorUtil.colorize("&aCongratulations! You bought out this auction."));
                        Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
                    }
                });
            });
        } else if (listing.hasBuyout() && isSeller) {
            setItem(15, createItem(
                    Material.DIAMOND,
                    "&b&lBuyout Set",
                    "&7Buyout Price: &e" + plugin.getEconomyManager().formatAmount(listing.getBuyoutPrice())
            ));
        }

        // Back button at slot 49
        setItem(49, createItem(Material.ARROW, "&c&lBack to Auction House"), event -> {
            new AuctionMainGui(plugin, viewer).open();
        });
    }
}
