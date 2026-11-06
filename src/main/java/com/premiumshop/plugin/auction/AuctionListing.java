package com.premiumshop.plugin.auction;

import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

/**
 * Represents a single auction listing in the Premium Shop auction house.
 *
 * <p>An {@code AuctionListing} tracks all state for one item being sold
 * via the auction mechanism: the seller, the current highest bid, the
 * optional buyout price, and the auction's end time.</p>
 */
public class AuctionListing {

    // -------------------------------------------------------------------------
    // Status enum
    // -------------------------------------------------------------------------

    /**
     * Lifecycle status of an auction listing.
     */
    public enum AuctionStatus {
        /** Auction is accepting bids. */
        ACTIVE,
        /** Item was sold (buyout or winning bid). */
        SOLD,
        /** Auction ended without a sale. */
        EXPIRED,
        /** Seller cancelled the listing before it expired. */
        CANCELLED
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Unique identifier (UUID string) for this listing. */
    private final String id;

    /** UUID of the player who created this listing. */
    private final UUID sellerUuid;

    /** Display name of the seller at the time of listing creation. */
    private final String sellerName;

    /** The item being auctioned (never mutated after creation). */
    private final ItemStack item;

    /** Minimum starting bid. */
    private final double startBid;

    /**
     * Optional buyout price. {@code -1} if no buyout is offered.
     * Any positive value is a valid buyout price.
     */
    private final double buyoutPrice;

    /** Current highest bid amount (equals {@link #startBid} when no bids placed). */
    private double currentBid;

    /** UUID of the current highest bidder, or {@code null} if no bids have been placed. */
    private UUID currentBidderUuid;

    /** Display name of the current highest bidder, or {@code null} if no bids have been placed. */
    private String currentBidderName;

    /** Wall-clock epoch millis at which this auction expires. */
    private long endTimeMillis;

    /** Current lifecycle status. */
    private AuctionStatus status;

    /** Wall-clock epoch millis when this listing was created. */
    private final long createdAt;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Full constructor used for both new listings and listings rehydrated from
     * persistent storage.
     *
     * @param id               unique listing identifier (UUID string)
     * @param sellerUuid       seller's UUID
     * @param sellerName       seller's display name
     * @param item             the item being auctioned (cloned internally)
     * @param startBid         minimum starting bid amount
     * @param buyoutPrice      immediate-purchase price, or {@code -1} for none
     * @param currentBid       current highest bid (use {@code startBid} when no bids yet)
     * @param currentBidderUuid UUID of the current highest bidder, or {@code null}
     * @param currentBidderName name of the current highest bidder, or {@code null}
     * @param endTimeMillis    epoch millis when the auction ends
     * @param status           current status of the listing
     * @param createdAt        epoch millis when the listing was created
     */
    public AuctionListing(
            String id,
            UUID sellerUuid,
            String sellerName,
            ItemStack item,
            double startBid,
            double buyoutPrice,
            double currentBid,
            UUID currentBidderUuid,
            String currentBidderName,
            long endTimeMillis,
            AuctionStatus status,
            long createdAt) {

        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.startBid = startBid;
        this.buyoutPrice = buyoutPrice;
        this.currentBid = currentBid;
        this.currentBidderUuid = currentBidderUuid;
        this.currentBidderName = currentBidderName;
        this.endTimeMillis = endTimeMillis;
        this.status = status;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return unique listing identifier (UUID string) */
    public String getId() { return id; }

    /** @return seller's UUID */
    public UUID getSellerUuid() { return sellerUuid; }

    /** @return seller's display name at listing creation time */
    public String getSellerName() { return sellerName; }

    /** @return a clone of the auctioned item */
    public ItemStack getItem() { return item.clone(); }

    /** @return minimum starting bid */
    public double getStartBid() { return startBid; }

    /** @return buyout price, or {@code -1} if no buyout */
    public double getBuyoutPrice() { return buyoutPrice; }

    /** @return current highest bid amount */
    public double getCurrentBid() { return currentBid; }

    /** @return UUID of the current highest bidder, or {@code null} */
    public UUID getCurrentBidderUuid() { return currentBidderUuid; }

    /** @return name of the current highest bidder, or {@code null} */
    public String getCurrentBidderName() { return currentBidderName; }

    /** @return epoch millis at which this auction expires */
    public long getEndTimeMillis() { return endTimeMillis; }

    /** @return current lifecycle status */
    public AuctionStatus getStatus() { return status; }

    /** @return epoch millis when this listing was created */
    public long getCreatedAt() { return createdAt; }

    // -------------------------------------------------------------------------
    // Setters (mutable fields only)
    // -------------------------------------------------------------------------

    /** @param currentBid new highest bid amount */
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }

    /** @param currentBidderUuid UUID of the new highest bidder */
    public void setCurrentBidderUuid(UUID currentBidderUuid) { this.currentBidderUuid = currentBidderUuid; }

    /** @param currentBidderName display name of the new highest bidder */
    public void setCurrentBidderName(String currentBidderName) { this.currentBidderName = currentBidderName; }

    /** @param endTimeMillis new auction expiry time (epoch millis) */
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    /** @param status new lifecycle status */
    public void setStatus(AuctionStatus status) { this.status = status; }

    // -------------------------------------------------------------------------
    // Business logic helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the auction's end time has already passed.
     *
     * @return {@code true} if expired
     */
    public boolean hasExpired() {
        return System.currentTimeMillis() > endTimeMillis;
    }

    /**
     * Returns {@code true} if at least one bid has been placed on this listing.
     *
     * @return {@code true} if bids exist
     */
    public boolean hasBids() {
        return currentBidderUuid != null;
    }

    /**
     * Returns {@code true} if a buyout price has been configured for this listing.
     *
     * @return {@code true} if a buyout price is available
     */
    public boolean hasBuyout() {
        return buyoutPrice > 0;
    }

    /**
     * Returns the {@link Duration} remaining until the auction expires.
     * Returns {@link Duration#ZERO} if the auction has already expired.
     *
     * @return non-negative remaining duration
     */
    public Duration getRemainingTime() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    /**
     * Returns a human-readable string of the remaining auction time, e.g.
     * {@code "2d 14h 30m"}, {@code "45m 12s"}, or {@code "Ended"} if expired.
     *
     * @return formatted remaining time string
     */
    public String getFormattedTimeRemaining() {
        long remaining = endTimeMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return "Ended";
        }

        long totalSeconds = remaining / 1000L;
        long days    = totalSeconds / 86400L;
        long hours   = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m");
        }
        if (days == 0 && hours == 0) {
            // Show seconds when less than one hour remains
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    /**
     * Encapsulates the outgoing refund information returned by {@link #placeBid}.
     *
     * @param bidderUuid  UUID of the outbid player (may be {@code null} when no previous bid)
     * @param bidderName  name of the outbid player (may be {@code null})
     * @param amount      amount to refund (0 when no previous bid)
     */
    public record OutbidInfo(UUID bidderUuid, String bidderName, double amount) {}

    /**
     * Places a new bid on this listing, updating internal state.
     *
     * <p>Callers are responsible for taking payment from the new bidder <em>before</em>
     * calling this method and for issuing a refund to the previous bidder using the
     * returned {@link OutbidInfo}.</p>
     *
     * @param bidderUuid    UUID of the player placing the bid
     * @param bidderName    display name of the player placing the bid
     * @param bid           bid amount
     * @param minIncrement  minimum increment percentage (e.g. 5.0 = 5 %)
     * @return {@link OutbidInfo} containing the previous bidder's details and the
     *         amount that must be refunded, or {@code null} if the bid is invalid
     * @throws IllegalArgumentException if bid does not meet minimum requirements
     */
    public OutbidInfo placeBid(UUID bidderUuid, String bidderName, double bid, double minIncrement) {
        double minimumBid;
        if (hasBids()) {
            minimumBid = currentBid * (1.0 + minIncrement / 100.0);
        } else {
            minimumBid = startBid;
        }

        if (bid < minimumBid) {
            throw new IllegalArgumentException(
                    String.format("Bid of %.2f does not meet minimum of %.2f", bid, minimumBid));
        }

        // Capture previous bidder info for refund
        UUID prevBidderUuid = this.currentBidderUuid;
        String prevBidderName = this.currentBidderName;
        double prevBid = hasBids() ? this.currentBid : 0.0;

        // Apply new bid
        this.currentBid = bid;
        this.currentBidderUuid = bidderUuid;
        this.currentBidderName = bidderName;

        return new OutbidInfo(prevBidderUuid, prevBidderName, prevBid);
    }

    // -------------------------------------------------------------------------
    // DB factory / serialisation helpers
    // -------------------------------------------------------------------------

    /**
     * Creates an {@code AuctionListing} from a JDBC {@link ResultSet} row.
     *
     * <p>The {@code item} parameter must be pre-deserialised from the blob/text
     * column before calling this method (item de-serialisation requires the
     * Bukkit API, which is not available inside a plain JDBC callback).</p>
     *
     * @param rs   the current result-set row (not advanced by this method)
     * @param item the deserialised {@link ItemStack}
     * @return a fully constructed {@code AuctionListing}
     * @throws SQLException if any column read fails
     */
    public static AuctionListing fromResultSet(ResultSet rs, ItemStack item) throws SQLException {
        String id             = rs.getString("id");
        UUID sellerUuid       = UUID.fromString(rs.getString("seller_uuid"));
        String sellerName     = rs.getString("seller_name");
        double startBid       = rs.getDouble("start_bid");
        double buyoutPrice    = rs.getDouble("buyout_price");
        double currentBid     = rs.getDouble("current_bid");
        String bidderUuidStr  = rs.getString("current_bidder_uuid");
        UUID currentBidderUuid = bidderUuidStr != null ? UUID.fromString(bidderUuidStr) : null;
        String currentBidderName = rs.getString("current_bidder_name");
        long endTimeMillis    = rs.getLong("end_time_millis");
        AuctionStatus status  = AuctionStatus.valueOf(rs.getString("status"));
        long createdAt        = rs.getLong("created_at");

        return new AuctionListing(
                id, sellerUuid, sellerName, item,
                startBid, buyoutPrice, currentBid,
                currentBidderUuid, currentBidderName,
                endTimeMillis, status, createdAt);
    }

    /**
     * Returns an {@code Object[]} suitable for a parameterised INSERT statement
     * of the form:
     * <pre>
     * INSERT INTO auction_listings
     *   (id, seller_uuid, seller_name, item_data,
     *    start_bid, buyout_price, current_bid,
     *    current_bidder_uuid, current_bidder_name,
     *    end_time_millis, status, created_at)
     * VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
     * </pre>
     * Note: {@code item_data} is intentionally set to {@code null} here because
     * the serialised Base-64 blob is computed by {@link com.premiumshop.plugin.auction.AuctionManager}
     * which has access to the Bukkit scheduler. Replace index 3 with the
     * Base-64 string before executing.
     *
     * @return ordered parameter array (12 elements)
     */
    public Object[] toInsertValues() {
        return new Object[]{
                id,
                sellerUuid.toString(),
                sellerName,
                null,                   // placeholder – caller sets item_data
                startBid,
                buyoutPrice,
                currentBid,
                currentBidderUuid != null ? currentBidderUuid.toString() : null,
                currentBidderName,
                endTimeMillis,
                status.name(),
                createdAt
        };
    }
}
