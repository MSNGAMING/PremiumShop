package com.premiumshop.plugin.command;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.GuiManager;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.database.DatabaseManager;
import com.premiumshop.plugin.shop.ShopManager;
import com.premiumshop.plugin.util.ColorUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Executor and tab-completer for the {@code /shopadmin} command.
 *
 * <p>All sub-commands require the {@code premiumshop.admin} permission, with
 * {@code premiumshop.admin.editor} additionally required for the {@code editor} sub-command.
 *
 * <p>Sub-command matrix:
 * <pre>
 *   /shopadmin reload
 *   /shopadmin setprice &lt;shopId&gt; &lt;categoryId&gt; &lt;itemId&gt; &lt;buy|sell&gt; &lt;price&gt;
 *   /shopadmin setstock &lt;shopId&gt; &lt;categoryId&gt; &lt;itemId&gt; &lt;amount&gt;
 *   /shopadmin editor &lt;shopId&gt; &lt;categoryId&gt;
 *   /shopadmin resetprices &lt;shopId&gt;
 *   /shopadmin auctionban &lt;player&gt;
 *   /shopadmin info
 * </pre>
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "reload", "setprice", "setstock", "editor", "resetprices", "auctionban", "info", "generatecatalog");

    private final PremiumShopPlugin plugin;
    private final GuiManager guiManager;
    private final ShopManager shopManager;
    private final AuctionManager auctionManager;
    private final DatabaseManager databaseManager;

    /**
     * Constructs a new ShopAdminCommand.
     *
     * @param plugin          the owning plugin instance
     * @param guiManager      GUI manager for opening editor GUIs
     * @param shopManager     shop manager for price/stock mutations
     * @param auctionManager  auction manager for ban management
     * @param databaseManager database manager for stats queries
     */
    public ShopAdminCommand(final PremiumShopPlugin plugin,
                            final GuiManager guiManager,
                            final ShopManager shopManager,
                            final AuctionManager auctionManager,
                            final DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.shopManager = shopManager;
        this.auctionManager = auctionManager;
        this.databaseManager = databaseManager;
    }

    public ShopAdminCommand(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getGuiManager(), plugin.getShopManager(), plugin.getAuctionManager(), plugin.getDatabaseManager());
    }

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String label,
                             final String[] args) {

        if (!sender.hasPermission("premiumshop.admin")) {
            sender.sendMessage(ColorUtil.colorize("&cYou do not have permission to use admin commands."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload"      -> handleReload(sender);
            case "setprice"    -> handleSetPrice(sender, args, label);
            case "setstock"    -> handleSetStock(sender, args, label);
            case "editor"      -> handleEditor(sender, args, label);
            case "resetprices" -> handleResetPrices(sender, args, label);
            case "auctionban"      -> handleAuctionBan(sender, args, label);
            case "info"            -> handleInfo(sender);
            case "generatecatalog" -> handleGenerateCatalog(sender);
            default                -> sendUsage(sender, label);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    /**
     * Reloads all plugin configurations and reinitialises all managers.
     *
     * @param sender the command sender
     */
    private void handleReload(final CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&7[ShopAdmin] Reloading plugin configuration..."));
        try {
            plugin.reloadPluginConfig();
            shopManager.reload();
            auctionManager.reload();
            sender.sendMessage(ColorUtil.colorize("&a[ShopAdmin] Reload complete."));
        } catch (final Exception ex) {
            sender.sendMessage(ColorUtil.colorize("&c[ShopAdmin] Reload failed: " + ex.getMessage()));
            plugin.getLogger().severe("[ShopAdmin] Reload error: " + ex.getMessage());
        }
    }

    /**
     * Sets the buy or sell base price of a specific item in a shop category.
     *
     * <p>Usage: {@code /shopadmin setprice <shopId> <categoryId> <itemId> <buy|sell> <price>}
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param label  the command label
     */
    private void handleSetPrice(final CommandSender sender, final String[] args, final String label) {
        if (args.length < 6) {
            sender.sendMessage(ColorUtil.colorize(
                    "&cUsage: &e/" + label + " setprice <shopId> <categoryId> <itemId> <buy|sell> <price>"));
            return;
        }

        final String shopId = args[1];
        final String categoryId = args[2];
        final String itemId = args[3];
        final String priceType = args[4].toLowerCase();
        final double price;

        if (!priceType.equals("buy") && !priceType.equals("sell")) {
            sender.sendMessage(ColorUtil.colorize("&cPrice type must be &ebuy &cor &esell&c."));
            return;
        }

        try {
            price = Double.parseDouble(args[5]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage(ColorUtil.colorize("&cInvalid price: &e" + args[5]));
            return;
        }

        if (price < 0) {
            sender.sendMessage(ColorUtil.colorize("&cPrice cannot be negative."));
            return;
        }

        final boolean success = shopManager.setBasePrice(shopId, categoryId, itemId, priceType, price);
        if (success) {
            sender.sendMessage(ColorUtil.colorize(
                    "&a[ShopAdmin] Set &e" + priceType + " &aprice for &e" + itemId
                    + " &ain &e" + shopId + "/" + categoryId + " &ato &e" + price + "&a."));
        } else {
            sender.sendMessage(ColorUtil.colorize(
                    "&c[ShopAdmin] Failed to set price. Check that the shop/category/item IDs are correct."));
        }
    }

    /**
     * Sets the current stock level of a specific item in a shop category.
     *
     * <p>Usage: {@code /shopadmin setstock <shopId> <categoryId> <itemId> <amount>}
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param label  the command label
     */
    private void handleSetStock(final CommandSender sender, final String[] args, final String label) {
        if (args.length < 5) {
            sender.sendMessage(ColorUtil.colorize(
                    "&cUsage: &e/" + label + " setstock <shopId> <categoryId> <itemId> <amount>"));
            return;
        }

        final String shopId = args[1];
        final String categoryId = args[2];
        final String itemId = args[3];
        final int amount;

        try {
            amount = Integer.parseInt(args[4]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage(ColorUtil.colorize("&cInvalid amount: &e" + args[4]));
            return;
        }

        if (amount < 0) {
            sender.sendMessage(ColorUtil.colorize("&cStock amount cannot be negative."));
            return;
        }

        final boolean success = shopManager.setStock(shopId, categoryId, itemId, amount);
        if (success) {
            sender.sendMessage(ColorUtil.colorize(
                    "&a[ShopAdmin] Set stock for &e" + itemId
                    + " &ain &e" + shopId + "/" + categoryId + " &ato &e" + amount + "&a."));
        } else {
            sender.sendMessage(ColorUtil.colorize(
                    "&c[ShopAdmin] Failed to set stock. Check that the shop/category/item IDs are correct."));
        }
    }

    /**
     * Opens the in-game editor GUI for a specific shop category.
     *
     * <p>Requires the invoking sender to be a {@link Player} with the
     * {@code premiumshop.admin.editor} permission.
     *
     * <p>Usage: {@code /shopadmin editor <shopId> <categoryId>}
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param label  the command label
     */
    private void handleEditor(final CommandSender sender, final String[] args, final String label) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can open the editor."));
            return;
        }

        if (!player.hasPermission("premiumshop.admin.editor")) {
            player.sendMessage(ColorUtil.colorize("&cYou do not have permission to use the in-game editor."));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(ColorUtil.colorize(
                    "&cUsage: &e/" + label + " editor <shopId> <categoryId>"));
            return;
        }

        final String shopId = args[1];
        final String categoryId = args[2];

        final boolean opened = guiManager.openEditorGui(player, shopId, categoryId);
        if (!opened) {
            player.sendMessage(ColorUtil.colorize(
                    "&c[ShopAdmin] Could not open editor for &e" + shopId + "/" + categoryId
                    + "&c. Check that the shop and category exist."));
        }
    }

    /**
     * Resets all dynamically adjusted prices for a shop back to their configured base values.
     *
     * <p>Usage: {@code /shopadmin resetprices <shopId>}
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param label  the command label
     */
    private void handleResetPrices(final CommandSender sender, final String[] args, final String label) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: &e/" + label + " resetprices <shopId>"));
            return;
        }

        final String shopId = args[1];

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final int resetCount = shopManager.resetDynamicPrices(shopId);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage(ColorUtil.colorize(
                            "&a[ShopAdmin] Reset &e" + resetCount + " &adynamic price(s) for shop &e" + shopId + "&a.")));
        });
    }

    /**
     * Toggles the auction-house ban status of a player.
     *
     * <p>If the player is currently banned they will be unbanned, and vice versa.
     * The ban state is persisted in the plugin's data storage.
     *
     * <p>Usage: {@code /shopadmin auctionban <player>}
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param label  the command label
     */
    @SuppressWarnings("deprecation")
    private void handleAuctionBan(final CommandSender sender, final String[] args, final String label) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: &e/" + label + " auctionban <player>"));
            return;
        }

        final String targetName = args[1];
        final OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ColorUtil.colorize("&cPlayer &e" + targetName + " &chas never joined this server."));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean isBanned = auctionManager.isAuctionBanned(target.getUniqueId());
            if (isBanned) {
                auctionManager.removeAuctionBan(target.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorUtil.colorize(
                            "&a[ShopAdmin] Removed auction ban from &e" + targetName + "&a."));
                    final Player online = target.getPlayer();
                    if (online != null) {
                        online.sendMessage(ColorUtil.colorize("&aYour auction house access has been restored."));
                    }
                });
            } else {
                auctionManager.applyAuctionBan(target.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorUtil.colorize(
                            "&c[ShopAdmin] &e" + targetName + " &chas been banned from the auction house."));
                    final Player online = target.getPlayer();
                    if (online != null) {
                        online.sendMessage(ColorUtil.colorize(
                                "&cYou have been banned from the auction house by an administrator."));
                    }
                });
            }
        });
    }

    /**
     * Displays runtime statistics about the plugin.
     *
     * @param sender the command sender
     */
    private void handleInfo(final CommandSender sender) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final int activeShops     = shopManager.getActiveShopCount();
            final int activeAuctions  = auctionManager.getActiveListingCount();
            final long totalTx        = databaseManager.getTotalTransactionCount();
            final String dbType       = databaseManager.getDatabaseType();
            final String version      = plugin.getDescription().getVersion();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ColorUtil.colorize("&6&l=== Premium Shop Info ==="));
                sender.sendMessage(ColorUtil.colorize("  &eVersion:           &f" + version));
                sender.sendMessage(ColorUtil.colorize("  &eDatabase:          &f" + dbType));
                sender.sendMessage(ColorUtil.colorize("  &eActive Shops:       &f" + activeShops));
                sender.sendMessage(ColorUtil.colorize("  &eActive Auctions:    &f" + activeAuctions));
                sender.sendMessage(ColorUtil.colorize("  &eTotal Transactions: &f" + totalTx));
            });
        });
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public List<String> onTabComplete(final CommandSender sender,
                                      final Command command,
                                      final String label,
                                      final String[] args) {

        if (!sender.hasPermission("premiumshop.admin")) {
            return List.of();
        }

        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, completions);
            return completions;
        }

        final String sub = args[0].toLowerCase();

        switch (sub) {
            case "setprice", "setstock", "editor" -> {
                if (args.length == 2) {
                    // Complete shop IDs.
                    StringUtil.copyPartialMatches(args[1], shopManager.getRegisteredShopIds(), completions);
                } else if (args.length == 3) {
                    // Complete category IDs for the selected shop.
                    final List<String> cats = shopManager.getCategoryIds(args[1]);
                    StringUtil.copyPartialMatches(args[2], cats, completions);
                } else if (args.length == 4) {
                    // Complete item IDs for the selected category.
                    final List<String> items = shopManager.getItemIds(args[1], args[2]);
                    StringUtil.copyPartialMatches(args[3], items, completions);
                } else if (args.length == 5 && sub.equals("setprice")) {
                    StringUtil.copyPartialMatches(args[4], Arrays.asList("buy", "sell"), completions);
                }
            }
            case "resetprices" -> {
                if (args.length == 2) {
                    StringUtil.copyPartialMatches(args[1], shopManager.getRegisteredShopIds(), completions);
                }
            }
            case "auctionban" -> {
                if (args.length == 2) {
                    final List<String> playerNames = plugin.getServer().getOnlinePlayers()
                            .stream()
                            .map(Player::getName)
                            .toList();
                    StringUtil.copyPartialMatches(args[1], playerNames, completions);
                }
            }
            default -> { /* no completions */ }
        }

        return completions;
    }

    // -------------------------------------------------------------------------
    // Paper BasicCommand Implementation
    // -------------------------------------------------------------------------

    @Override
    public void execute(final CommandSourceStack stack, final String[] args) {
        onCommand(stack.getSender(), null, "shopadmin", args);
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack stack, final String[] args) {
        final List<String> completions = onTabComplete(stack.getSender(), null, "shopadmin", args);
        return completions != null ? completions : Collections.emptyList();
    }

    @Override
    public boolean canUse(final CommandSender sender) {
        return sender.hasPermission("premiumshop.admin");
    }

    @Override
    public String permission() {
        return "premiumshop.admin";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void handleGenerateCatalog(final CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&7[ShopAdmin] Generating full survival item catalog..."));
        try {
            com.premiumshop.plugin.shop.Shop shop = com.premiumshop.plugin.shop.ShopCatalogGenerator.generateDefaultShop();
            shopManager.registerShop(shop);
            shopManager.saveShops();
            int itemCount = shop.getCategories().stream().mapToInt(c -> c.getItems().size()).sum();
            sender.sendMessage(ColorUtil.colorize("&a[ShopAdmin] Successfully generated " + itemCount
                    + " survival items across " + shop.getCategories().size() + " categories!"));
        } catch (final Exception ex) {
            sender.sendMessage(ColorUtil.colorize("&c[ShopAdmin] Generation failed: " + ex.getMessage()));
            plugin.getLogger().severe("[ShopAdmin] Generate catalog error: " + ex.getMessage());
        }
    }

    /**
     * Sends the full command usage to the given sender.
     *
     * @param sender the recipient
     * @param label  the command label
     */
    private void sendUsage(final CommandSender sender, final String label) {
        sender.sendMessage(ColorUtil.colorize("&6&l[ShopAdmin] &eCommands:"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " reload"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " generatecatalog"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " setprice <shop> <cat> <item> <buy|sell> <price>"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " setstock <shop> <cat> <item> <amount>"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " editor <shop> <cat>"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " resetprices <shop>"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " auctionban <player>"));
        sender.sendMessage(ColorUtil.colorize("  &b/" + label + " info"));
    }
}
