package com.premiumshop.plugin.database.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration version 2 — adds performance indexes for auction and transaction
 * query patterns.
 *
 * <p>All index creation statements use {@code CREATE INDEX IF NOT EXISTS} so
 * the migration is idempotent and safe to re-run.
 */
public final class MigrationV2_AuctionIndexes implements DatabaseMigration {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "Add performance indexes for auctions, transactions, and price history";
    }

    @Override
    public void migrate(final Connection connection) throws SQLException {
        final boolean isSqlite = isSqlite(connection);

        try (final Statement stmt = connection.createStatement()) {

            // ----------------------------------------------------------------
            // shop_transactions indexes
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_transactions_player "
                    + "ON shop_transactions(player_uuid)"
            );

            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_transactions_item "
                    + "ON shop_transactions(shop_id, item_id)"
            );

            // ----------------------------------------------------------------
            // auction_listings indexes
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_auction_status "
                    + "ON auction_listings(status)"
            );

            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_auction_seller "
                    + "ON auction_listings(seller_uuid)"
            );

            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_auction_end "
                    + "ON auction_listings(end_time_millis)"
            );

            // ----------------------------------------------------------------
            // item_price_history index
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_price_history "
                    + "ON item_price_history(shop_id, item_id, recorded_at)"
            );

            // ----------------------------------------------------------------
            // Record this migration version
            // ----------------------------------------------------------------
            if (isSqlite) {
                stmt.executeUpdate(
                        "INSERT OR IGNORE INTO schema_version (version, description) "
                        + "VALUES (2, 'Add performance indexes for auctions, transactions, and price history')"
                );
            } else {
                stmt.executeUpdate(
                        "INSERT IGNORE INTO schema_version (version, description) "
                        + "VALUES (2, 'Add performance indexes for auctions, transactions, and price history')"
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether the given connection targets SQLite.
     *
     * @param connection the active JDBC connection
     * @return {@code true} when the JDBC URL begins with {@code jdbc:sqlite}
     * @throws SQLException if the metadata cannot be retrieved
     */
    private boolean isSqlite(final Connection connection) throws SQLException {
        final String url = connection.getMetaData().getURL();
        return url != null && url.toLowerCase().startsWith("jdbc:sqlite");
    }
}
