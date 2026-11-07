package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Creation GUI where players configure starting bid, buyout price,
 * and listing duration before publishing an auction listing.
 */
public class AuctionCreateGui extends AbstractGui {

    private ItemStack itemToList;
    private double startBid = 100.0;
    private double buyoutPrice = 500.0;
    private int durationHours = 24;
    private boolean isSubmitting = false;

    public AuctionCreateGui(PremiumShopPlugin plugin, Player viewer, ItemStack itemToList) {
        super(plugin, viewer, "&5&lCreate Auction Listing", 54);
        this.itemToList = itemToList != null ? itemToList.clone() : new ItemStack(Material.STONE);
        int defaultDuration = plugin.getConfigManager().getInt("auction.default-duration-hours", 24);
        this.durationHours = Math.max(1, defaultDuration);
    }

    public AuctionCreateGui(PremiumShopPlugin plugin, AuctionManager auctionManager, Player viewer) {
        this(plugin, viewer, viewer.getInventory().getItemInMainHand());
    }

    public void prepareItemDropSlot(Player player) {
        setItem(13, itemToList);
    }

    public void onItemPlaced(Player player, ItemStack item) {
        this.itemToList = item != null ? item.clone() : new ItemStack(Material.STONE);
        refresh();
    }

    public void onItemRemoved(Player player) {
        this.itemToList = new ItemStack(Material.STONE);
        refresh();
    }

    @Override
    protected void populateInventory() {
        setFillerAll();

        double feePercent = plugin.getConfigManager().getDouble("auction.listing-fee-percent", 2.0);
        double listingFee = Math.round(startBid * (feePercent / 100.0) * 100.0) / 100.0;

        // Preview item at slot 13
        setItem(13, itemToList);

        // Starting bid at slot 11
        setItem(11, createItem(
                Material.EMERALD,
                "&a&lStarting Bid: &e" + plugin.getEconomyManager().formatAmount(startBid),
                "&7Left-click: &a+50",
                "&7Right-click: &c-50",
                "&7Shift-click: &a+500"
        ), event -> {
            if (event.isShiftClick()) {
                startBid += 500;
            } else if (event.isLeftClick()) {
                startBid += 50;
            } else if (event.isRightClick()) {
                startBid = Math.max(10.0, startBid - 50);
            }
            if (buyoutPrice > 0 && buyoutPrice < startBid) {
                buyoutPrice = startBid;
            }
            refresh();
        });

        // Buyout price at slot 15
        String buyoutDisplay = buyoutPrice > 0 ? plugin.getEconomyManager().formatAmount(buyoutPrice) : "&cDisabled";
        setItem(15, createItem(
                Material.DIAMOND,
                "&b&lBuyout Price: &e" + buyoutDisplay,
                "&7Left-click: &a+100",
                "&7Right-click: &c-100",
                "&7Shift-click: &eToggle On/Off"
        ), event -> {
            if (event.isShiftClick()) {
                buyoutPrice = buyoutPrice > 0 ? -1.0 : startBid * 2;
            } else if (event.isLeftClick()) {
                if (buyoutPrice <= 0) buyoutPrice = startBid;
                buyoutPrice += 100;
            } else if (event.isRightClick()) {
                if (buyoutPrice > 0) {
                    buyoutPrice = Math.max(startBid, buyoutPrice - 100);
                }
            }
            refresh();
        });

        // Duration adjustments: Slot 29 (-) and Slot 33 (+)
        setItem(29, createItem(Material.RED_DYE, "&c-1 Hour Duration"), event -> {
            int minDur = plugin.getConfigManager().getInt("auction.min-duration-hours", 1);
            durationHours = Math.max(minDur, durationHours - 1);
            refresh();
        });

        // Duration indicator at slot 31
        setItem(31, createItem(
                Material.CLOCK,
                "&6&lDuration: &e" + durationHours + " Hours",
                "&7The auction will stay active for this duration."
        ));

        setItem(33, createItem(Material.LIME_DYE, "&a+1 Hour Duration"), event -> {
            int maxDur = plugin.getConfigManager().getInt("auction.max-duration-hours", 168);
            durationHours = Math.min(maxDur, durationHours + 1);
            refresh();
        });

        // Cancel at slot 48
        setItem(48, createItem(Material.ARROW, "&c&lCancel"), event -> {
            new AuctionMainGui(plugin, viewer).open();
        });

        // Confirm & Publish at slot 50
        setItem(50, createItem(
                Material.LIME_CONCRETE,
                "&a&lPublish Auction",
                "&7Listing Fee: &e" + plugin.getEconomyManager().formatAmount(listingFee) + " &7(" + feePercent + "%)",
                "&7Starting Bid: &e" + plugin.getEconomyManager().formatAmount(startBid),
                "&7Buyout Price: &e" + buyoutDisplay,
                "&7Duration: &e" + durationHours + "h",
                "",
                "&eClick to pay fee and publish listing!"
        ), event -> {
            if (isSubmitting) return;

            // Remove item from hand
            ItemStack hand = viewer.getInventory().getItemInMainHand();
            if (hand == null || !hand.isSimilar(itemToList)) {
                viewer.sendMessage(ColorUtil.colorize("&cThe item you selected is no longer in your hand!"));
                viewer.closeInventory();
                return;
            }

            int amount = itemToList.getAmount();
            if (hand.getAmount() < amount) {
                viewer.sendMessage(ColorUtil.colorize("&cNot enough items in hand."));
                viewer.closeInventory();
                return;
            }

            isSubmitting = true;
            hand.setAmount(hand.getAmount() - amount);

            plugin.getAuctionManager().createListing(viewer, itemToList, startBid, buyoutPrice, durationHours)
                    .thenAccept(listing -> Bukkit.getScheduler().runTask(plugin, () -> {
                        isSubmitting = false;
                        if (listing != null) {
                            viewer.sendMessage(ColorUtil.colorize("&aListing successfully published to the Auction House!"));
                            new AuctionMainGui(plugin, viewer).open();
                        } else {
                            // Safely refund item on main thread
                            viewer.getInventory().addItem(itemToList);
                            viewer.sendMessage(ColorUtil.colorize("&cFailed to create auction listing."));
                        }
                    }));
        });
    }
}
