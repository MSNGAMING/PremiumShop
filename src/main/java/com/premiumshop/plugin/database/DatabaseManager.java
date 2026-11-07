package com.premiumshop.plugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.database.migration.DatabaseMigration;
import com.premiumshop.plugin.database.migration.MigrationV1_InitialSchema;
import com.premiumshop.plugin.database.migration.MigrationV2_AuctionIndexes;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Central database access layer for PremiumShop.
 *
 * <p>Manages a HikariCP connection pool for either SQLite (embedded, single-file)
 * or MySQL (remote, full pool).  Schema versioning is handled through the
 * {@link DatabaseMigration} system: migrations are registered in ascending
 * version order and each is applied exactly once.
 *
 * <p>All blocking I/O helpers ({@link #executeUpdate}, {@link #executeQuery})
 * must be called from an async context.  Use {@link #executeAsync} to dispatch
 * work onto the Bukkit async scheduler when no dedicated thread pool is
 * available.
 */
public final class DatabaseManager {

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Supported backing database types.
     */
    public enum DatabaseType {
        SQLITE,
        MYSQL
    }

    /**
     * Functional callback that processes a {@link ResultSet} and returns a
     * typed result.
     *
     * @param <T> the type produced by the handler
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        /**
         * Processes the supplied {@link ResultSet} and returns a value.
         *
         * @param rs the result set positioned at the first row (or exhausted)
         * @return the processed result; may be {@code null}
         * @throws SQLException if any SQL operation fails
         */
        T handle(ResultSet rs) throws SQLException;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PremiumShopPlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType type;
    private final List<DatabaseMigration> migrations = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code DatabaseManager} bound to the given plugin instance.
     *
     * <p>Call {@link #initialize()} before using any other method.
     *
     * @param plugin the owning {@link PremiumShopPlugin}
     */
    public DatabaseManager(final PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises the connection pool and runs any pending schema migrations.
     *
     * <p>For <b>SQLite</b> the pool is limited to a single connection (SQLite
     * does not support concurrent writers) and WAL journal mode is enabled for
     * improved read concurrency.  For <b>MySQL</b> the full HikariCP pool is
     * configured from values read via {@code ConfigManager}.
     *
     * @throws SQLException if the pool cannot be created or migrations fail
     */
    public void initialize() throws SQLException {
        final String typeName = plugin.getConfigManager()
                .getString("database.type", "SQLITE")
                .toUpperCase();

        try {
            type = DatabaseType.valueOf(typeName);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown database.type '" + typeName
                    + "', defaulting to SQLITE.");
            type = DatabaseType.SQLITE;
        }

        if (type == DatabaseType.SQLITE) {
            setupSqlite();
        } else {
            setupMysql();
        }

        runMigrations();
    }

    /**
     * Alias for {@link #initialize()} called by the plugin lifecycle.
     */
    public void connect() throws SQLException {
        initialize();
    }

    /**
     * Closes the underlying connection pool, releasing all JDBC resources.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    /**
     * Alias for {@link #close()} called by the plugin lifecycle.
     */
    public void disconnect() {
        close();
    }

    // -------------------------------------------------------------------------
    // Pool setup helpers
    // -------------------------------------------------------------------------

    /**
     * Configures HikariCP for an embedded SQLite database.
     */
    private void setupSqlite() throws SQLException {
        final File dbFile = new File(plugin.getDataFolder(), "premiumshop.db");
        plugin.getDataFolder().mkdirs();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (final ClassNotFoundException ex) {
            plugin.getLogger().warning("Failed to explicitly load org.sqlite.JDBC: " + ex.getMessage());
        }

        final HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("PremiumShop-SQLite");

        // Sensible timeouts for a local file-based DB
        config.setConnectionTimeout(30_000L);
        config.setIdleTimeout(600_000L);
        config.setMaxLifetime(1_800_000L);

        dataSource = new HikariDataSource(config);

        // Enable WAL mode for better read concurrency
        try (final Connection conn = dataSource.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA journal_mode=WAL");
            stmt.executeUpdate("PRAGMA synchronous=NORMAL");
            stmt.executeUpdate("PRAGMA foreign_keys=ON");
        }

        plugin.getLogger().info("SQLite database initialised at: " + dbFile.getAbsolutePath());
    }

    /**
     * Configures HikariCP for a remote MySQL database using values from config.
     */
    private void setupMysql() {
        final String host     = plugin.getConfigManager().getString("database.mysql.host", "localhost");
        final int    port     = plugin.getConfigManager().getInt("database.mysql.port", 3306);
        final String dbName   = plugin.getConfigManager().getString("database.mysql.database", "premiumshop");
        final String user     = plugin.getConfigManager().getString("database.mysql.username", "root");
        final String password = plugin.getConfigManager().getString("database.mysql.password", "");
        final int    maxPool  = plugin.getConfigManager().getInt("database.mysql.pool.maximum-pool-size", 10);
        final int    minIdle  = plugin.getConfigManager().getInt("database.mysql.pool.minimum-idle", 2);
        final long   connTimeout  = plugin.getConfigManager().getLong("database.mysql.pool.connection-timeout", 30_000L);
        final long   idleTimeout  = plugin.getConfigManager().getLong("database.mysql.pool.idle-timeout", 600_000L);
        final long   maxLifetime  = plugin.getConfigManager().getLong("database.mysql.pool.max-lifetime", 1_800_000L);

        final HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8mb4"
                + "&useUnicode=true&serverTimezone=UTC");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName("PremiumShop-MySQL");

        // MySQL-specific performance settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("MySQL connection pool initialised (host=" + host + ", db=" + dbName + ").");
    }

    // -------------------------------------------------------------------------
    // Migration runner
    // -------------------------------------------------------------------------

    /**
     * Registers all known {@link DatabaseMigration} implementations in
     * ascending version order and applies each one that has not yet been run.
     *
     * @throws SQLException if the current schema version cannot be queried or
     *                       any migration fails
     */
    public void runMigrations() throws SQLException {
        // Register migrations in strict ascending version order
        migrations.clear();
        migrations.add(new MigrationV1_InitialSchema());
        migrations.add(new MigrationV2_AuctionIndexes());

        try (final Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // Ensure schema_version table exists before we can query it
            try (final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS schema_version ("
                        + "  version      INT PRIMARY KEY,"
                        + "  applied_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "  description  VARCHAR(255)"
                        + ")"
                );
                conn.commit();
            }

            final int currentVersion = getCurrentSchemaVersion(conn);

            int migrationsApplied = 0;
            for (final DatabaseMigration migration : migrations) {
                if (!migration.isApplicable(currentVersion)) {
                    continue;
                }
                try {
                    plugin.getLogger().info("Applying database migration v"
                            + migration.getVersion() + ": " + migration.getDescription());
                    migration.migrate(conn);
                    conn.commit();
                    migrationsApplied++;
                } catch (final SQLException ex) {
                    conn.rollback();
                    throw new SQLException("Failed to apply migration v"
                            + migration.getVersion() + ": " + ex.getMessage(), ex);
                }
            }

            if (migrationsApplied > 0) {
                plugin.getLogger().info("Successfully applied " + migrationsApplied + " database migration(s).");
            } else {
                plugin.getLogger().info("Database schema is up to date (version " + currentVersion + ").");
            }

            conn.setAutoCommit(true);
        }
    }

    /**
     * Reads the highest schema version currently recorded in the
     * {@code schema_version} table.
     *
     * @param conn an open JDBC connection
     * @return the maximum version number, or {@code 0} if the table is empty
     * @throws SQLException on query failure
     */
    private int getCurrentSchemaVersion(final Connection conn) throws SQLException {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(
                     "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Public connection / query helpers
    // -------------------------------------------------------------------------

    /**
     * Borrows a {@link Connection} from the HikariCP pool.
     *
     * <p><b>Callers must close the returned connection</b> (via try-with-resources)
     * to return it to the pool.
     *
     * @return a ready-to-use {@link Connection}
     * @throws SQLException if no connection is available
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialised or has been closed.");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes a DML statement (INSERT / UPDATE / DELETE) using a
     * {@link PreparedStatement} and the supplied positional parameters.
     *
     * @param sql    the parameterised SQL string
     * @param params positional bind parameters
     * @return the number of affected rows
     * @throws SQLException on any SQL error
     */
    public int executeUpdate(final String sql, final Object... params) throws SQLException {
        try (final Connection conn = getConnection();
             final PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);
            return ps.executeUpdate();
        }
    }

    /**
     * Executes a query and passes the resulting {@link ResultSet} to the
     * supplied {@link ResultSetHandler}, returning the handler's result.
     *
     * @param <T>     the return type produced by {@code handler}
     * @param sql     the parameterised SQL query
     * @param handler the callback that processes the {@link ResultSet}
     * @param params  positional bind parameters
     * @return the value returned by {@code handler}
     * @throws SQLException on any SQL error
     */
    public <T> T executeQuery(final String sql,
                               final ResultSetHandler<T> handler,
                               final Object... params) throws SQLException {
        try (final Connection conn = getConnection();
             final PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);
            try (final ResultSet rs = ps.executeQuery()) {
                return handler.handle(rs);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Async helpers
    // -------------------------------------------------------------------------

    /**
     * Dispatches {@code task} onto the Bukkit async scheduler and returns a
     * {@link CompletableFuture} that completes (or completes exceptionally) when
     * the task finishes.
     *
     * @param task the work to perform asynchronously
     * @return a future tracking task completion
     */
    public CompletableFuture<Void> executeAsync(final Runnable task) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (final Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Async database task threw an exception", t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Upserts a player's UUID, username, and last seen timestamp into player_data asynchronously.
     *
     * @param uuid player unique id
     * @param name player username
     */
    public void upsertPlayerData(final java.util.UUID uuid, final String name) {
        if (uuid == null) return;
        executeAsync(() -> {
            String sql = (type == DatabaseType.SQLITE)
                    ? "INSERT INTO player_data (player_uuid, player_name, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP) "
                    + "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name, last_seen = CURRENT_TIMESTAMP"
                    : "INSERT INTO player_data (player_uuid, player_name, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = CURRENT_TIMESTAMP";
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name != null ? name : "Unknown");
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to upsert player data for " + name, e);
            }
        });
    }

    /**
     * Updates the last_seen timestamp for a player asynchronously.
     *
     * @param uuid player unique id
     */
    public void updateLastSeen(final java.util.UUID uuid) {
        if (uuid == null) return;
        executeAsync(() -> {
            String sql = "UPDATE player_data SET last_seen = CURRENT_TIMESTAMP WHERE player_uuid = ?";
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update last_seen for " + uuid, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the active database type.
     *
     * @return {@link DatabaseType#SQLITE} or {@link DatabaseType#MYSQL}
     */
    public DatabaseType getType() {
        return type;
    }

    public String getDatabaseType() {
        return type != null ? type.name() : "UNKNOWN";
    }

    public long getTotalTransactionCount() {
        String sql = "SELECT COUNT(*) FROM shop_transactions";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to count shop transactions", e);
        }
        return 0L;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Binds positional parameters to a {@link PreparedStatement}.
     *
     * @param ps     the statement to bind into
     * @param params the values to bind, in order
     * @throws SQLException if binding fails
     */
    private void bindParameters(final PreparedStatement ps,
                                 final Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
