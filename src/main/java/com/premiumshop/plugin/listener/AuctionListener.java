package com.premiumshop.plugin.listener;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.AuctionCreateGui;
import com.premiumshop.plugin.gui.AbstractGui;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.economy.EconomyManager;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener dedicated to auction-house specific inventory and player-lifecycle events.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Saving pending auction state when a player disconnects</li>
 *   <li>Sending outbid/won notification summaries to the console on quit</li>
 *   <li>Preparing the item-drop slot in {@link AuctionCreateGui} when it is opened</li>
 *   <li>Handling item placement and removal in slot&nbsp;13 of the create GUI</li>
 * </ul>
 */
public class AuctionListener implements Listener {

    /**
     * The inventory slot index (0-based, within the top inventory) used as the item
     * drop zone in {@link AuctionCreateGui}.
     */
    private static final int ITEM_DROP_SLOT = 13;

    private final PremiumShopPlugin plugin;
    private final AuctionManager auctionManager;
    private final EconomyManager economyManager;

    /**
     * Constructs a new AuctionListener.
     *
     * @param plugin          the owning plugin instance
     * @param auctionManager  the auction manager for state persistence and notifications
     * @param economyManager  the economy manager used to identify currency items
     */
    public AuctionListener(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getAuctionManager(), plugin.getEconomyManager());
    }

    public AuctionListener(final PremiumShopPlugin plugin,
                           final AuctionManager auctionManager,
                           final EconomyManager economyManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.economyManager = economyManager;
    }

    // -------------------------------------------------------------------------
    // Player lifecycle
    // -------------------------------------------------------------------------

    /**
     * Saves any pending auction state for a player who is disconnecting and emits a
     * console summary of unsent outbid / auction-won notifications.
     *
     * @param event the player quit event
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        // Flush any in-progress auction creation state.
        auctionManager.flushPendingCreation(uuid);

        // Run DB work off the main thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                auctionManager.savePendingAuctionData(uuid);

                final int pendingCount = auctionManager.countPendingNotifications(uuid);
                if (pendingCount > 0) {
                    plugin.getLogger().info(String.format(
                            "[AuctionListener] Player %s quit with %d unsent auction notification(s) stored in DB.",
                            player.getName(), pendingCount));
                }
            } catch (final Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[AuctionListener] Failed to save auction data for " + player.getName(), ex);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Inventory open — set up drop zone
    // -------------------------------------------------------------------------

    /**
     * When an {@link AuctionCreateGui} is opened, ensures that slot {@value #ITEM_DROP_SLOT}
     * is cleared of any placeholder item so the player can place their item there.
     *
     * @param event the inventory open event
     */
    @EventHandler
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final Inventory inventory = event.getInventory();
        final InventoryHolder holder = inventory.getHolder();

        if (!(holder instanceof AuctionCreateGui createGui)) {
            return;
        }

        // The GUI renders a glass-pane placeholder in slot 13 during build.
        // After the GUI is fully populated, replace slot 13 with air so the
        // player can place their item. The GUI's own handleClick manages further logic.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                createGui.prepareItemDropSlot(player);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Click handling for AuctionCreateGui — item placement / removal
    // -------------------------------------------------------------------------

    /**
     * Handles item placement and removal in the auction-create GUI.
     *
     * <p>Only processes clicks that land in slot {@value #ITEM_DROP_SLOT} of an open
     * {@link AuctionCreateGui}. The player may:
     * <ul>
     *   <li>Place an item into the slot by clicking with an item in hand</li>
     *   <li>Retrieve a previously placed item by clicking the slot with an empty hand</li>
     * </ul>
     * Items that are air or recognised as currency are rejected with a chat message.
     *
     * @param event the inventory click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null) {
            return;
        }

        final InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof AuctionCreateGui createGui)) {
            return;
        }

        // GuiClickListener (HIGH priority) has already cancelled all clicks in AbstractGui
        // instances. We handle our specific logic here and re-cancel as needed.
        event.setCancelled(true);

        // Ignore clicks that are not on the top inventory.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(topInventory)) {
            return;
        }

        // We only care about the designated item drop slot.
        if (event.getSlot() != ITEM_DROP_SLOT) {
            // Delegate all other clicks to the GUI's own handler.
            createGui.handleClick(event);
            return;
        }

        final ItemStack cursor = player.getItemOnCursor();
        final ItemStack currentSlotItem = topInventory.getItem(ITEM_DROP_SLOT);

        final boolean slotIsEmpty = currentSlotItem == null || currentSlotItem.getType() == Material.AIR;
        final boolean cursorIsEmpty = cursor == null || cursor.getType() == Material.AIR;

        if (!cursorIsEmpty) {
            // Player is placing an item into the drop zone.
            if (!isAuctionableItem(player, cursor)) {
                return; // isAuctionableItem already sent the rejection message.
            }

            // Move cursor item to slot 13.
            final ItemStack toPlace = cursor.clone();
            topInventory.setItem(ITEM_DROP_SLOT, toPlace);
            player.setItemOnCursor(null);
            createGui.onItemPlaced(player, toPlace);

        } else if (!slotIsEmpty) {
            // Player is retrieving the item from the drop zone.
            player.setItemOnCursor(currentSlotItem.clone());
            topInventory.setItem(ITEM_DROP_SLOT, null);
            createGui.onItemRemoved(player);

        }
        // If both are empty, nothing happens.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that the given item is eligible for auction listing.
     *
     * <p>An item is considered invalid if:
     * <ul>
     *   <li>It is {@code null} or its material is {@link Material#AIR}</li>
     *   <li>It is recognised as the server's currency item by {@link EconomyManager}</li>
     * </ul>
     *
     * @param player the player attempting to list the item (receives rejection messages)
     * @param item   the item to validate
     * @return {@code true} if the item can be auctioned
     */
    private boolean isAuctionableItem(final Player player, final ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ColorUtil.colorize("&cYou cannot auction an empty item."));
            return false;
        }

        if (economyManager.isCurrencyItem(item)) {
            player.sendMessage(ColorUtil.colorize("&cYou cannot auction the server currency item."));
            return false;
        }

        return true;
    }
}
