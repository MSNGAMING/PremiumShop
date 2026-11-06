package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import com.premiumshop.plugin.shop.ShopCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI that displays all {@link ShopCategory categories} of a single {@link Shop}.
 *
 * <p>Each category is shown as a clickable item.  If the viewing player lacks
 * the required permission the item is rendered in a "locked" style and a
 * no-permission message is sent instead of opening the item list.  A back
 * button at slot 49 returns to the {@link ShopMainGui}.</p>
 *
 * <p>Layout (54 slots, 6 rows):</p>
 * <pre>
 * Row 0 (0–8):   glass border
 * Rows 1–4:      category icons (cols 1-7, up to 28 categories)
 * Row 5 (45–53): glass border | back-button at slot 49
 * </pre>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
public class ShopCategoryGui extends AbstractGui {

    /** The shop whose categories are displayed. */
    private final Shop shop;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code ShopCategoryGui}.
     *
     * @param plugin the owning plugin instance
     * @param viewer the player viewing the GUI
     * @param shop   the shop whose categories should be displayed; must not be {@code null}
     */
    public ShopCategoryGui(PremiumShopPlugin plugin, Player viewer, Shop shop) {
        super(plugin, viewer, "&6&l" + (shop != null ? shop.getDisplayName() : "Shop"), 54);
        if (shop == null) throw new IllegalArgumentException("shop must not be null");
        this.shop = shop;
    }

    // -------------------------------------------------------------------------
    // AbstractGui implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Lays out each category icon, a back button, and glass-pane borders.</p>
     */
    @Override
    protected void populateInventory() {
        fillBorder();
        placeBackButton();

        List<ShopCategory> categories = shop.getCategories();
        if (categories == null || categories.isEmpty()) {
            setItem(26, createItem(
                    Material.BARRIER,
                    "&c&lNo Categories",
                    "&7This shop has no categories configured."
            ));
            return;
        }

        int[] contentSlots = buildContentSlots();
        int maxVisible = Math.min(categories.size(), contentSlots.length);
        int startOffset = (contentSlots.length - maxVisible) / 2;

        for (int i = 0; i < maxVisible; i++) {
            final ShopCategory category = categories.get(i);

            // Determine which slot to use: use the category's configured slot if valid,
            // otherwise fall back to the auto-layout position.
            int configuredSlot = category.getSlot();
            int slot;
            if (configuredSlot >= 0 && configuredSlot < 54
                    && !isReservedSlot(configuredSlot)) {
                slot = configuredSlot;
            } else {
                slot = contentSlots[startOffset + i];
            }

            boolean hasAccess = hasAccessToCategory(category);

            int itemCount = category.getItems() != null ? category.getItems().size() : 0;
            String accessLine = hasAccess
                    ? "&a✔ Access granted"
                    : "&c✘ Requires: &7" + (category.getPermission() != null
                        ? category.getPermission()
                        : "unknown permission");
            String countLine = "&7" + itemCount + " item" + (itemCount == 1 ? "" : "s");

            Material iconMaterial = category.getIconMaterial() != null ? category.getIconMaterial() : Material.BOOK;
            // If locked, show a dyed glass to indicate inaccessibility
            if (!hasAccess) {
                iconMaterial = Material.RED_STAINED_GLASS_PANE;
            }

            ItemStack icon = createItem(
                    iconMaterial,
                    (hasAccess ? "&e&l" : "&c&l") + category.getDisplayName(),
                    countLine,
                    "&8─────────────────",
                    accessLine,
                    hasAccess ? "&eClick to browse!" : "&cYou cannot access this category."
            );

            final boolean accessFinal = hasAccess;
            final String noPermMsg = shop.getNoPermissionMessage() != null
                    ? shop.getNoPermissionMessage()
                    : "&cYou do not have permission to access this category.";

            setItem(slot, icon, event -> {
                if (!accessFinal) {
                    viewer.sendMessage(colorizeComponent(noPermMsg));
                    return;
                }
                ShopItemGui itemGui = new ShopItemGui(plugin, viewer, shop, category);
                itemGui.open();
            });
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Places a barrier "Back" button at slot 49 that returns the viewer to the
     * main shop-selection GUI.
     */
    private void placeBackButton() {
        ItemStack back = createItem(
                Material.BARRIER,
                "&c&l← Back",
                "&7Return to shop list"
        );
        // Determine which shops list to pass back (fetched via plugin's ShopManager)
        setItem(49, back, event -> {
            List<Shop> allShops = plugin.getShopManager().getShops();
            ShopMainGui mainGui = new ShopMainGui(plugin, viewer, allShops);
            mainGui.open();
        });
    }

    /**
     * Builds the array of content slot indices (rows 1–4, columns 1–7).
     *
     * @return an array of 28 slot indices
     */
    private int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Returns {@code true} if the given slot belongs to the border or the back-
     * button position and should not be used for category icons.
     *
     * @param slot the slot to check
     * @return {@code true} if the slot is reserved
     */
    private boolean isReservedSlot(int slot) {
        // Top row
        if (slot <= 8) return true;
        // Bottom row
        if (slot >= 45) return true;
        // Left/right columns (rows 1-4)
        if (slot % 9 == 0 || slot % 9 == 8) return true;
        return false;
    }

    /**
     * Checks whether the viewing player has access to the given category,
     * taking into account both the category-level and shop-level permissions.
     *
     * @param category the category to check
     * @return {@code true} if the player may browse the category
     */
    private boolean hasAccessToCategory(ShopCategory category) {
        // Shop-level permission check
        if (shop.getPermission() != null && !shop.getPermission().isEmpty()) {
            if (!viewer.hasPermission(shop.getPermission())) return false;
        }
        // Category-level permission check
        if (category.getPermission() != null && !category.getPermission().isEmpty()) {
            return viewer.hasPermission(category.getPermission());
        }
        return true;
    }

    /**
     * Resolves a material name string to a {@link Material}, falling back to
     * {@code fallback} when the name is invalid or {@code null}.
     *
     * @param materialName the material name from config
     * @param fallback     the fallback material
     * @return a valid, non-{@code AIR} {@link Material}
     */
    private Material resolveMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isEmpty()) return fallback;
        try {
            Material m = Material.valueOf(materialName.toUpperCase());
            return m == Material.AIR ? fallback : m;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
