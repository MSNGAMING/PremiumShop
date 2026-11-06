package com.premiumshop.plugin.auction;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.auction.AuctionListing.AuctionStatus;
import com.premiumshop.plugin.auction.AuctionListing.OutbidInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Central manager for the auction house feature.
 *
 * <p>All public mutating operations are executed asynchronously via
 * {@link CompletableFuture} so that database I/O never blocks the main thread.
 * Callbacks that must touch Bukkit API (inventory, messaging) are synchronised
 * back to the main thread using {@link org.bukkit.scheduler.BukkitScheduler}.</p>
 */
public class AuctionManager {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PremiumShopPlugin plugin;

    /** All currently ACTIVE listings, keyed by listing ID. */
    private final Map<String, AuctionListing> activeListings = new ConcurrentHashMap<>();

    /** Maps seller UUID → list of their active listing IDs. */
    private final Map<UUID, List<String>> playerListingIds = new ConcurrentHashMap<>();

    /**
     * Pending claims for each player.
     * Each entry is a list of {@link AuctionListing} objects whose items or
     * payments have not yet been collected by the owner/winner.
     */
    private final Map<UUID, List<AuctionListing>> playerClaims = new ConcurrentHashMap<>();
    private final Set<UUID> bannedPlayers = ConcurrentHashMap.newKeySet();

    /** Repeating task that checks for expired auctions every 10 seconds. */
    private BukkitTask expirationTask;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code AuctionManager} bound to the given plugin instance.
     *
     * @param plugin the owning plugin
     */
    public AuctionManager(PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises the auction manager: loads active listings from the database
     * and starts the periodic expiration-check task.
     */
    public void initialize() {
        ensureTable();
        loadListingsFromDatabase().thenRun(() ->
                plugin.getLogger().info("[AuctionManager] Loaded "
                        + activeListings.size() + " active listing(s) from database.")
        );

        // Check expirations every 10 seconds (200 ticks)
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkExpirations, 200L, 200L);
    }

    /**
     * Alias for {@link #initialize()} used by the plugin lifecycle.
     */
    public void load() {
        initialize();
    }

    /**
     * Shuts down the manager, cancelling the expiration task.
     */
    public void shutdown() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
    }

    /**
     * Alias for {@link #shutdown()} used by the plugin lifecycle.
     */
    public void save() {
        shutdown();
    }

    // -------------------------------------------------------------------------
    // Database bootstrap
    // -------------------------------------------------------------------------

    /**
     * Ensures the {@code auction_listings} table exists, creating it if needed.
     */
    private void ensureTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS auction_listings ("
                + "id VARCHAR(36) PRIMARY KEY, "
                + "seller_uuid VARCHAR(36) NOT NULL, "
                + "seller_name VARCHAR(64) NOT NULL, "
                + "item_data MEDIUMTEXT NOT NULL, "
                + "start_bid DOUBLE NOT NULL, "
                + "buyout_price DOUBLE NOT NULL DEFAULT -1, "
                + "current_bid DOUBLE NOT NULL, "
                + "current_bidder_uuid VARCHAR(36), "
                + "current_bidder_name VARCHAR(64), "
                + "end_time_millis BIGINT NOT NULL, "
                + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', "
                + "created_at BIGINT NOT NULL"
                + ");";
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(ddl)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AuctionManager] Failed to create auction_listings table: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Load from DB
    // -------------------------------------------------------------------------

    /**
     * Asynchronously loads all ACTIVE listings from the database, populating
     * {@link #activeListings} and {@link #playerListingIds}.
     *
     * @return a {@link CompletableFuture} that completes when loading is done
     */
    public CompletableFuture<Void> loadListingsFromDatabase() {
        return CompletableFuture.runAsync(() -> {
            String sql = "SELECT * FROM auction_listings WHERE status = 'ACTIVE'";
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String itemData = rs.getString("item_data");
                    ItemStack item;
                    try {
                        item = deserializeItem(itemData);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[AuctionManager] Could not deserialize item for listing "
                                + rs.getString("id") + ": " + e.getMessage());
                        continue;
                    }

                    AuctionListing listing = AuctionListing.fromResultSet(rs, item);
                    activeListings.put(listing.getId(), listing);
                    playerListingIds
                            .computeIfAbsent(listing.getSellerUuid(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(listing.getId());
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[AuctionManager] Failed to load listings: " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Create listing
    // -------------------------------------------------------------------------

    /**
     * Creates a new auction listing for the given seller.
     *
     * <p>This method:
     * <ol>
     *   <li>Validates that the player has not exceeded their maximum listing count.</li>
     *   <li>Charges the configured listing fee.</li>
     *   <li>Persists the new listing to the database.</li>
     *   <li>Adds the listing to the in-memory maps.</li>
     * </ol>
     *
     * @param seller        the selling player
     * @param item          the item to auction (cloned; the original should be removed
     *                      from the player's inventory by the caller)
     * @param startBid      minimum starting bid
     * @param buyoutPrice   immediate-purchase price, or {@code -1} for none
     * @param durationHours how many hours the auction should run
     * @return a {@link CompletableFuture} that resolves to the created {@link AuctionListing},
     *         or {@code null} if validation failed
     */
    public CompletableFuture<AuctionListing> createListing(
            Player seller, ItemStack item, double startBid, double buyoutPrice, int durationHours) {

        return CompletableFuture.supplyAsync(() -> {
            // 1. Check listing count
            int maxListings = plugin.getConfig().getInt("auction.maxListingsPerPlayer", 10);
            List<String> existing = playerListingIds.getOrDefault(seller.getUniqueId(), Collections.emptyList());
            if (existing.size() >= maxListings) {
                syncMessage(seller, plugin.getLang("auction.error.tooManyListings"));
                return null;
            }

            // 2. Listing fee
            double feePercent = plugin.getConfig().getDouble("auction.listingFeePercent", 2.0);
            double fee = startBid * (feePercent / 100.0);
            if (!plugin.getEconomyManager().has(seller, fee)) {
                syncMessage(seller, plugin.getLang("auction.error.cannotAffordFee")
                        .replace("{fee}", String.format("%.2f", fee)));
                return null;
            }
            plugin.getEconomyManager().withdraw(seller, fee);

            // 3. Build listing object
            String id = UUID.randomUUID().toString();
            long now  = System.currentTimeMillis();
            long endTimeMillis = now + (long) durationHours * 3600_000L;

            AuctionListing listing = new AuctionListing(
                    id,
                    seller.getUniqueId(),
                    seller.getName(),
                    item,
                    startBid,
                    buyoutPrice,
                    startBid,          // currentBid starts at startBid
                    null,              // no bidder yet
                    null,
                    endTimeMillis,
                    AuctionStatus.ACTIVE,
                    now);

            // 4. Serialize item and persist
            String itemData = serializeItem(item);
            Object[] values = listing.toInsertValues();
            values[3] = itemData; // fill in item_data placeholder

            String sql = "INSERT INTO auction_listings "
                    + "(id, seller_uuid, seller_name, item_data, start_bid, buyout_price, "
                    + "current_bid, current_bidder_uuid, current_bidder_name, end_time_millis, status, created_at) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) {
                    ps.setObject(i + 1, values[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[AuctionManager] Failed to insert listing: " + e.getMessage());
                // Refund the fee
                plugin.getEconomyManager().deposit(seller, fee);
                return null;
            }

            // 5. Add to in-memory maps
            activeListings.put(id, listing);
            playerListingIds
                    .computeIfAbsent(seller.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(id);

            syncMessage(seller, plugin.getLang("auction.listingCreated")
                    .replace("{id}", id.substring(0, 8)));
            return listing;
        });
    }

    // -------------------------------------------------------------------------
    // Place bid
    // -------------------------------------------------------------------------

    /**
     * Places a bid on the specified listing on behalf of the bidder.
     *
     * @param bidder    the player placing the bid
     * @param listingId the target listing ID
     * @param bidAmount the offered bid amount
     * @return {@code true} if the bid was accepted; {@code false} otherwise
     */
    public CompletableFuture<Boolean> placeBid(Player bidder, String listingId, double bidAmount) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionListing listing = activeListings.get(listingId);
            if (listing == null) {
                syncMessage(bidder, plugin.getLang("auction.error.notFound"));
                return false;
            }

            synchronized (listing) {
                if (listing.getStatus() != AuctionStatus.ACTIVE || listing.hasExpired()) {
                    syncMessage(bidder, plugin.getLang("auction.error.notActive"));
                    return false;
                }
                if (bidder.getUniqueId().equals(listing.getSellerUuid())) {
                    syncMessage(bidder, plugin.getLang("auction.error.bidOnOwn"));
                    return false;
                }
                if (bidder.getUniqueId().equals(listing.getCurrentBidderUuid())) {
                    syncMessage(bidder, plugin.getLang("auction.error.alreadyHighest"));
                    return false;
                }

                double minIncrement = plugin.getConfig().getDouble("auction.minBidIncrementPercent", 5.0);
                double minimumBid;
                if (listing.hasBids()) {
                    minimumBid = listing.getCurrentBid() * (1.0 + minIncrement / 100.0);
                } else {
                    minimumBid = listing.getStartBid();
                }

                if (bidAmount < minimumBid) {
                    syncMessage(bidder, plugin.getLang("auction.error.bidTooLow")
                            .replace("{min}", String.format("%.2f", minimumBid)));
                    return false;
                }

                if (!plugin.getEconomyManager().has(bidder, bidAmount)) {
                    syncMessage(bidder, plugin.getLang("auction.error.cannotAffordBid"));
                    return false;
                }

                // Charge bidder
                plugin.getEconomyManager().withdraw(bidder, bidAmount);

                // Place bid and capture outbid info
                OutbidInfo outbid = listing.placeBid(bidder.getUniqueId(), bidder.getName(), bidAmount, minIncrement);

                // Refund previous bidder
                if (outbid.bidderUuid() != null && outbid.amount() > 0) {
                    Player prev = Bukkit.getPlayer(outbid.bidderUuid());
                    if (prev != null && prev.isOnline()) {
                        plugin.getEconomyManager().deposit(prev, outbid.amount());
                        syncMessage(prev, plugin.getLang("auction.outbid")
                                .replace("{item}", getItemName(listing.getItem()))
                                .replace("{amount}", String.format("%.2f", outbid.amount())));
                    } else {
                        // Store refund in claims for offline player
                        addPaymentClaim(outbid.bidderUuid(), outbid.bidderName(), outbid.amount(), listing);
                    }
                }

                // Possibly extend end time (anti-snipe)
                long extendSeconds = plugin.getConfig().getLong("auction.extendOnBidSeconds", 60L);
                if (extendSeconds > 0) {
                    long timeLeft = listing.getEndTimeMillis() - System.currentTimeMillis();
                    if (timeLeft < extendSeconds * 1000L) {
                        listing.setEndTimeMillis(System.currentTimeMillis() + extendSeconds * 1000L);
                    }
                }

                // Persist to DB
                updateBidInDb(listing);

                syncMessage(bidder, plugin.getLang("auction.bidPlaced")
                        .replace("{amount}", String.format("%.2f", bidAmount))
                        .replace("{item}", getItemName(listing.getItem())));
                return true;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Buyout
    // -------------------------------------------------------------------------

    /**
     * Processes an immediate buyout of the specified listing.
     *
     * @param buyer     the player buying out the listing
     * @param listingId the target listing ID
     * @return {@code true} if the buyout was successful; {@code false} otherwise
     */
    public CompletableFuture<Boolean> buyout(Player buyer, String listingId) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionListing listing = activeListings.get(listingId);
            if (listing == null) {
                syncMessage(buyer, plugin.getLang("auction.error.notFound"));
                return false;
            }

            synchronized (listing) {
                if (listing.getStatus() != AuctionStatus.ACTIVE || listing.hasExpired()) {
                    syncMessage(buyer, plugin.getLang("auction.error.notActive"));
                    return false;
                }
                if (!listing.hasBuyout()) {
                    syncMessage(buyer, plugin.getLang("auction.error.noBuyout"));
                    return false;
                }
                if (buyer.getUniqueId().equals(listing.getSellerUuid())) {
                    syncMessage(buyer, plugin.getLang("auction.error.buyOwnItem"));
                    return false;
                }

                double price = listing.getBuyoutPrice();
                if (!plugin.getEconomyManager().has(buyer, price)) {
                    syncMessage(buyer, plugin.getLang("auction.error.cannotAffordBuyout")
                            .replace("{price}", String.format("%.2f", price)));
                    return false;
                }

                // Mark listing sold FIRST inside lock
                listing.setStatus(AuctionStatus.SOLD);
                removeFromActiveMaps(listing);

                // Charge buyer
                plugin.getEconomyManager().withdraw(buyer, price);

                // Refund current highest bidder if different from buyer
                if (listing.hasBids() && !listing.getCurrentBidderUuid().equals(buyer.getUniqueId())) {
                    UUID prevBidder = listing.getCurrentBidderUuid();
                    double prevBid  = listing.getCurrentBid();
                    Player prevPlayer = Bukkit.getPlayer(prevBidder);
                    if (prevPlayer != null && prevPlayer.isOnline()) {
                        plugin.getEconomyManager().deposit(prevPlayer, prevBid);
                        syncMessage(prevPlayer, plugin.getLang("auction.outbidBuyout")
                                .replace("{item}", getItemName(listing.getItem()))
                                .replace("{amount}", String.format("%.2f", prevBid)));
                    } else {
                        addPaymentClaim(prevBidder, listing.getCurrentBidderName(), prevBid, listing);
                    }
                }

                // Pay seller
                plugin.getEconomyManager().depositByUuid(listing.getSellerUuid(), price);
                Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    syncMessage(seller, plugin.getLang("auction.itemSold")
                            .replace("{item}", getItemName(listing.getItem()))
                            .replace("{price}", String.format("%.2f", price)));
                }

                updateStatusInDb(listing);

                // Give item to buyer or add to claims on main thread safely
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.getItem());
                    if (!leftover.isEmpty()) {
                        // Inventory full – add to claims
                        addItemClaim(buyer.getUniqueId(), buyer.getName(), listing);
                        syncMessage(buyer, plugin.getLang("auction.inventoryFullClaim"));
                    } else {
                        syncMessage(buyer, plugin.getLang("auction.buyoutSuccess")
                                .replace("{item}", getItemName(listing.getItem()))
                                .replace("{price}", String.format("%.2f", price)));
                    }
                });

                return true;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Expiration checker
    // -------------------------------------------------------------------------

    /**
     * Scans all active listings for expired entries and processes them.
     * Called every 10 seconds asynchronously by {@link #expirationTask}.
     */
    public void checkExpirations() {
        List<AuctionListing> expired = activeListings.values().stream()
                .filter(AuctionListing::hasExpired)
                .collect(Collectors.toList());

        for (AuctionListing listing : expired) {
            if (listing.getStatus() != AuctionStatus.ACTIVE) {
                continue; // already processed
            }

            if (listing.hasBids()) {
                // Auction sold to highest bidder
                listing.setStatus(AuctionStatus.SOLD);

                // Pay seller
                double winAmount = listing.getCurrentBid();
                plugin.getEconomyManager().depositByUuid(listing.getSellerUuid(), winAmount);
                Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    syncMessage(seller, plugin.getLang("auction.itemSoldExpiry")
                            .replace("{item}", getItemName(listing.getItem()))
                            .replace("{price}", String.format("%.2f", winAmount)));
                }

                // Give item to winner or store in claims
                UUID winnerUuid = listing.getCurrentBidderUuid();
                Player winner = Bukkit.getPlayer(winnerUuid);
                if (winner != null && winner.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<Integer, ItemStack> leftover = winner.getInventory().addItem(listing.getItem());
                        if (!leftover.isEmpty()) {
                            addItemClaim(winnerUuid, listing.getCurrentBidderName(), listing);
                            syncMessage(winner, plugin.getLang("auction.wonItemClaim")
                                    .replace("{item}", getItemName(listing.getItem())));
                        } else {
                            syncMessage(winner, plugin.getLang("auction.wonItem")
                                    .replace("{item}", getItemName(listing.getItem())));
                        }
                    });
                } else {
                    addItemClaim(winnerUuid, listing.getCurrentBidderName(), listing);
                }

            } else {
                // No bids – return item to seller
                listing.setStatus(AuctionStatus.EXPIRED);
                addItemClaim(listing.getSellerUuid(), listing.getSellerName(), listing);

                Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    syncMessage(seller, plugin.getLang("auction.listingExpired")
                            .replace("{item}", getItemName(listing.getItem())));
                }
            }

            removeFromActiveMaps(listing);
            updateStatusInDb(listing);
        }
    }

    // -------------------------------------------------------------------------
    // Claims
    // -------------------------------------------------------------------------

    /**
     * Gives all pending claims (items and payments) to the specified player.
     *
     * @param player the player claiming their rewards
     */
    public int countUnclaimedItems(UUID uuid) {
        List<AuctionListing> claims = playerClaims.get(uuid);
        return claims != null ? claims.size() : 0;
    }

    public List<String> fetchPendingNotifications(UUID uuid) {
        return Collections.emptyList();
    }

    public int countPendingNotifications(UUID uuid) {
        return 0;
    }

    public void flushPendingCreation(UUID uuid) {
        // Safe lifecycle flush
    }

    public void savePendingAuctionData(UUID uuid) {
        // Safe lifecycle flush
    }

    public void reload() {
        load();
    }

    public int getActiveListingCount() {
        return activeListings.size();
    }

    public boolean isAuctionBanned(UUID uuid) {
        return uuid != null && bannedPlayers.contains(uuid);
    }

    public void applyAuctionBan(UUID uuid) {
        if (uuid != null) {
            bannedPlayers.add(uuid);
        }
    }

    public void removeAuctionBan(UUID uuid) {
        if (uuid != null) {
            bannedPlayers.remove(uuid);
        }
    }

    public List<String> getActiveListingIds(UUID uuid) {
        List<String> list = playerListingIds.get(uuid);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public boolean cancelListing(UUID playerUuid, int listingId) {
        return cancelListing(playerUuid, String.valueOf(listingId));
    }

    public boolean cancelListing(UUID playerUuid, String listingId) {
        AuctionListing listing = activeListings.get(listingId);
        if (listing == null) {
            return false;
        }
        if (!listing.getSellerUuid().equals(playerUuid)) {
            return false;
        }
        if (listing.hasBids()) {
            return false;
        }

        listing.setStatus(AuctionStatus.CANCELLED);
        removeFromActiveMaps(listing);
        updateStatusInDb(listing);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(listing.getItem());
                if (!leftover.isEmpty()) {
                    addItemClaim(playerUuid, player.getName(), listing);
                }
            });
        } else {
            addItemClaim(playerUuid, listing.getSellerName(), listing);
        }
        return true;
    }

    public int claimItems(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return 0;
        return claimItems(player);
    }

    /**
     * Gives all pending claims (items and payments) to the specified player.
     *
     * @param player the player claiming their rewards
     * @return count of items and payments claimed
     */
    public int claimItems(Player player) {
        List<AuctionListing> claims = playerClaims.remove(player.getUniqueId());
        if (claims == null || claims.isEmpty()) {
            player.sendMessage(plugin.getLang("auction.noClaims"));
            return 0;
        }

        int itemsClaimed = 0;
        int paymentsCollected = 0;

        for (AuctionListing claim : claims) {
            // Items are stored in SOLD listings where this player is the winner,
            // or EXPIRED/CANCELLED listings where they are the seller.
            if (shouldClaimItem(claim, player.getUniqueId())) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(claim.getItem());
                if (!leftover.isEmpty()) {
                    // Re-queue the remaining items
                    playerClaims
                            .computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(claim);
                    player.sendMessage(plugin.getLang("auction.claimInventoryFull"));
                } else {
                    itemsClaimed++;
                }
            } else {
                // Payment claim – deposit the current bid to the player
                double amount = claim.getCurrentBid();
                plugin.getEconomyManager().deposit(player, amount);
                paymentsCollected++;
            }
        }

        player.sendMessage(plugin.getLang("auction.claimSummary")
                .replace("{items}", String.valueOf(itemsClaimed))
                .replace("{payments}", String.valueOf(paymentsCollected)));
        return itemsClaimed + paymentsCollected;
    }

    // -------------------------------------------------------------------------
    // Cancel listing
    // -------------------------------------------------------------------------

    /**
     * Cancels an active listing owned by the specified player, provided no bids
     * have been placed.
     *
     * @param player    the listing owner
     * @param listingId the listing to cancel
     * @return a {@link CompletableFuture} resolving to {@code true} on success
     */
    public CompletableFuture<Boolean> cancelListing(Player player, String listingId) {
        return CompletableFuture.supplyAsync(() -> {
            AuctionListing listing = activeListings.get(listingId);
            if (listing == null) {
                syncMessage(player, plugin.getLang("auction.error.notFound"));
                return false;
            }
            if (!listing.getSellerUuid().equals(player.getUniqueId())) {
                syncMessage(player, plugin.getLang("auction.error.notOwner"));
                return false;
            }
            if (listing.hasBids()) {
                syncMessage(player, plugin.getLang("auction.error.hasBids"));
                return false;
            }

            listing.setStatus(AuctionStatus.CANCELLED);
            removeFromActiveMaps(listing);
            updateStatusInDb(listing);

            // Return item to seller
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(listing.getItem());
                if (!leftover.isEmpty()) {
                    addItemClaim(player.getUniqueId(), player.getName(), listing);
                    syncMessage(player, plugin.getLang("auction.cancelledItemClaim"));
                } else {
                    syncMessage(player, plugin.getLang("auction.cancelled"));
                }
            });

            return true;
        });
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    /**
     * @return an unmodifiable list of all ACTIVE listings, sorted by ascending end time
     */
    public List<AuctionListing> getActiveListings() {
        return activeListings.values().stream()
                .filter(l -> l.getStatus() == AuctionStatus.ACTIVE)
                .sorted(Comparator.comparingLong(AuctionListing::getEndTimeMillis))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the active listings belonging to the given player.
     *
     * @param playerUuid the player's UUID
     * @return list of active listings for that player
     */
    public List<AuctionListing> getPlayerListings(UUID playerUuid) {
        List<String> ids = playerListingIds.getOrDefault(playerUuid, Collections.emptyList());
        return ids.stream()
                .map(activeListings::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns all pending claims for the given player.
     *
     * @param playerUuid the player's UUID
     * @return list of pending claim listings
     */
    public List<AuctionListing> getClaims(UUID playerUuid) {
        return Collections.unmodifiableList(
                playerClaims.getOrDefault(playerUuid, Collections.emptyList()));
    }

    /**
     * Looks up an active listing by its ID.
     *
     * @param id listing ID
     * @return an {@link Optional} containing the listing, or empty if not found
     */
    public Optional<AuctionListing> getListingById(String id) {
        return Optional.ofNullable(activeListings.get(id));
    }

    // -------------------------------------------------------------------------
    // Item serialisation
    // -------------------------------------------------------------------------

    /**
     * Serialises an {@link ItemStack} to a Base-64-encoded YAML string.
     *
     * @param item the item to serialise
     * @return Base-64 encoded YAML string
     */
    public String serializeItem(ItemStack item) {
        return com.premiumshop.plugin.util.ItemSerializer.serialize(item);
    }

    public ItemStack deserializeItem(String data) {
        return com.premiumshop.plugin.util.ItemSerializer.deserialize(data);
    }

    // -------------------------------------------------------------------------
    // DB update helpers
    // -------------------------------------------------------------------------

    private void updateBidInDb(AuctionListing listing) {
        String sql = "UPDATE auction_listings SET current_bid=?, current_bidder_uuid=?, "
                + "current_bidder_name=?, end_time_millis=? WHERE id=?";
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, listing.getCurrentBid());
            ps.setString(2, listing.getCurrentBidderUuid() != null
                    ? listing.getCurrentBidderUuid().toString() : null);
            ps.setString(3, listing.getCurrentBidderName());
            ps.setLong(4, listing.getEndTimeMillis());
            ps.setString(5, listing.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AuctionManager] Failed to update bid in DB: " + e.getMessage());
        }
    }

    private void updateStatusInDb(AuctionListing listing) {
        String sql = "UPDATE auction_listings SET status=? WHERE id=?";
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, listing.getStatus().name());
            ps.setString(2, listing.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AuctionManager] Failed to update status in DB: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void removeFromActiveMaps(AuctionListing listing) {
        activeListings.remove(listing.getId());
        List<String> ids = playerListingIds.get(listing.getSellerUuid());
        if (ids != null) {
            ids.remove(listing.getId());
        }
    }

    /**
     * Adds an item-claim entry for a player so they can collect it later.
     */
    private void addItemClaim(UUID playerUuid, String playerName, AuctionListing listing) {
        playerClaims
                .computeIfAbsent(playerUuid, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(listing);
    }

    /**
     * Adds a payment-claim entry (refund) so an offline player can collect it later.
     * We repurpose the AuctionListing as a lightweight carrier here; the amount is
     * stored as {@code currentBid} for consistency.
     */
    private void addPaymentClaim(UUID playerUuid, String playerName, double amount, AuctionListing source) {
        // Build a minimal listing shell to hold the payment amount
        AuctionListing paymentShell = new AuctionListing(
                "refund-" + UUID.randomUUID(),
                source.getSellerUuid(),
                source.getSellerName(),
                new ItemStack(Material.GOLD_INGOT),
                amount,
                -1,
                amount,
                playerUuid,
                playerName,
                0L,
                AuctionStatus.SOLD,
                System.currentTimeMillis());
        playerClaims
                .computeIfAbsent(playerUuid, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(paymentShell);
    }

    /**
     * Returns {@code true} when the claim represents an item the player should receive.
     */
    private boolean shouldClaimItem(AuctionListing claim, UUID playerUuid) {
        // Item claims: seller when expired/cancelled, or winner when sold
        if (claim.getStatus() == AuctionStatus.EXPIRED || claim.getStatus() == AuctionStatus.CANCELLED) {
            return playerUuid.equals(claim.getSellerUuid());
        }
        if (claim.getStatus() == AuctionStatus.SOLD) {
            return playerUuid.equals(claim.getCurrentBidderUuid());
        }
        // Payment refund shells start with "refund-" and are payment claims
        return false;
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace('_', ' ');
    }

    /** Sends a message to a player on the main thread safely. */
    private void syncMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }
}
