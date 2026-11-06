package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for all plugin-managed GUIs.
 *
 * <p>Subclasses implement {@link #populateInventory()} to lay out items and
 * register per-slot {@link GuiAction}s.  Call {@link #open()} to show the
 * inventory to the viewer, and {@link #refresh()} to rebuild it in-place
 * without reopening.</p>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
public abstract class AbstractGui implements InventoryHolder {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Plugin instance used for scheduling and service access. */
    protected final PremiumShopPlugin plugin;

    /** The player who is currently viewing this GUI. */
    protected final Player viewer;

    /** The underlying Bukkit {@link Inventory} backing this GUI. */
    protected Inventory inventory;

    /** Raw (un-colorized) title stored for inventory recreation on refresh. */
    protected String title;

    /** Number of slots in the inventory (must be a multiple of 9, max 54). */
    protected int size;

    /**
     * Map from slot index to the {@link GuiAction} that should fire when that
     * slot is clicked.  Slots without an entry are ignored on click.
     */
    protected final Map<Integer, GuiAction> slotActions = new HashMap<>();

    /**
     * Cache of the last {@link ItemStack} placed in each slot, used to detect
     * whether a slot needs re-rendering during a {@link #refresh()}.
     */
    protected final Map<Integer, ItemStack> cachedItems = new HashMap<>();

    /**
     * When {@code true} the inventory layout is considered stale and will be
     * fully repopulated on the next {@link #refresh()} call.
     */
    protected boolean dirty = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code AbstractGui}.
     *
     * @param plugin the owning plugin instance; must not be {@code null}
     * @param viewer the player for whom the GUI is being built; must not be {@code null}
     * @param title  the inventory title (supports {@code &} colour codes); must not be {@code null}
     * @param size   the inventory size; must be a positive multiple of 9 and ≤ 54
     */
    protected AbstractGui(PremiumShopPlugin plugin, Player viewer, String title, int size) {
        if (plugin == null) throw new IllegalArgumentException("plugin must not be null");
        if (viewer == null) throw new IllegalArgumentException("viewer must not be null");
        if (title == null) throw new IllegalArgumentException("title must not be null");
        if (size <= 0 || size % 9 != 0 || size > 54) {
            throw new IllegalArgumentException("size must be a positive multiple of 9, max 54 (got " + size + ")");
        }
        this.plugin = plugin;
        this.viewer = viewer;
        this.title  = title;
        this.size   = size;
        this.inventory = Bukkit.createInventory(this, size, colorizeComponent(title));
    }

    // -------------------------------------------------------------------------
    // Abstract contract
    // -------------------------------------------------------------------------

    /**
     * Populates the inventory with items and registers slot actions.
     *
     * <p>Called automatically by {@link #open()} and {@link #refresh()}.
     * Subclasses must call {@link #setItem(int, ItemStack, GuiAction)} (or its
     * overloads) rather than manipulating {@link #inventory} directly, so that
     * the action map and cache stay consistent.</p>
     */
    protected abstract void populateInventory();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Opens the GUI for the viewer.
     *
     * <p>Clears all slots, action registrations, and the item cache before
     * calling {@link #populateInventory()}, then opens the inventory.</p>
     */
    public void open() {
        clearAll();
        populateInventory();
        viewer.openInventory(inventory);
    }

    /**
     * Marks the GUI as dirty and re-populates its contents in-place.
     *
     * <p>Unlike {@link #open()}, this method does <em>not</em> reopen the
     * inventory from scratch, so the viewer's cursor position is preserved.</p>
     */
    public void refresh() {
        dirty = true;
        clearAll();
        populateInventory();
        dirty = false;
    }

    /**
     * Places an {@link ItemStack} in the given slot and registers a click action.
     *
     * @param slot   the inventory slot index (0-based)
     * @param item   the item to display; may be {@code null} to clear the slot
     * @param action the {@link GuiAction} to fire on click; may be {@code null}
     */
    public void setItem(int slot, ItemStack item, GuiAction action) {
        if (slot < 0 || slot >= size) return;
        inventory.setItem(slot, item);
        cachedItems.put(slot, item);
        if (action != null) {
            slotActions.put(slot, action);
        } else {
            slotActions.remove(slot);
        }
    }

    /**
     * Places an {@link ItemStack} in the given slot with no click action.
     *
     * @param slot the inventory slot index (0-based)
     * @param item the item to display; may be {@code null} to clear the slot
     */
    public void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Places a glass-pane filler item in each of the specified slots.
     *
     * @param material the filler material (typically a stained glass pane)
     * @param slots    the slot indices to fill
     */
    public void setFiller(Material material, int... slots) {
        ItemStack filler = buildGlassPane(material);
        for (int slot : slots) {
            setItem(slot, filler);
        }
    }

    /**
     * Fills every currently empty slot with the default light-grey glass pane.
     */
    public void setFillerAll() {
        ItemStack filler = buildGlassPane();
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) == null) {
                setItem(slot, filler);
            }
        }
    }

    /**
     * Routes an {@link InventoryClickEvent} to the registered {@link GuiAction}
     * for the clicked slot, if one exists.
     *
     * <p>The event is always cancelled before the action is executed.</p>
     *
     * @param event the click event; must not be {@code null}
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= size) return;
        GuiAction action = slotActions.get(slot);
        if (action != null) {
            action.execute(event);
        }
    }

    /**
     * Closes the viewer's currently open inventory, triggering
     * {@link org.bukkit.event.inventory.InventoryCloseEvent}.
     */
    public void close() {
        viewer.closeInventory();
    }

    // -------------------------------------------------------------------------
    // Item creation utilities
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link ItemStack} with a custom display name and optional lore.
     *
     * @param material the material for the item; must not be {@code null}
     * @param name     the display name (supports {@code &} colour codes); must not be {@code null}
     * @param lore     zero or more lore lines (each supports {@code &} colour codes)
     * @return a new {@link ItemStack} with the given meta applied
     */
    public ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, 1, lore);
    }

    /**
     * Builds an {@link ItemStack} with a custom display name, stack amount, and optional lore.
     *
     * @param material the material for the item; must not be {@code null}
     * @param name     the display name (supports {@code &} colour codes); must not be {@code null}
     * @param amount   the stack size; clamped to [1, 64]
     * @param lore     zero or more lore lines (each supports {@code &} colour codes)
     * @return a new {@link ItemStack} with the given meta applied
     */
    public ItemStack createItem(Material material, String name, int amount, String... lore) {
        if (material == null) material = Material.BARRIER;
        int clampedAmount = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(material, clampedAmount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorizeComponent(name));
            if (lore != null && lore.length > 0) {
                List<Component> loreComponents = Arrays.stream(lore)
                        .map(this::colorizeComponent)
                        .collect(Collectors.toList());
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Builds a player-head {@link ItemStack} representing the given player.
     *
     * @param playerName the name of the player whose skull texture to use
     * @param name       the display name (supports {@code &} colour codes)
     * @param lore       zero or more lore lines (each supports {@code &} colour codes)
     * @return a {@link Material#PLAYER_HEAD} {@link ItemStack} with skull meta applied,
     *         or a {@link Material#PLAYER_HEAD} without skull texture if the player is
     *         not found
     */
    public ItemStack createSkull(String playerName, String name, String... lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            meta.setOwningPlayer(offlinePlayer);
            meta.displayName(colorizeComponent(name));
            if (lore != null && lore.length > 0) {
                List<Component> loreComponents = Arrays.stream(lore)
                        .map(this::colorizeComponent)
                        .collect(Collectors.toList());
                meta.lore(loreComponents);
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }

    // -------------------------------------------------------------------------
    // InventoryHolder implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @return the {@link Inventory} backing this GUI
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------
    // Protected helpers
    // -------------------------------------------------------------------------

    /**
     * Called when the viewer closes this GUI. Subclasses may override to handle cleanup.
     *
     * @param player the player who closed the GUI
     */
    public void onClose(Player player) {
        // Default no-op, can be overridden by subclasses
    }

    /**
     * Translates colour codes in the given string using {@link com.premiumshop.plugin.util.ColorUtil}.
     *
     * @param text the raw text
     * @return the colour-translated string
     */
    protected String colorize(String text) {
        return com.premiumshop.plugin.util.ColorUtil.colorize(text);
    }

    /**
     * Converts a colour-code string to an Adventure {@link Component}.
     *
     * @param text the raw text
     * @return a coloured {@link Component}
     */
    protected Component colorizeComponent(String text) {
        return com.premiumshop.plugin.util.ColorUtil.parse(text);
    }

    /**
     * Returns a default light-grey stained glass pane named {@code " "} (a
     * single space) for use as a GUI border or filler slot.
     *
     * @return an {@link ItemStack} of {@link Material#LIGHT_GRAY_STAINED_GLASS_PANE}
     */
    protected ItemStack buildGlassPane() {
        return buildGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Returns a stained (or plain) glass pane of the specified material named
     * {@code " "} (a single space).
     *
     * @param material the pane material to use
     * @return an {@link ItemStack} of the given material with a space display name
     */
    protected ItemStack buildGlassPane(Material material) {
        ItemStack pane = new ItemStack(material, 1);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.space());
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Clears all slots, slot-action registrations, and the item cache.
     * Called internally by {@link #open()} and {@link #refresh()}.
     */
    private void clearAll() {
        inventory.clear();
        slotActions.clear();
        cachedItems.clear();
    }

    // -------------------------------------------------------------------------
    // Border / frame helpers
    // -------------------------------------------------------------------------

    /**
     * Fills the outermost ring of a 54-slot (6-row) inventory with the
     * default glass-pane filler.
     */
    protected void fillBorder() {
        fillBorder(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Fills the outermost ring of a 54-slot (6-row) inventory with a
     * glass pane of the specified material.
     *
     * @param material the pane material to use for the border
     */
    protected void fillBorder(Material material) {
        ItemStack pane = buildGlassPane(material);
        // Top row (0-8) + bottom row (45-53)
        for (int i = 0; i <= 8; i++) setItem(i, pane);
        for (int i = 45; i <= 53; i++) setItem(i, pane);
        // Left and right columns (rows 1-4)
        for (int row = 1; row <= 4; row++) {
            setItem(row * 9, pane);
            setItem(row * 9 + 8, pane);
        }
    }
}
