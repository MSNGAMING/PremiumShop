package com.premiumshop.plugin.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a named category within a {@link Shop}.
 *
 * <p>Each category has its own GUI page, an icon displayed in the shop
 * navigation menu, an ordered list of {@link ShopItem}s, and an optional
 * permission node that gates player access.
 *
 * <p>The {@code slotToItemId} map allows item positions to be pinned to
 * specific inventory slots, so the layout can be fully customised in the
 * YAML configuration.  Any inventory slot listed in {@code decoratorSlots}
 * is treated as visual filler and does not interact with item logic.
 *
 * @author PremiumShop Development Team
 */
public final class ShopCategory {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Unique identifier for this category within its parent shop. */
    private String id;

    /** Human-readable display name (supports colour codes). */
    private String displayName;

    /** Icon shown in the navigation menu for this category. */
    private ItemStack icon;

    /** Ordered list of items belonging to this category. */
    private final List<ShopItem> items;

    /**
     * Maps GUI inventory slot indices to item IDs.
     * Entries are added/removed automatically via {@link #addItem(ShopItem)}
     * and {@link #removeItem(String)}.
     */
    private final Map<Integer, String> slotToItemId;

    /**
     * Inventory slot indices that are reserved for decorative glass panes or
     * other filler items and should never be mapped to real shop items.
     */
    private int[] decoratorSlots;

    /**
     * Bukkit permission node required to open this category.
     * {@code null} means no restriction – everyone may access the category.
     */
    private String permission;

    /** Configured GUI slot index in parent menu, or -1 for default. */
    private int slot = -1;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Full constructor.
     *
     * @param id             unique category ID; must not be {@code null}
     * @param displayName    display name shown in GUI (colour codes supported)
     * @param icon           navigation-menu icon {@link ItemStack}; must not be {@code null}
     * @param items          initial list of items; {@code null} is treated as empty
     * @param slotToItemId   initial slot-to-ID mapping; {@code null} is treated as empty
     * @param decoratorSlots slot indices used as decorators; {@code null} is treated as empty array
     * @param permission     Bukkit permission node, or {@code null} for unrestricted access
     */
    public ShopCategory(
            final String             id,
            final String             displayName,
            final ItemStack          icon,
            final List<ShopItem>     items,
            final Map<Integer, String> slotToItemId,
            final int[]              decoratorSlots,
            final String             permission) {

        Objects.requireNonNull(id,   "ShopCategory id must not be null");
        Objects.requireNonNull(icon, "ShopCategory icon must not be null");

        this.id             = id;
        this.displayName    = displayName != null ? displayName : id;
        this.icon           = icon.clone();
        this.items          = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.slotToItemId   = slotToItemId != null ? new HashMap<>(slotToItemId) : new HashMap<>();
        this.decoratorSlots = decoratorSlots != null ? Arrays.copyOf(decoratorSlots, decoratorSlots.length) : new int[0];
        this.permission     = permission;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** @return the unique category identifier */
    public String getId() { return id; }

    /** @return the display name used in the GUI */
    public String getDisplayName() { return displayName; }

    public int getSlot() { return slot; }

    public void setSlot(int slot) { this.slot = slot; }

    public org.bukkit.Material getIconMaterial() {
        return icon != null ? icon.getType() : org.bukkit.Material.CHEST;
    }

    /**
     * Returns a defensive copy of the category icon.
     *
     * @return a clone of the icon {@link ItemStack}
     */
    public ItemStack getIcon() { return icon.clone(); }

    /**
     * Returns an unmodifiable view of the item list.
     *
     * @return read-only list of {@link ShopItem}s
     */
    public List<ShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Returns an unmodifiable view of the slot-to-item-ID mapping.
     *
     * @return read-only map of GUI slot → item ID
     */
    public Map<Integer, String> getSlotToItemId() {
        return Collections.unmodifiableMap(slotToItemId);
    }

    /**
     * Returns a defensive copy of the decorator slot array.
     *
     * @return array of decorator slot indices
     */
    public int[] getDecoratorSlots() {
        return Arrays.copyOf(decoratorSlots, decoratorSlots.length);
    }

    /**
     * Returns the Bukkit permission node for this category.
     *
     * @return the permission string, or {@code null} if the category is unrestricted
     */
    public String getPermission() { return permission; }

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------

    /**
     * Sets the category's unique identifier.
     *
     * @param id new identifier; must not be {@code null}
     */
    public void setId(final String id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Sets the display name shown in the GUI.
     *
     * @param displayName new display name
     */
    public void setDisplayName(final String displayName) {
        this.displayName = displayName != null ? displayName : id;
    }

    /**
     * Replaces the navigation-menu icon.
     *
     * @param icon new icon; must not be {@code null}
     */
    public void setIcon(final ItemStack icon) {
        Objects.requireNonNull(icon, "icon must not be null");
        this.icon = icon.clone();
    }

    /**
     * Replaces the decorator-slot array.
     *
     * @param decoratorSlots new decorator slot indices; {@code null} is treated as empty
     */
    public void setDecoratorSlots(final int[] decoratorSlots) {
        this.decoratorSlots = decoratorSlots != null
                ? Arrays.copyOf(decoratorSlots, decoratorSlots.length)
                : new int[0];
    }

    /**
     * Sets the Bukkit permission node.
     *
     * @param permission permission string, or {@code null} to remove the restriction
     */
    public void setPermission(final String permission) {
        this.permission = permission;
    }

    // -----------------------------------------------------------------------
    // Item management
    // -----------------------------------------------------------------------

    /**
     * Adds a {@link ShopItem} to this category.
     *
     * <p>If an item with the same ID already exists it is replaced.  The item
     * is also auto-mapped to the next available non-decorator slot in the GUI
     * layout.
     *
     * @param item the item to add; must not be {@code null}
     */
    public void addItem(final ShopItem item) {
        Objects.requireNonNull(item, "ShopItem must not be null");

        // Remove existing entry with the same ID (replacement semantics)
        items.removeIf(existing -> existing.getId().equals(item.getId()));
        slotToItemId.values().removeIf(existingId -> existingId.equals(item.getId()));

        items.add(item);

        // Auto-assign the item to the next free slot that is not a decorator
        java.util.Set<Integer> decoratorSet = new java.util.HashSet<>();
        for (int s : decoratorSlots) {
            decoratorSet.add(s);
        }
        // Determine next available slot (0-based, skip used slots and decorators)
        java.util.Set<Integer> usedSlots = slotToItemId.keySet();
        int nextSlot = 0;
        while (usedSlots.contains(nextSlot) || decoratorSet.contains(nextSlot)) {
            nextSlot++;
        }
        slotToItemId.put(nextSlot, item.getId());
    }

    /**
     * Removes the item with the given ID from this category.
     *
     * <p>The corresponding slot mapping is also removed.
     *
     * @param itemId the ID of the item to remove
     */
    public void removeItem(final String itemId) {
        if (itemId == null) return;
        items.removeIf(i -> i.getId().equals(itemId));
        slotToItemId.values().remove(itemId);
    }

    /**
     * Retrieves the {@link ShopItem} with the given ID.
     *
     * @param itemId the item ID to look up
     * @return the matching item, or {@code null} if not found
     */
    public ShopItem getItem(final String itemId) {
        if (itemId == null) return null;
        for (ShopItem item : items) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Returns the {@link ShopItem} mapped to the specified GUI inventory slot,
     * or {@code null} if no item occupies that slot.
     *
     * @param slot the inventory slot index (0-based)
     * @return the item at the slot, or {@code null}
     */
    public ShopItem getItemAtSlot(final int slot) {
        String itemId = slotToItemId.get(slot);
        if (itemId == null) return null;
        return getItem(itemId);
    }

    // -----------------------------------------------------------------------
    // Permission check
    // -----------------------------------------------------------------------

    /**
     * Checks whether a player may access this category.
     *
     * @param player the player to check; must not be {@code null}
     * @return {@code true} if the player has access
     */
    public boolean isAccessible(final Player player) {
        Objects.requireNonNull(player, "Player must not be null");
        if (permission == null || permission.isBlank()) {
            return true;   // unrestricted
        }
        return player.hasPermission(permission);
    }

    // -----------------------------------------------------------------------
    // Serialisation
    // -----------------------------------------------------------------------

    /**
     * Serialises this category into the supplied {@link ConfigurationSection}.
     *
     * @param section the YAML section to write into; must not be {@code null}
     */
    public void toConfigSection(final ConfigurationSection section) {
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        section.set("display-name",    displayName);
        section.set("slot",            slot);
        section.set("icon",            icon != null ? icon.getType().name() : "CHEST");
        section.set("permission",      permission);

        // Decorator slots stored as a list for YAML friendliness
        List<Integer> decoratorList = new ArrayList<>();
        for (int s : decoratorSlots) {
            decoratorList.add(s);
        }
        section.set("decorator-slots", decoratorList);

        // Slot-to-item-ID map
        ConfigurationSection slotSection = section.createSection("slot-map");
        for (Map.Entry<Integer, String> entry : slotToItemId.entrySet()) {
            slotSection.set(String.valueOf(entry.getKey()), entry.getValue());
        }

        // Items
        ConfigurationSection itemsSection = section.createSection("items");
        for (ShopItem item : items) {
            ConfigurationSection itemSection = itemsSection.createSection(item.getId());
            item.toConfigSection(itemSection);
        }
    }

    /**
     * Deserialises a {@link ShopCategory} from a {@link ConfigurationSection}.
     *
     * @param id      the category ID (taken from the parent section key)
     * @param section the YAML section to read from; must not be {@code null}
     * @return the constructed {@link ShopCategory}
     */
    public static ShopCategory fromConfigSection(final String id, final ConfigurationSection section) {
        Objects.requireNonNull(id,      "Category id must not be null");
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        String displayName = section.getString("display-name", id);

        // Icon
        ItemStack icon = null;
        if (section.isItemStack("icon")) {
            icon = section.getItemStack("icon");
        } else if (section.isConfigurationSection("icon")) {
            ConfigurationSection iconSec = section.getConfigurationSection("icon");
            if (iconSec != null) {
                try {
                    icon = ItemStack.deserialize(iconSec.getValues(false));
                } catch (Exception ignored) {}
            }
        } else {
            Object rawIconObj = section.get("icon");
            if (rawIconObj instanceof ItemStack is) {
                icon = is;
            } else if (rawIconObj instanceof java.util.Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> castMap = (java.util.Map<String, Object>) map;
                try {
                    icon = ItemStack.deserialize(castMap);
                } catch (Exception ignored) {}
            } else if (rawIconObj instanceof String matName) {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName);
                if (mat != null) icon = new ItemStack(mat);
            }
        }
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(org.bukkit.Material.CHEST);
        }

        String permission = section.getString("permission", null);
        if (permission != null && permission.isBlank()) {
            permission = null;
        }

        // Decorator slots
        List<Integer> decoratorList = section.getIntegerList("decorator-slots");
        int[] decoratorSlots = new int[decoratorList.size()];
        for (int i = 0; i < decoratorList.size(); i++) {
            decoratorSlots[i] = decoratorList.get(i);
        }

        // Slot-to-item-ID map
        Map<Integer, String> slotToItemId = new HashMap<>();
        ConfigurationSection slotSection = section.getConfigurationSection("slot-map");
        if (slotSection != null) {
            for (String key : slotSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    slotToItemId.put(slot, slotSection.getString(key));
                } catch (NumberFormatException ignored) {
                    // malformed YAML key – skip silently
                }
            }
        }

        // Items
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection != null) {
                    try {
                        items.add(ShopItem.fromConfigSection(itemId, itemSection));
                    } catch (Exception ex) {
                        // Log and continue loading other items
                        System.err.println("[PremiumShop] Failed to load item '" + itemId
                                + "' in category '" + id + "': " + ex.getMessage());
                    }
                }
            }
        }

        ShopCategory category = new ShopCategory(id, displayName, icon, items, slotToItemId, decoratorSlots, permission);
        category.setSlot(section.getInt("slot", -1));
        return category;
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "ShopCategory{id='" + id + "', items=" + items.size() + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopCategory other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
