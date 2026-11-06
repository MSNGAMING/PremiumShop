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
 * Main browsing interface for the Auction House, displaying active listings
 * with pagination, filtering (all / my listings), and access to claims.
 */
public class AuctionMainGui extends PaginatedGui {

    public enum FilterMode {
        ALL("&eAll Listings"),
        MY_LISTINGS("&bMy Listings");

        private final String display;

        FilterMode(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    private FilterMode filter = FilterMode.ALL;
    private String searchQuery = null;

    public AuctionMainGui(PremiumShopPlugin plugin, Player viewer) {
        super(plugin, viewer, "&5&lAuction House", 54);
    }

    public AuctionMainGui(PremiumShopPlugin plugin, Player viewer, FilterMode filter) {
        super(plugin, viewer, "&5&lAuction House", 54);
        this.filter = filter;
    }

    public AuctionMainGui(PremiumShopPlugin plugin, com.premiumshop.plugin.auction.AuctionManager auctionManager, Player viewer, String filterQuery) {
        super(plugin, viewer, "&5&lAuction House", 54);
        if (filterQuery != null && filterQuery.toLowerCase().startsWith("seller:")) {
            this.filter = FilterMode.MY_LISTINGS;
        } else {
            this.filter = FilterMode.ALL;
            this.searchQuery = filterQuery;
        }
    }

    @Override
    protected List<Map.Entry<ItemStack, GuiAction>> getPageItems() {
        List<Map.Entry<ItemStack, GuiAction>> items = new ArrayList<>();
        List<AuctionListing> listings;

        if (filter == FilterMode.MY_LISTINGS) {
            listings = plugin.getAuctionManager().getPlayerListings(viewer.getUniqueId());
        } else {
            listings = plugin.getAuctionManager().getActiveListings();
        }

        for (AuctionListing listing : listings) {
            if (listing.hasExpired() || listing.getStatus() != AuctionListing.AuctionStatus.ACTIVE) {
                continue;
            }

            if (searchQuery != null && !searchQuery.isBlank()) {
                String searchLower = searchQuery.toLowerCase();
                String typeName = listing.getItem().getType().name().toLowerCase();
                String dispName = listing.getItem().hasItemMeta() && listing.getItem().getItemMeta().hasDisplayName()
                        ? listing.getItem().getItemMeta().getDisplayName().toLowerCase() : "";
                if (!typeName.contains(searchLower) && !dispName.contains(searchLower)) {
                    continue;
                }
            }

            ItemStack icon = listing.getItem().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ColorUtil.colorize("&8&m------------------------"));
                lore.add(ColorUtil.colorize("&7Seller: &f" + listing.getSellerName()));
                lore.add(ColorUtil.colorize("&7Current Bid: &e" + plugin.getEconomyManager().formatAmount(listing.getCurrentBid())));

                if (listing.hasBuyout()) {
                    lore.add(ColorUtil.colorize("&7Buyout: &a" + plugin.getEconomyManager().formatAmount(listing.getBuyoutPrice())));
                }

                String timeColor = listing.getRemainingTime().toMinutes() < 60 ? "&c" : "&e";
                lore.add(ColorUtil.colorize("&7Time Left: " + timeColor + listing.getFormattedTimeRemaining()));

                if (listing.hasBids()) {
                    lore.add(ColorUtil.colorize("&7Highest Bidder: &b" + listing.getCurrentBidderName()));
                } else {
                    lore.add(ColorUtil.colorize("&7No bids yet"));
                }
                lore.add(ColorUtil.colorize("&8&m------------------------"));
                lore.add(ColorUtil.colorize("&eClick to view details & bid!"));

                meta.setLore(lore);
                icon.setItemMeta(meta);
            }

            items.add(new AbstractMap.SimpleEntry<>(icon, event -> {
                new AuctionListingGui(plugin, viewer, listing).open();
            }));
        }

        return items;
    }

    @Override
    protected void populateInventory() {
        super.populateInventory();

        // Filter button at slot 46
        setItem(46, createItem(
                Material.HOPPER,
                "&6&lFilter: " + filter.getDisplay(),
                "&7Click to toggle filter mode",
                "&7Currently showing: " + filter.getDisplay()
        ), event -> {
            filter = (filter == FilterMode.ALL) ? FilterMode.MY_LISTINGS : FilterMode.ALL;
            currentPage = 0;
            refresh();
        });

        // Claims / rewards button at slot 47
        int claimCount = plugin.getAuctionManager().getClaims(viewer.getUniqueId()).size();
        setItem(47, createItem(
                Material.ENDER_CHEST,
                "&d&lCollection / Claims &7(&e" + claimCount + "&7)",
                "&7Collect expired items or sales profits",
                claimCount > 0 ? "&aYou have pending claims!" : "&7No claims available"
        ), event -> {
            new ClaimsGui(plugin, viewer).open();
        });

        // Create new auction button at slot 51
        setItem(51, createItem(
                Material.EMERALD,
                "&a&lCreate Auction",
                "&7List an item on the auction house",
                "&7Hold an item or click to open listing menu"
        ), event -> {
            ItemStack inHand = viewer.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) {
                viewer.sendMessage(ColorUtil.colorize("&cPlease hold the item you want to list in your main hand!"));
                return;
            }
            new AuctionCreateGui(plugin, viewer, inHand).open();
        });
    }
}
