package com.premiumshop.plugin.economy;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * High-performance, non-blocking engine that calculates dynamic supply and demand
 * pricing using exponential decay models based on server stock and transaction velocity.
 */
public class DynamicPriceEngine implements Runnable {

    private final PremiumShopPlugin plugin;

    private double decayConstantK;
    private double minMultiplier;
    private double maxMultiplier;
    private double velocityWeight;
    private double stockWeight;
    private long updateIntervalTicks;
    private boolean enabled;

    private BukkitTask tickTask;

    /**
     * Cached buy multipliers: shopId -> (itemId -> multiplier)
     */
    private final Map<String, Map<String, Double>> buyMultipliers = new ConcurrentHashMap<>();

    /**
     * Cached sell multipliers: shopId -> (itemId -> multiplier)
     */
    private final Map<String, Map<String, Double>> sellMultipliers = new ConcurrentHashMap<>();

    /**
     * Atomic transaction velocity counters: (shopId + ":" + itemId) -> count
     */
    private final Map<String, AtomicInteger> velocityCounters = new ConcurrentHashMap<>();

    private record PriceSnapshot(String shopId, String itemId, double price, int stock) {}

    public DynamicPriceEngine(PremiumShopPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads pricing parameters from config.yml.
     */
    public void loadConfig() {
        this.enabled = plugin.getConfigManager().getBoolean("dynamic-pricing.enabled", true);
        this.decayConstantK = plugin.getConfigManager().getDouble("dynamic-pricing.decay-constant-k", 0.001);
        this.minMultiplier = plugin.getConfigManager().getDouble("dynamic-pricing.min-price-multiplier", 0.1);
        this.maxMultiplier = plugin.getConfigManager().getDouble("dynamic-pricing.max-price-multiplier", 10.0);
        this.velocityWeight = plugin.getConfigManager().getDouble("dynamic-pricing.velocity-weight", 0.3);
        this.stockWeight = plugin.getConfigManager().getDouble("dynamic-pricing.stock-weight", 0.7);
        this.updateIntervalTicks = plugin.getConfigManager().getLong("dynamic-pricing.update-interval-ticks", 6000L);
    }

    /**
     * Starts the asynchronous price recalculation and snapshot task.
     */
    public synchronized void start() {
        stop();
        if (!enabled) {
            plugin.getLogger().info("Dynamic pricing engine is disabled in configuration.");
            return;
        }

        tickTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this,
                updateIntervalTicks,
                updateIntervalTicks
        );
        plugin.getLogger().info("Dynamic pricing engine scheduled with interval " + updateIntervalTicks + " ticks.");
    }

    /**
     * Stops the recalculation task.
     */
    public synchronized void stop() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    @Override
    public void run() {
        if (!enabled || plugin.getShopManager() == null) {
            return;
        }

        List<PriceSnapshot> snapshots = new ArrayList<>();
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            for (ShopCategory category : shop.getCategories()) {
                for (ShopItem item : category.getItems()) {
                    double currentBuyPrice = recalculateItem(shop.getId(), item);
                    snapshots.add(new PriceSnapshot(shop.getId(), item.getId(), currentBuyPrice, item.getCurrentStock()));
                }
            }
        }

        if (!snapshots.isEmpty()) {
            savePriceSnapshotsBatch(snapshots);
        }
    }

    /**
     * Recalculates multipliers for a single item using the exponential decay and velocity formula.
     *
     * @return current dynamic buy price
     */
    public double recalculateItem(String shopId, ShopItem item) {
        String key = shopId.toLowerCase() + ":" + item.getId().toLowerCase();
        AtomicInteger counter = velocityCounters.get(key);
        int velocity = counter != null ? counter.getAndSet(0) : 0;

        double stockRatio = 0.5; // Neutral default
        if (item.getMaxStock() > 0) {
            stockRatio = Math.max(0.0, Math.min(1.0, (double) item.getCurrentStock() / (double) item.getMaxStock()));
        }

        double velocityFactor = Math.min(1.0, (double) velocity / 100.0);

        // Exponential decay / growth formulas
        double buyMult = Math.exp(decayConstantK * (1.0 - stockRatio) * 100.0) * (1.0 + velocityWeight * velocityFactor);
        buyMult = clamp(buyMult, minMultiplier, maxMultiplier);

        double sellMult = Math.exp(-decayConstantK * stockRatio * 100.0) * (1.0 - velocityWeight * velocityFactor * 0.5);
        sellMult = clamp(sellMult, minMultiplier * 0.5, 1.0);

        // Update caches
        buyMultipliers.computeIfAbsent(shopId.toLowerCase(), k -> new ConcurrentHashMap<>())
                .put(item.getId().toLowerCase(), buyMult);
        sellMultipliers.computeIfAbsent(shopId.toLowerCase(), k -> new ConcurrentHashMap<>())
                .put(item.getId().toLowerCase(), sellMult);

        return buyMult * item.getBaseBuyPrice();
    }

    /**
     * Records a transaction for velocity metrics.
     */
    public void recordTransaction(String shopId, String itemId) {
        String key = shopId.toLowerCase() + ":" + itemId.toLowerCase();
        velocityCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Computes the final dynamic buy price.
     */
    public double getDynamicBuyPrice(String shopId, String itemId, double basePrice) {
        if (!enabled) return basePrice;
        Map<String, Double> map = buyMultipliers.get(shopId.toLowerCase());
        if (map == null) return basePrice;
        Double mult = map.get(itemId.toLowerCase());
        if (mult == null) return basePrice;
        return Math.round(basePrice * mult * 100.0) / 100.0;
    }

    /**
     * Computes the final dynamic sell price.
     */
    public double getDynamicSellPrice(String shopId, String itemId, double basePrice) {
        if (!enabled) return basePrice;
        Map<String, Double> map = sellMultipliers.get(shopId.toLowerCase());
        if (map == null) return basePrice;
        Double mult = map.get(itemId.toLowerCase());
        if (mult == null) return basePrice;
        return Math.round(basePrice * mult * 100.0) / 100.0;
    }

    /**
     * Returns current price multiplier.
     */
    public double getPriceMultiplier(String shopId, String itemId) {
        Map<String, Double> map = buyMultipliers.get(shopId.toLowerCase());
        if (map == null) return 1.0;
        Double mult = map.get(itemId.toLowerCase());
        return mult != null ? mult : 1.0;
    }

    /**
     * Gets a trend string: rising (▲), falling (▼), or stable (—).
     */
    public String getTrendIndicator(String shopId, String itemId) {
        double mult = getPriceMultiplier(shopId, itemId);
        if (mult > 1.05) {
            return "&c▲ Rising";
        } else if (mult < 0.95) {
            return "&a▼ Falling";
        } else {
            return "&7— Stable";
        }
    }

    private void savePriceSnapshotsBatch(List<PriceSnapshot> snapshots) {
        if (plugin.getDatabaseManager() == null) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "INSERT INTO item_price_history (shop_id, item_id, recorded_price, stock) VALUES (?, ?, ?, ?)";
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                con.setAutoCommit(false);
                for (PriceSnapshot s : snapshots) {
                    ps.setString(1, s.shopId());
                    ps.setString(2, s.itemId());
                    ps.setDouble(3, s.price());
                    ps.setInt(4, s.stock());
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
                con.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.FINE, "Failed to record price history batch", e);
            }
        });
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
