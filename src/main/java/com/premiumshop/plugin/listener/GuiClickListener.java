package com.premiumshop.plugin.listener;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.gui.AbstractGui;
import com.premiumshop.plugin.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;

/**
 * Handles all inventory interactions within plugin-managed GUI windows.
 *
 * <p>This listener is responsible for:
 * <ul>
 *   <li>Cancelling all item movement inside plugin GUIs</li>
 *   <li>Delegating click events to the appropriate {@link AbstractGui}</li>
 *   <li>Cleaning up GUI tracking on inventory close</li>
 *   <li>Blocking drag events that bleed into plugin inventories</li>
 * </ul>
 */
public class GuiClickListener implements Listener {

    private final PremiumShopPlugin plugin;

    /**
     * Constructs a new GuiClickListener.
     *
     * @param plugin the owning plugin instance
     */
    public GuiClickListener(final PremiumShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts all inventory click events.
     *
     * <p>If the clicked inventory's holder is an {@link AbstractGui}, the event is
     * unconditionally cancelled to prevent item movement, then the click is forwarded
     * to {@link AbstractGui#handleClick(InventoryClickEvent)} for logic processing.
     * Shift-click edge cases are also handled to prevent items moving from the player
     * inventory into an open plugin GUI.
     *
     * @param event the inventory click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null) {
            return;
        }

        final InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof AbstractGui gui)) {
            return;
        }

        // Always cancel to prevent any item movement in plugin GUIs.
        event.setCancelled(true);

        // Handle shift-click from bottom inventory: the item would move to the top.
        // The event is already cancelled, so no additional action is needed beyond
        // ensuring the cancellation above covers this branch.
        if (event.isShiftClick() && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            // Shift-click from player inventory targeting the GUI — already cancelled.
            return;
        }

        // Only delegate clicks that land in the top (GUI) inventory.
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(topInventory)) {
            gui.handleClick(event);
        }
    }

    /**
     * Cleans up GUI tracking when a player closes a plugin-managed inventory.
     *
     * @param event the inventory close event
     */
    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final Inventory topInventory = event.getInventory();
        final InventoryHolder holder = topInventory.getHolder();

        if (holder instanceof AbstractGui gui) {
            gui.onClose(player);
            if (plugin.getGuiManager() != null) {
                plugin.getGuiManager().untrackGui(player);
            }
        }
    }

    /**
     * Cancels drag events that would place items into a plugin GUI inventory.
     *
     * <p>An {@link InventoryDragEvent} spans all slots the drag covers. If any slot
     * index falls within the top inventory and that inventory belongs to an
     * {@link AbstractGui}, the entire drag is cancelled.
     *
     * @param event the inventory drag event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null) {
            return;
        }

        final InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof AbstractGui)) {
            return;
        }

        final int topSize = topInventory.getSize();
        final Set<Integer> rawSlots = event.getRawSlots();

        // Raw slots 0..(topSize-1) correspond to the top inventory.
        for (final int rawSlot : rawSlots) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
