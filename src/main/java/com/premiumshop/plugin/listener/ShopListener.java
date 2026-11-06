package com.premiumshop.plugin.listener;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.GuiManager;
import com.premiumshop.plugin.auction.AuctionManager;
import com.premiumshop.plugin.database.DatabaseManager;
import com.premiumshop.plugin.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Listener for in-world shop interactions, player join events, and entity protection stubs.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detecting right-clicks on {@code [PremiumShop]} signs and opening the appropriate shop GUI</li>
 *   <li>Upserting player data in the database asynchronously on join</li>
 *   <li>Sending pending auction notifications to players on join</li>
 *   <li>Placeholder for future shop NPC entity protection</li>
 * </ul>
 */
public class ShopListener implements Listener {

    /** The first line a sign must display (after color-stripping) to be recognized as a shop sign. */
    private static final String SIGN_HEADER = "[PremiumShop]";

    private final PremiumShopPlugin plugin;
    private final GuiManager guiManager;
    private final DatabaseManager databaseManager;
    private final AuctionManager auctionManager;

    /**
     * Constructs a new ShopListener.
     *
     * @param plugin          the owning plugin instance
     * @param guiManager      the GUI manager used to open shop windows
     * @param databaseManager the database manager for player data persistence
     * @param auctionManager  the auction manager for notification dispatch
     */
    public ShopListener(final PremiumShopPlugin plugin) {
        this(plugin, plugin.getGuiManager(), plugin.getDatabaseManager(), plugin.getAuctionManager());
    }

    public ShopListener(final PremiumShopPlugin plugin,
                        final GuiManager guiManager,
                        final DatabaseManager databaseManager,
                        final AuctionManager auctionManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.databaseManager = databaseManager;
        this.auctionManager = auctionManager;
    }

    /**
     * Handles right-click interaction with blocks.
     *
     * <p>When a player right-clicks a sign whose first line (stripped of color codes) is
     * {@value #SIGN_HEADER}, this method extracts the shop ID from line 2 and opens that
     * shop for the player. If line 2 is blank, the default shop is opened instead.
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        final Material type = block.getType();
        if (!isSign(type)) {
            return;
        }

        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        final Player player = event.getPlayer();

        // Modern Paper 1.20+ / 1.21+ / 26.x sign API
        org.bukkit.block.sign.SignSide side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        String firstLineRaw = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(side.line(0)).trim();
        if (firstLineRaw.isEmpty()) {
            firstLineRaw = ColorUtil.stripColor(sign.getLines()[0]).trim();
        }

        if (!SIGN_HEADER.equalsIgnoreCase(firstLineRaw)) {
            return;
        }

        // Cancel to avoid opening the sign editor or triggering default sign behaviour.
        event.setCancelled(true);

        if (!player.hasPermission("premiumshop.shop")) {
            player.sendMessage(ColorUtil.colorize("&cYou do not have permission to use this shop."));
            return;
        }

        String secondLineRaw = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(side.line(1)).trim();
        final String shopId = (!secondLineRaw.isBlank())
                ? secondLineRaw
                : plugin.getConfig().getString("default-shop", "main");

        // Open on the main thread (we are already on it from the event).
        guiManager.openShop(player, shopId);
    }

    /**
     * Placeholder handler for entity damage events.
     *
     * <p>This method is reserved for future shop NPC protection logic (e.g., preventing
     * players from attacking Citizens/MythicMobs shop NPCs). Currently a no-op.
     *
     * @param event the entity damage by entity event
     */
    @EventHandler
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        // Reserved for future shop NPC protection.
        // Example implementation when NPC support is added:
        // if (ShopNpcRegistry.isShopNpc(event.getEntity())) { event.setCancelled(true); }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given material is any variant of a wall or standing sign.
     *
     * @param material the material to test
     * @return {@code true} if the material is a sign type
     */
    private boolean isSign(final Material material) {
        final String name = material.name();
        return name.endsWith("_SIGN") || name.endsWith("_WALL_SIGN") || name.endsWith("_HANGING_SIGN");
    }
}
