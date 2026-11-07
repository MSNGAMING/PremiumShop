package com.premiumshop.plugin.command;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.AuctionCreateGui;
import com.premiumshop.plugin.gui.AuctionMainGui;
import com.premiumshop.plugin.gui.GuiManager;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.util.ColorUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Executor and tab-completer for the {@code /ah} (Auction House) command.
 *
 * <p>Sub-command matrix:
 * <pre>
 *   /ah                       — opens the main auction GUI
 *   /ah open                  — same as above
 *   /ah list                  — opens the create-listing GUI (requires item in hand)
 *   /ah claim                 — claims all won / expired items
 *   /ah cancel &lt;listingId&gt;   — cancels an active listing owned by the player
 *   /ah search &lt;query&gt;       — opens the AH with a search filter
 *   /ah mine                  — opens the AH filtered to the player's own listings
 * </pre>
 *
 * <p>Permission nodes:
 * <ul>
 *   <li>{@code premiumshop.auction} — required for {@code open} / default</li>
 *   <li>{@code premiumshop.auction.list} — required for {@code list}</li>
 *   <li>{@code premiumshop.auction} — required for {@code claim}, {@code cancel},
 *       {@code search}, {@code mine}</li>
 * </ul>
 */
public class AuctionCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private static final List<String> SUB_COMMANDS =
            Arrays.asList("open", "list", "claim", "cancel", "search", "mine");

    private final PremiumShopPlugin plugin;
    private final GuiManager guiManager;
    private final AuctionManager auctionManager;

    /**
     * Constructs a new AuctionCommand.
     *
     * @param plugin         the owning plugin instance
     * @param guiManager     the GUI manager used to open auction GUIs
     * @param auctionManager the auction manager for business logic
     */
    public AuctionCommand(final PremiumShopPlugin plugin,
                          final GuiManager guiManager,
                          final AuctionManager auctionManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
    }

    public AuctionCommand(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getGuiManager(), plugin.getAuctionManager());
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

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("premiumshop.auction")) {
            player.sendMessage(ColorUtil.colorize("&cYou do not have permission to use the auction house."));
            return true;
        }

        // Check if player is auction-banned.
        if (auctionManager.isAuctionBanned(player.getUniqueId())) {
            player.sendMessage(ColorUtil.colorize("&cYou are banned from using the auction house."));
            return true;
        }

        final String sub = args.length == 0 ? "open" : args[0].toLowerCase();

        switch (sub) {
            case "open" -> handleOpen(player);
            case "list"  -> handleList(player, label);
            case "claim" -> handleClaim(player);
            case "cancel" -> handleCancel(player, args, label);
            case "search" -> handleSearch(player, args, label);
            case "mine" -> handleMine(player);
            default -> sendUsage(player, label);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    /**
     * Opens the main auction house browsing GUI.
     *
     * @param player the invoking player
     */
    private void handleOpen(final Player player) {
        final AuctionMainGui gui = new AuctionMainGui(plugin, auctionManager, player, null);
        guiManager.openGui(player, gui);
    }

    /**
     * Opens the auction listing creation GUI.
     *
     * <p>Requires the player to be holding a non-air item, and the
     * {@code premiumshop.auction.list} permission.
     *
     * @param player the invoking player
     * @param label  the command label (for error messages)
     */
    private void handleList(final Player player, final String label) {
        if (!player.hasPermission("premiumshop.auction.list")) {
            player.sendMessage(ColorUtil.colorize("&cYou do not have permission to list items."));
            return;
        }

        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(ColorUtil.colorize("&cHold the item you wish to auction in your main hand."));
            return;
        }

        final AuctionCreateGui gui = new AuctionCreateGui(plugin, auctionManager, player);
        guiManager.openGui(player, gui);
    }

    /**
     * Claims all won and expired auction items for the player.
     *
     * @param player the invoking player
     */
    private void handleClaim(final Player player) {
        // Run off main thread to avoid DB blocking.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final int claimed = auctionManager.claimItems(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (claimed == 0) {
                    player.sendMessage(ColorUtil.colorize("&eYou have no items to claim."));
                } else {
                    player.sendMessage(ColorUtil.colorize(
                            "&a" + claimed + " item(s) have been added to your inventory (or drop chest if full)."));
                }
            });
        });
    }

    /**
     * Cancels an active auction listing.
     *
     * @param player the invoking player
     * @param args   the full argument array (expects args[1] = listingId)
     * @param label  the command label (for error messages)
     */
    private void handleCancel(final Player player, final String[] args, final String label) {
        if (args.length < 2) {
            player.sendMessage(ColorUtil.colorize("&cUsage: &e/" + label + " cancel <listingId>"));
            return;
        }

        final int listingId;
        try {
            listingId = Integer.parseInt(args[1]);
        } catch (final NumberFormatException ex) {
            player.sendMessage(ColorUtil.colorize("&cInvalid listing ID: &e" + args[1]));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean success = auctionManager.cancelListing(player.getUniqueId(), listingId);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (success) {
                    player.sendMessage(ColorUtil.colorize(
                            "&aListing &e#" + listingId + " &ahas been cancelled. Item returned to your claims."));
                } else {
                    player.sendMessage(ColorUtil.colorize(
                            "&cCould not cancel listing &e#" + listingId
                            + "&c. It may not exist or you may not own it."));
                }
            });
        });
    }

    /**
     * Opens the auction house GUI with a search filter pre-applied.
     *
     * @param player the invoking player
     * @param args   the full argument array (args[1..] form the query)
     * @param label  the command label (for error messages)
     */
    private void handleSearch(final Player player, final String[] args, final String label) {
        if (args.length < 2) {
            player.sendMessage(ColorUtil.colorize("&cUsage: &e/" + label + " search <query>"));
            return;
        }
        final String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final AuctionMainGui gui = new AuctionMainGui(plugin, auctionManager, player, query);
        guiManager.openGui(player, gui);
    }

    /**
     * Opens the auction house GUI pre-filtered to show only the player's own listings.
     *
     * @param player the invoking player
     */
    private void handleMine(final Player player) {
        final String filterKey = "seller:" + player.getUniqueId();
        final AuctionMainGui gui = new AuctionMainGui(plugin, auctionManager, player, filterKey);
        guiManager.openGui(player, gui);
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

        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (!player.hasPermission("premiumshop.auction")) {
            return List.of();
        }

        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            // Provide the player's own active listing IDs as completions.
            final List<String> listingIds = auctionManager
                    .getActiveListingIds(player.getUniqueId())
                    .stream()
                    .map(String::valueOf)
                    .toList();
            StringUtil.copyPartialMatches(args[1], listingIds, completions);
        }

        return completions;
    }

    // -------------------------------------------------------------------------
    // Paper BasicCommand Implementation
    // -------------------------------------------------------------------------

    @Override
    public void execute(final CommandSourceStack stack, final String[] args) {
        onCommand(stack.getSender(), null, "auction", args);
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack stack, final String[] args) {
        final List<String> completions = onTabComplete(stack.getSender(), null, "auction", args);
        return completions != null ? completions : Collections.emptyList();
    }

    @Override
    public boolean canUse(final CommandSender sender) {
        return sender.hasPermission("premiumshop.auction");
    }

    @Override
    public String permission() {
        return "premiumshop.auction";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends the full command usage to the player.
     *
     * @param player the recipient
     * @param label  the command label
     */
    private void sendUsage(final Player player, final String label) {
        player.sendMessage(ColorUtil.colorize("&6&l[Auction House] &eCommands:"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " &7— browse listings"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " list &7— list held item"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " claim &7— claim won items"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " cancel <id> &7— cancel your listing"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " search <query> &7— search listings"));
        player.sendMessage(ColorUtil.colorize("  &b/" + label + " mine &7— view your listings"));
    }
}
