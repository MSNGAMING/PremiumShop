package com.premiumshop.plugin;

import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.command.AuctionCommand;
import com.premiumshop.plugin.command.ShopAdminCommand;
import com.premiumshop.plugin.command.ShopCommand;
import com.premiumshop.plugin.config.ConfigManager;
import com.premiumshop.plugin.database.DatabaseManager;
import com.premiumshop.plugin.economy.DynamicPriceEngine;
import com.premiumshop.plugin.economy.EconomyManager;
import com.premiumshop.plugin.gui.GuiManager;
import com.premiumshop.plugin.listener.AuctionListener;
import com.premiumshop.plugin.listener.GuiClickListener;
import com.premiumshop.plugin.listener.PlayerJoinListener;
import com.premiumshop.plugin.listener.ShopListener;
import com.premiumshop.plugin.placeholder.PlaceholderManager;
import com.premiumshop.plugin.shop.ShopManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Main entry point for the PremiumShop plugin.
 *
 * <p>PremiumShop is a fully featured, database-backed economy shop plugin for
 * Paper 1.21+.  It supports dynamic pricing, auction houses, per-category
 * permissions, and a fully configurable GUI layer.
 *
 * <p>Life-cycle:
 * <ol>
 *   <li>Managers are initialised in dependency order inside {@link #onEnable()}.</li>
 *   <li>All Bukkit listeners and commands are registered after all managers are
 *       ready.</li>
 *   <li>PlaceholderAPI is hooked if present on the server.</li>
 *   <li>All managers are shut down cleanly inside {@link #onDisable()}.</li>
 * </ol>
 *
 * @author PremiumShop Development Team
 * @version 1.0.0
 */
public final class PremiumShopPlugin extends JavaPlugin {

    /** Singleton instance – set during {@link #onEnable()}, cleared in {@link #onDisable()}. */
    private static PremiumShopPlugin instance;

    // -----------------------------------------------------------------------
    // Manager fields
    // -----------------------------------------------------------------------

    private ConfigManager       configManager;
    private DatabaseManager     databaseManager;
    private ShopManager         shopManager;
    private AuctionManager      auctionManager;
    private DynamicPriceEngine  priceEngine;
    private GuiManager          guiManager;
    private EconomyManager      economyManager;
    /** May be {@code null} when PlaceholderAPI is not installed. */
    private PlaceholderManager  placeholderManager;

    // -----------------------------------------------------------------------
    // Life-cycle
    // -----------------------------------------------------------------------

    /**
     * Called by Paper when the plugin is first enabled.
     *
     * <p>Initialises all managers in strict dependency order.  Any fatal
     * initialisation failure (database, Vault) disables the plugin
     * immediately via {@link #setEnabled(boolean) setEnabled(false)} so that
     * the server continues to run safely without a half-ready shop system.
     */
    @Override
    public void onEnable() {
        instance = this;

        // ── 1. Save default resource files ──────────────────────────────
        saveDefaultConfig();
        saveResource("shops.yml",    false);
        saveResource("auctions.yml", false);

        // ── 2. Config ────────────────────────────────────────────────────
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // ── 3. Database & Migrations ────────────────────────────────────
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.connect();
        } catch (Exception ex) {
            getLogger().severe("Failed to connect to the database: " + ex.getMessage());
            getLogger().severe("PremiumShop is disabling itself to avoid data corruption.");
            setEnabled(false);
            return;
        }

        // ── 5. Economy (Vault) ───────────────────────────────────────────
        try {
            economyManager = new EconomyManager(this);
            economyManager.hook();
        } catch (Exception ex) {
            getLogger().severe("Could not hook Vault / economy provider: " + ex.getMessage());
            setEnabled(false);
            return;
        }

        // ── 6. ShopManager ───────────────────────────────────────────────
        shopManager = new ShopManager(this);
        shopManager.loadShops();

        // ── 7. AuctionManager ────────────────────────────────────────────
        auctionManager = new AuctionManager(this);
        auctionManager.load();

        // ── 8. DynamicPriceEngine + ticker ──────────────────────────────
        priceEngine = new DynamicPriceEngine(this);
        priceEngine.start();

        // ── 9. GuiManager ────────────────────────────────────────────────
        guiManager = new GuiManager(this);

        // ── 10. Register listeners ───────────────────────────────────────
        getServer().getPluginManager().registerEvents(new ShopListener(this),      this);
        getServer().getPluginManager().registerEvents(new AuctionListener(this),   this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(this),  this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // ── 11. Register commands (Native Paper Brigadier Lifecycle) ─────
        registerCommands();

        // ── 12. PlaceholderAPI ───────────────────────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderManager = new PlaceholderManager(this);
            placeholderManager.register();
            getLogger().info("Hooked into PlaceholderAPI successfully.");
        } else {
            getLogger().info("PlaceholderAPI not found – placeholders will be unavailable.");
        }

        // ── 13. Startup banner ───────────────────────────────────────────
        printStartupBanner();
    }

    /**
     * Called by Paper when the plugin is disabled (server shutdown or
     * {@code /reload}).
     *
     * <p>Resources are released in reverse dependency order: tasks first,
     * then data persistence, then connections.
     */
    @Override
    public void onDisable() {
        // Cancel all scheduled tasks owned by this plugin
        getServer().getScheduler().cancelTasks(this);

        // Persist runtime state
        if (shopManager != null) {
            shopManager.saveShops();
        }
        if (auctionManager != null) {
            auctionManager.save();
        }

        // Stop the price engine ticker cleanly
        if (priceEngine != null) {
            priceEngine.stop();
        }

        // Close database connections / pools
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("PremiumShop has been disabled. Goodbye!");
        instance = null;
    }

    // -----------------------------------------------------------------------
    // Public convenience delegators
    // -----------------------------------------------------------------------

    /**
     * Triggers a full save of all shop data to {@code shops.yml}.
     * Delegates to {@link ShopManager#saveShops()}.
     */
    public void saveShopsData() {
        if (shopManager != null) {
            shopManager.saveShops();
        }
    }

    /**
     * Triggers a full reload of all shop data from {@code shops.yml}.
     * Delegates to {@link ShopManager#reloadShops()}.
     */
    public void loadShopsData() {
        if (shopManager != null) {
            shopManager.reloadShops();
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /**
     * Returns the singleton instance of PremiumShopPlugin.
     *
     * @return the plugin instance, or {@code null} if the plugin is not
     *         currently enabled
     */
    public static PremiumShopPlugin getInstance() {
        return instance;
    }

    public void reloadPluginConfig() {
        if (configManager != null) {
            configManager.reloadAll();
        }
        if (shopManager != null) {
            shopManager.reloadShops();
        }
        if (priceEngine != null) {
            priceEngine.loadConfig();
        }
    }

    /**
     * Retrieves a localized/configured message by its key with color codes translated.
     *
     * @param key the message key in config.yml (e.g. "messages.no-permission" or "messages.purchase-success")
     * @return the colorized string
     */
    public String getLang(String key) {
        if (configManager == null) return key;
        String val = configManager.getString("messages." + key);
        if (val == null || val.isEmpty()) {
            val = configManager.getString(key, key);
        }
        return com.premiumshop.plugin.util.ColorUtil.colorize(val);
    }

    /**
     * @return the {@link ConfigManager} responsible for all YAML configurations
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * @return the {@link DatabaseManager} used for all persistent storage
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * @return the {@link ShopManager} that owns all loaded {@link com.premiumshop.plugin.shop.Shop} instances
     */
    public ShopManager getShopManager() {
        return shopManager;
    }

    /**
     * @return the {@link AuctionManager} that manages player-to-player auctions
     */
    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    /**
     * @return the {@link DynamicPriceEngine} used for supply/demand price adjustments
     */
    public DynamicPriceEngine getPriceEngine() {
        return priceEngine;
    }

    /**
     * @return the {@link GuiManager} that builds and tracks open inventory GUIs
     */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * @return the {@link EconomyManager} bridging Vault's Economy API
     */
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    /**
     * @return the {@link PlaceholderManager} for PAPI expansion, or {@code null}
     *         if PlaceholderAPI is not installed
     */
    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Registers commands using Paper's native Brigadier Lifecycle API (LifecycleEvents.COMMANDS),
     * with graceful Bukkit fallback if running under an alternate environment.
     */
    private void registerCommands() {
        try {
            this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                final Commands commands = event.registrar();
                commands.register("shop", "Opens the main shop interface", List.of("ps", "store"), new ShopCommand(this));
                commands.register("auction", "Opens the auction house interface", List.of("ah", "auctionhouse"), new AuctionCommand(this));
                commands.register("shopadmin", "Administrative commands for PremiumShop", List.of("psadmin", "sa"), new ShopAdminCommand(this));
            });
            getLogger().info("Successfully registered commands via Paper Lifecycle API.");
        } catch (Throwable t) {
            getLogger().warning("Could not register commands via Paper Lifecycle API (" + t.getMessage() + "), falling back to Bukkit command registration.");
            registerBukkitCommand("shop", new ShopCommand(this));
            registerBukkitCommand("auction", new AuctionCommand(this));
            registerBukkitCommand("shopadmin", new ShopAdminCommand(this));
        }
    }

    private void registerBukkitCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' is not declared in plugin.yml – skipping Bukkit registration.");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    /**
     * Prints a decorative startup banner to the server console.
     */
    private void printStartupBanner() {
        String version = getPluginMeta().getVersion();
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║        PremiumShop  v" + version + "          ║");
        getLogger().info("║  Economy | Auctions | Dynamic Prices ║");
        getLogger().info("╚══════════════════════════════════════╝");
        getLogger().info("Plugin enabled successfully.");
    }
}
