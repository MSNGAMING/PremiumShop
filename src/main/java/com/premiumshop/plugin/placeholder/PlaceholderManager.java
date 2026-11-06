package com.premiumshop.plugin.placeholder;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import com.premiumshop.plugin.shop.ShopItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PlaceholderAPI expansion exposing economic and shop metrics.
 * Identifier: {@code %advancedshop_<token>%}
 */
public class PlaceholderManager extends PlaceholderExpansion {

    private final PremiumShopPlugin plugin;

    // Cached metrics with periodic invalidation
    private final AtomicInteger cachedTotalTransactions = new AtomicInteger(-1);
    private final AtomicReference<String> cachedTopSeller = new AtomicReference<>(null);
    private long lastCacheUpdate = 0L;
    private static final long CACHE_TTL_MILLIS = 300_000L; // 5 minutes

    public PlaceholderManager(PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "advancedshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PremiumDev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        refreshCacheIfNeeded();

        if (identifier.equalsIgnoreCase("balance")) {
            if (player == null) return "0";
            return plugin.getEconomyManager().formatAmount(plugin.getEconomyManager().getBalance(player));
        }

        if (identifier.equalsIgnoreCase("auctions_active")) {
            return String.valueOf(plugin.getAuctionManager().getActiveListings().size());
        }

        if (identifier.equalsIgnoreCase("auctions_mine")) {
            if (player == null) return "0";
            return String.valueOf(plugin.getAuctionManager().getPlayerListings(player.getUniqueId()).size());
        }

        if (identifier.equalsIgnoreCase("auctions_claims")) {
            if (player == null) return "0";
            return String.valueOf(plugin.getAuctionManager().getClaims(player.getUniqueId()).size());
        }

        if (identifier.equalsIgnoreCase("top_seller")) {
            String top = cachedTopSeller.get();
            return top != null ? top : "None";
        }

        if (identifier.equalsIgnoreCase("total_transactions")) {
            int tx = cachedTotalTransactions.get();
            return tx >= 0 ? String.valueOf(tx) : "0";
        }

        // dynamic_price_<shopId>_<itemId>
        if (identifier.startsWith("dynamic_price_")) {
            String remainder = identifier.substring("dynamic_price_".length());
            String[] parts = remainder.split("_", 2);
            if (parts.length == 2) {
                Shop shop = plugin.getShopManager().getShop(parts[0]);
                if (shop != null) {
                    for (ShopCategory cat : shop.getCategories()) {
                        ShopItem item = cat.getItem(parts[1]);
                        if (item != null) {
                            double price = plugin.getPriceEngine() != null
                                    ? plugin.getPriceEngine().getDynamicBuyPrice(shop.getId(), item.getId(), item.getBaseBuyPrice())
                                    : item.getBaseBuyPrice();
                            return plugin.getEconomyManager().formatAmount(price);
                        }
                    }
                }
            }
            return "0";
        }

        // dynamic_sell_<shopId>_<itemId>
        if (identifier.startsWith("dynamic_sell_")) {
            String remainder = identifier.substring("dynamic_sell_".length());
            String[] parts = remainder.split("_", 2);
            if (parts.length == 2) {
                Shop shop = plugin.getShopManager().getShop(parts[0]);
                if (shop != null) {
                    for (ShopCategory cat : shop.getCategories()) {
                        ShopItem item = cat.getItem(parts[1]);
                        if (item != null) {
                            double price = plugin.getPriceEngine() != null
                                    ? plugin.getPriceEngine().getDynamicSellPrice(shop.getId(), item.getId(), item.getBaseSellPrice())
                                    : item.getBaseSellPrice();
                            return plugin.getEconomyManager().formatAmount(price);
                        }
                    }
                }
            }
            return "0";
        }

        // price_trend_<shopId>_<itemId>
        if (identifier.startsWith("price_trend_")) {
            String remainder = identifier.substring("price_trend_".length());
            String[] parts = remainder.split("_", 2);
            if (parts.length == 2 && plugin.getPriceEngine() != null) {
                return plugin.getPriceEngine().getTrendIndicator(parts[0], parts[1]);
            }
            return "—";
        }

        // stock_<shopId>_<categoryId>_<itemId>
        if (identifier.startsWith("stock_")) {
            String remainder = identifier.substring("stock_".length());
            String[] parts = remainder.split("_", 3);
            if (parts.length == 3) {
                Shop shop = plugin.getShopManager().getShop(parts[0]);
                if (shop != null) {
                    ShopCategory cat = shop.getCategory(parts[1]);
                    if (cat != null) {
                        ShopItem item = cat.getItem(parts[2]);
                        if (item != null) {
                            return item.getMaxStock() > 0 ? String.valueOf(item.getCurrentStock()) : "Unlimited";
                        }
                    }
                }
            }
            return "0";
        }

        return null;
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_TTL_MILLIS) {
            return;
        }
        lastCacheUpdate = now;

        if (plugin.getDatabaseManager() == null) return;

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection con = plugin.getDatabaseManager().getConnection()) {
                // Total transactions
                try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM shop_transactions");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cachedTotalTransactions.set(rs.getInt(1));
                    }
                }

                // Top seller
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT player_name FROM player_data ORDER BY total_sales DESC LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cachedTopSeller.set(rs.getString(1));
                    }
                }
            } catch (SQLException ignored) {
            }
        });
    }
}
