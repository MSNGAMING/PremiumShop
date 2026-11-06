package com.premiumshop.plugin.config;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages all YAML configuration files used by PremiumShop.
 *
 * <p>Three configuration files are managed:
 * <ul>
 *   <li>{@code config.yml}   – main plugin settings (read via {@link PremiumShopPlugin#getConfig()})</li>
 *   <li>{@code shops.yml}    – shop / category / item definitions</li>
 *   <li>{@code auctions.yml} – persisted auction listings</li>
 * </ul>
 *
 * <p>All disk I/O is performed asynchronously via {@link CompletableFuture}
 * and results are synchronised back to the main thread where Bukkit API
 * constraints apply.
 *
 * @author PremiumShop Development Team
 */
public final class ConfigManager {

    private final PremiumShopPlugin plugin;

    // Shops config
    private File              shopsFile;
    private FileConfiguration shopsConfig;

    // Auctions config
    private File              auctionsFile;
    private FileConfiguration auctionsConfig;

    /**
     * Constructs a new ConfigManager.
     *
     * @param plugin the owning plugin instance; must not be {@code null}
     */
    public ConfigManager(final PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Load / Reload
    // -----------------------------------------------------------------------

    /**
     * Loads all configuration files from disk.
     *
     * <p>File I/O is performed off the main thread via {@link CompletableFuture}.
     * The resulting {@link FileConfiguration} objects are assigned on the main
     * thread to avoid visibility issues.
     *
     * <p>Errors during loading are logged but do not propagate as exceptions;
     * the plugin will operate with empty/default configs in that case.
     */
    public void loadAll() {
        // Resolve file paths
        shopsFile    = new File(plugin.getDataFolder(), "shops.yml");
        auctionsFile = new File(plugin.getDataFolder(), "auctions.yml");

        // Ensure the files exist (saveResource should have created them, but
        // guard anyway in case DataFolder was not yet created)
        plugin.getDataFolder().mkdirs();
        if (!shopsFile.exists()) {
            plugin.saveResource("shops.yml", false);
        }
        if (!auctionsFile.exists()) {
            plugin.saveResource("auctions.yml", false);
        }

        // Reload main config from disk first (synchronous – already on main thread)
        plugin.reloadConfig();

        // Load shops.yml and auctions.yml asynchronously, then assign on main thread
        CompletableFuture
            .supplyAsync(() -> {
                FileConfiguration shops    = YamlConfiguration.loadConfiguration(shopsFile);
                FileConfiguration auctions = YamlConfiguration.loadConfiguration(auctionsFile);
                return new FileConfiguration[]{shops, auctions};
            })
            .thenAccept(configs -> Bukkit.getScheduler().runTask(plugin, () -> {
                shopsConfig    = configs[0];
                auctionsConfig = configs[1];
                plugin.getLogger().info("All configuration files loaded successfully.");
            }))
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to load one or more config files asynchronously.", ex);
                // Fall back to synchronous load on the calling thread
                shopsConfig    = YamlConfiguration.loadConfiguration(shopsFile);
                auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);
                return null;
            });

        // Provide synchronous fallback immediately so callers on onEnable()
        // that run right after loadAll() always have non-null configs.
        if (shopsConfig == null) {
            shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        }
        if (auctionsConfig == null) {
            auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);
        }
    }

    /**
     * Re-reads all configuration files from disk, discarding any in-memory
     * changes that were not explicitly saved.
     *
     * <p>This is equivalent to running {@link #loadAll()} again and is exposed
     * so that administrators can use {@code /shopadmin reload} without
     * restarting the server.
     */
    public void reloadAll() {
        // Main config
        plugin.reloadConfig();

        // shops.yml
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);

        // auctions.yml
        auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);

        plugin.getLogger().info("All configuration files reloaded from disk.");
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Saves the in-memory {@code shops.yml} configuration to disk asynchronously.
     *
     * <p>Errors are logged but do not propagate.
     *
     * @return a {@link CompletableFuture} that completes when the file has been
     *         written (or when an error has been logged)
     */
    public CompletableFuture<Void> saveShopsConfig() {
        final FileConfiguration snapshot = shopsConfig;
        return CompletableFuture.runAsync(() -> {
            try {
                snapshot.save(shopsFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save shops.yml to disk.", ex);
            }
        });
    }

    /**
     * Saves the in-memory {@code auctions.yml} configuration to disk asynchronously.
     *
     * <p>Errors are logged but do not propagate.
     *
     * @return a {@link CompletableFuture} that completes when the file has been
     *         written (or when an error has been logged)
     */
    public CompletableFuture<Void> saveAuctionsConfig() {
        final FileConfiguration snapshot = auctionsConfig;
        return CompletableFuture.runAsync(() -> {
            try {
                snapshot.save(auctionsFile);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save auctions.yml to disk.", ex);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Typed getters – main config with default fallback
    // -----------------------------------------------------------------------

    /**
     * Returns the string value at {@code path} in the main config, or
     * {@code ""} if the path does not exist.
     *
     * @param path dot-separated config path
     * @return the string value, never {@code null}
     */
    public String getString(final String path) {
        return getString(path, "");
    }

    public String getString(final String path, final String def) {
        String value = plugin.getConfig().getString(path);
        return value != null ? value : def;
    }

    public int getInt(final String path) {
        return getInt(path, 0);
    }

    public int getInt(final String path, final int def) {
        return plugin.getConfig().getInt(path, def);
    }

    public double getDouble(final String path) {
        return getDouble(path, 0.0);
    }

    public double getDouble(final String path, final double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    public long getLong(final String path, final long def) {
        return plugin.getConfig().getLong(path, def);
    }

    public boolean getBoolean(final String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(final String path, final boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    public boolean isSellAllEnabled() {
        return getBoolean("shop.enable-sell-all", true);
    }

    /**
     * Returns the string list at {@code path} in the main config, or an
     * empty list if the path does not exist.
     *
     * @param path dot-separated config path
     * @return an unmodifiable list of strings, never {@code null}
     */
    public List<String> getStringList(final String path) {
        List<String> list = plugin.getConfig().getStringList(path);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    // -----------------------------------------------------------------------
    // Config accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the loaded {@code shops.yml} {@link FileConfiguration}.
     *
     * @return the shops configuration; never {@code null} after {@link #loadAll()} has completed
     */
    public FileConfiguration getShopsConfig() {
        return shopsConfig;
    }

    /**
     * Returns the loaded {@code auctions.yml} {@link FileConfiguration}.
     *
     * @return the auctions configuration; never {@code null} after {@link #loadAll()} has completed
     */
    public FileConfiguration getAuctionsConfig() {
        return auctionsConfig;
    }

    // -----------------------------------------------------------------------
    // Specialised getters
    // -----------------------------------------------------------------------

    /**
     * Reads the {@code database.type} key from the main config and converts it
     * to the corresponding {@link DatabaseManager.DatabaseType} enum constant.
     *
     * <p>Supported values (case-insensitive): {@code SQLITE}, {@code MYSQL},
     * {@code POSTGRESQL}.  Defaults to {@link DatabaseManager.DatabaseType#SQLITE}
     * if the key is absent or unrecognised.
     *
     * @return the configured database type
     */
    public DatabaseManager.DatabaseType getDatabaseType() {
        String raw = plugin.getConfig().getString("database.type", "SQLITE");
        if (raw == null) {
            return DatabaseManager.DatabaseType.SQLITE;
        }
        try {
            return DatabaseManager.DatabaseType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown database type '" + raw + "' in config.yml – defaulting to SQLITE.");
            return DatabaseManager.DatabaseType.SQLITE;
        }
    }
}
