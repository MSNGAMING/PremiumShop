package com.premiumshop.plugin.listener;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.database.DatabaseManager;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles player join and quit events for the Premium Shop plugin.
 *
 * <p>On join:
 * <ul>
 *   <li>Asynchronously upserts the player's display name and join timestamp into {@code player_data}</li>
 *   <li>Checks for unclaimed auction items and notifies the player</li>
 *   <li>Fetches and delivers stored outbid/won notifications from the database</li>
 * </ul>
 *
 * <p>On quit:
 * <ul>
 *   <li>Flushes any dirty in-memory state (e.g., pending auction creation data)</li>
 * </ul>
 */
public class PlayerJoinListener implements Listener {

    private final PremiumShopPlugin plugin;
    private final DatabaseManager databaseManager;
    private final AuctionManager auctionManager;

    /**
     * Constructs a new PlayerJoinListener.
     *
     * @param plugin          the owning plugin instance
     * @param databaseManager the database manager used for player data upserts and queries
     * @param auctionManager  the auction manager used to query and clear pending notifications
     */
    public PlayerJoinListener(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getDatabaseManager(), plugin.getAuctionManager());
    }

    public PlayerJoinListener(final PremiumShopPlugin plugin,
                              final DatabaseManager databaseManager,
                              final AuctionManager auctionManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.auctionManager = auctionManager;
    }

    /**
     * Fires when a player successfully joins the server.
     *
     * <p>All database I/O is performed asynchronously via
     * {@link org.bukkit.scheduler.BukkitScheduler#runTaskAsynchronously} to avoid
     * blocking the main thread. Any player-facing messages are dispatched back to the
     * main thread.
     *
     * @param event the player join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. Upsert player_data with current name and last_seen.
            try {
                databaseManager.upsertPlayerData(uuid, name);
            } catch (final Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlayerJoinListener] Failed to upsert player data for " + name, ex);
            }

            // 2. Check for unclaimed auction items.
            int unclaimedCount = 0;
            try {
                unclaimedCount = auctionManager.countUnclaimedItems(uuid);
            } catch (final Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlayerJoinListener] Failed to query unclaimed items for " + name, ex);
            }

            // 3. Fetch pending notifications (outbid / auction won).
            List<String> notifications = List.of();
            try {
                notifications = auctionManager.fetchPendingNotifications(uuid);
            } catch (final Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlayerJoinListener] Failed to fetch notifications for " + name, ex);
            }

            // Capture final copies for lambda.
            final int finalUnclaimed = unclaimedCount;
            final List<String> finalNotifications = notifications;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (finalUnclaimed > 0) {
                    player.sendMessage(ColorUtil.colorize(
                            "&6[Auction House] &eYou have &a" + finalUnclaimed
                            + " &eunclaimed item(s)! Use &b/ah claim &eto collect them."));
                }

                if (!finalNotifications.isEmpty()) {
                    player.sendMessage(ColorUtil.colorize("&6[Auction House] &ePending notifications:"));
                    for (final String note : finalNotifications) {
                        player.sendMessage(ColorUtil.colorize("  &7» &f" + note));
                    }
                }
            });
        });
    }

    /**
     * Fires when a player leaves the server.
     *
     * <p>Flushes any dirty in-memory state that has not yet been persisted, such as
     * partially configured auction listings that were abandoned mid-creation.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        // Flush any in-progress GUI state synchronously (safe — this is the main thread).
        auctionManager.flushPendingCreation(uuid);

        // Persist anything that remains dirty asynchronously.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                auctionManager.savePendingAuctionData(uuid);
                databaseManager.updateLastSeen(uuid);
            } catch (final Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlayerJoinListener] Failed to flush state for " + player.getName(), ex);
            }
        });
    }
}
