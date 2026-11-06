package com.premiumshop.plugin.database.migration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Represents a single, versioned database migration step.
 *
 * <p>Implementations must be registered in ascending version order inside
 * {@code DatabaseManager#runMigrations()}.  Each migration is responsible for
 * creating or altering schema objects and inserting its own record into the
 * {@code schema_version} table so that subsequent restarts can skip it.
 */
public interface DatabaseMigration {

    /**
     * Returns the schema version that this migration produces.
     *
     * @return positive integer version number, unique across all migrations
     */
    int getVersion();

    /**
     * Returns a human-readable description of what this migration does.
     *
     * @return non-null, non-empty description string
     */
    String getDescription();

    /**
     * Applies this migration against the supplied database connection.
     *
     * <p>The connection is <em>not</em> in auto-commit mode; callers commit
     * or roll back as appropriate.  Implementations should be idempotent where
     * possible (e.g. {@code CREATE TABLE IF NOT EXISTS},
     * {@code CREATE INDEX IF NOT EXISTS}).
     *
     * @param connection the active JDBC connection to use
     * @throws SQLException if any SQL statement fails
     */
    void migrate(Connection connection) throws SQLException;

    /**
     * Determines whether this migration should run given the database's current
     * schema version.
     *
     * <p>The default implementation returns {@code true} when this migration's
     * {@link #getVersion()} is strictly greater than {@code currentVersion},
     * meaning it has not yet been applied.
     *
     * @param currentVersion the highest version already present in
     *                       {@code schema_version}; {@code 0} when the table is
     *                       empty or brand new
     * @return {@code true} if the migration should be executed
     */
    default boolean isApplicable(int currentVersion) {
        return getVersion() > currentVersion;
    }
}
