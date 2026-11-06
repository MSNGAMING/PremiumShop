package com.premiumshop.plugin.database.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration version 1 — creates all baseline tables required by PremiumShop.
 *
 * <p>All {@code CREATE TABLE} statements use {@code IF NOT EXISTS} so the
 * migration is safe to re-run without data loss.  Auto-increment syntax is
 * adjusted automatically based on the JDBC URL reported by the connection.
 */
public final class MigrationV1_InitialSchema implements DatabaseMigration {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Initial schema creation";
    }

    @Override
    public void migrate(final Connection connection) throws SQLException {
        final boolean isSqlite = isSqlite(connection);

        try (final Statement stmt = connection.createStatement()) {

            // ----------------------------------------------------------------
            // schema_version — tracks which migrations have been applied
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS schema_version ("
                    + "  version      INT PRIMARY KEY,"
                    + "  applied_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "  description  VARCHAR(255)"
                    + ")"
            );

            // ----------------------------------------------------------------
            // shop_transactions — every buy/sell event
            // ----------------------------------------------------------------
            if (isSqlite) {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS shop_transactions ("
                        + "  id               INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "  player_uuid      VARCHAR(36),"
                        + "  shop_id          VARCHAR(64),"
                        + "  item_id          VARCHAR(64),"
                        + "  transaction_type VARCHAR(4),"
                        + "  amount           INT,"
                        + "  price            DOUBLE,"
                        + "  transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
            } else {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS shop_transactions ("
                        + "  id               BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "  player_uuid      VARCHAR(36),"
                        + "  shop_id          VARCHAR(64),"
                        + "  item_id          VARCHAR(64),"
                        + "  transaction_type ENUM('BUY','SELL'),"
                        + "  amount           INT,"
                        + "  price            DOUBLE,"
                        + "  transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
            }

            // ----------------------------------------------------------------
            // auction_listings — active and historical auction records
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS auction_listings ("
                    + "  id                   VARCHAR(36) PRIMARY KEY,"
                    + "  seller_uuid          VARCHAR(36) NOT NULL,"
                    + "  seller_name          VARCHAR(64) NOT NULL,"
                    + "  item_data            MEDIUMTEXT NOT NULL,"
                    + "  start_bid            DOUBLE NOT NULL,"
                    + "  buyout_price         DOUBLE NOT NULL DEFAULT -1,"
                    + "  current_bid          DOUBLE NOT NULL,"
                    + "  current_bidder_uuid  VARCHAR(36),"
                    + "  current_bidder_name  VARCHAR(64),"
                    + "  end_time_millis      BIGINT NOT NULL,"
                    + "  status               VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',"
                    + "  created_at           BIGINT NOT NULL"
                    + ")"
            );

            // ----------------------------------------------------------------
            // player_data — per-player statistics and metadata
            // ----------------------------------------------------------------
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_data ("
                    + "  player_uuid      VARCHAR(36) PRIMARY KEY,"
                    + "  player_name      VARCHAR(16),"
                    + "  total_purchases  INT DEFAULT 0,"
                    + "  total_sales      INT DEFAULT 0,"
                    + "  last_seen        TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")"
            );

            // ----------------------------------------------------------------
            // item_price_history — periodic price / stock snapshots
            // ----------------------------------------------------------------
            if (isSqlite) {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS item_price_history ("
                        + "  id             INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "  shop_id        VARCHAR(64),"
                        + "  item_id        VARCHAR(64),"
                        + "  recorded_price DOUBLE,"
                        + "  stock          INT,"
                        + "  recorded_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
            } else {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS item_price_history ("
                        + "  id             BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "  shop_id        VARCHAR(64),"
                        + "  item_id        VARCHAR(64),"
                        + "  recorded_price DOUBLE,"
                        + "  stock          INT,"
                        + "  recorded_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
                );
            }

            // ----------------------------------------------------------------
            // Record this migration version
            // ----------------------------------------------------------------
            if (isSqlite) {
                stmt.executeUpdate(
                        "INSERT OR IGNORE INTO schema_version (version, description) "
                        + "VALUES (1, 'Initial schema creation')"
                );
            } else {
                stmt.executeUpdate(
                        "INSERT IGNORE INTO schema_version (version, description) "
                        + "VALUES (1, 'Initial schema creation')"
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether the given connection is backed by SQLite by inspecting
     * its JDBC URL.
     *
     * @param connection the active connection
     * @return {@code true} for SQLite, {@code false} for all other databases
     * @throws SQLException if the JDBC metadata cannot be retrieved
     */
    private boolean isSqlite(final Connection connection) throws SQLException {
        final String url = connection.getMetaData().getURL();
        return url != null && url.toLowerCase().startsWith("jdbc:sqlite");
    }
}
