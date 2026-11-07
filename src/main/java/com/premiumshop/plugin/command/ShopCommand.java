package com.premiumshop.plugin.command;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.GuiManager;
import com.premiumshop.plugin.util.ColorUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
 * Executor and tab-completer for the {@code /shop} command.
 *
 * <p>Sub-command matrix:
 * <pre>
 *   /shop              — opens the default shop GUI
 *   /shop open         — same as above
 *   /shop open &lt;id&gt;   — opens the specified shop by ID
 *   /shop search &lt;q&gt;  — opens the shop with a search filter pre-applied (future feature)
 * </pre>
 *
 * <p>All usages require the {@code premiumshop.shop} permission.
 */
public class ShopCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private static final List<String> TOP_COMPLETIONS = Arrays.asList("open", "search");

    private final PremiumShopPlugin plugin;
    private final GuiManager guiManager;

    /**
     * Constructs a new ShopCommand.
     *
     * @param plugin     the owning plugin instance
     * @param guiManager the GUI manager responsible for opening shop windows
     */
    public ShopCommand(final PremiumShopPlugin plugin, final GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public ShopCommand(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getGuiManager());
    }

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Routes the command to the appropriate handler. Returns {@code true} in all
     * cases to suppress Bukkit's default usage message (this class manages its own).
     */
    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String label,
                             final String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("premiumshop.shop")) {
            player.sendMessage(ColorUtil.colorize("&cYou do not have permission to use the shop."));
            return true;
        }

        // /shop  or  /shop open  → open default shop
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("open"))) {
            final String defaultShop = plugin.getConfig().getString("default-shop", "main");
            guiManager.openShop(player, defaultShop);
            return true;
        }

        // /shop open <shopId>
        if (args[0].equalsIgnoreCase("open")) {
            if (args.length < 2) {
                sendUsage(player, label);
                return true;
            }
            final String shopId = args[1];
            if (!guiManager.shopExists(shopId)) {
                player.sendMessage(ColorUtil.colorize("&cShop &e" + shopId + " &cdoes not exist."));
                return true;
            }
            guiManager.openShop(player, shopId);
            return true;
        }

        // /shop search <query>
        if (args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) {
                player.sendMessage(ColorUtil.colorize("&cUsage: &e/" + label + " search <query>"));
                return true;
            }
            // Build the query from all remaining arguments to support multi-word searches.
            final String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            // TODO (future): implement cross-shop search GUI.
            // For now, open the default shop with the search term logged/stored for the GUI to pick up.
            final String defaultShop = plugin.getConfig().getString("default-shop", "main");
            player.sendMessage(ColorUtil.colorize(
                    "&7[Shop] Search for &e\"" + query + "\"&7 is coming in a future update. Opening the default shop..."));
            guiManager.openShop(player, defaultShop);
            return true;
        }

        // Unknown sub-command.
        sendUsage(player, label);
        return true;
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Returns:
     * <ul>
     *   <li>First argument: {@code ["open", "search"]}</li>
     *   <li>Second argument when first is {@code "open"}: all registered shop IDs</li>
     * </ul>
     */
    @Override
    public List<String> onTabComplete(final CommandSender sender,
                                      final Command command,
                                      final String label,
                                      final String[] args) {

        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (!player.hasPermission("premiumshop.shop")) {
            return List.of();
        }

        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], TOP_COMPLETIONS, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            final List<String> shopIds = guiManager.getRegisteredShopIds();
            StringUtil.copyPartialMatches(args[1], shopIds, completions);
        }

        return completions;
    }

    // -------------------------------------------------------------------------
    // Paper BasicCommand Implementation
    // -------------------------------------------------------------------------

    @Override
    public void execute(final CommandSourceStack stack, final String[] args) {
        onCommand(stack.getSender(), null, "shop", args);
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack stack, final String[] args) {
        final List<String> completions = onTabComplete(stack.getSender(), null, "shop", args);
        return completions != null ? completions : Collections.emptyList();
    }

    @Override
    public boolean canUse(final CommandSender sender) {
        return sender.hasPermission("premiumshop.shop");
    }

    @Override
    public String permission() {
        return "premiumshop.shop";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends the command usage message to the given player.
     *
     * @param player the recipient
     * @param label  the command label as typed by the player
     */
    private void sendUsage(final Player player, final String label) {
        player.sendMessage(ColorUtil.colorize("&eUsage:"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " &7— open default shop"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " open <shopId> &7— open specific shop"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " search <query> &7— search items"));
    }
}
