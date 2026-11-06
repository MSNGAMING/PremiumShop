package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import com.premiumshop.plugin.shop.Shop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The main shop-selection GUI displayed to a player who opens the plugin.
 *
 * <p>All registered {@link Shop}s are shown as clickable icons inside the
 * inventory.  Clicking a shop icon immediately opens the
 * {@link ShopCategoryGui} for that shop.  When fewer than 28 shops are
 * present the icons are centred in the middle rows; borders are filled with
 * light-grey glass panes.</p>
 *
 * <p>Layout (54 slots, 6 rows):</p>
 * <pre>
 * Row 0 (0–8):   glass border
 * Row 1 (9–17):  shops (cols 1-7)
 * Row 2 (18-26): shops (cols 1-7)
 * Row 3 (27-35): shops (cols 1-7)
 * Row 4 (36-44): shops (cols 1-7)
 * Row 5 (45-53): glass border
 * </pre>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
public class ShopMainGui extends AbstractGui {

    /** All shops to display in this GUI. */
    private final List<Shop> shops;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code ShopMainGui}.
     *
     * @param plugin the owning plugin instance
     * @param viewer the player viewing the GUI
     * @param shops  the list of shops to display; must not be {@code null}
     */
    public ShopMainGui(PremiumShopPlugin plugin, Player viewer, List<Shop> shops) {
        super(plugin, viewer, "&6&lPremium Shop", 54);
        this.shops = shops != null ? shops : new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // AbstractGui implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Places each shop as a clickable item at a calculated slot that keeps
     * small lists centred.  Border slots are filled with glass panes.</p>
     */
    @Override
    protected void populateInventory() {
        // Fill the border first
        fillBorder();

        if (shops.isEmpty()) {
            // Show an informational barrier in the centre slot
            setItem(26, createItem(
                    Material.BARRIER,
                    "&c&lNo Shops Available",
                    "&7Ask an administrator to configure shops."
            ));
            return;
        }

        // Available content slots: rows 1-4, columns 1-7 → 28 slots
        int[] contentSlots = buildContentSlots();
        int maxVisible = Math.min(shops.size(), contentSlots.length);

        // If fewer shops than available slots, centre them
        int startOffset = (contentSlots.length - maxVisible) / 2;

        for (int i = 0; i < maxVisible; i++) {
            final Shop shop = shops.get(i);
            int slot = contentSlots[startOffset + i];

            String categoryInfo = shop.getCategories() != null
                    ? shop.getCategories().size() + " categor" + (shop.getCategories().size() == 1 ? "y" : "ies")
                    : "No categories";

            String permInfo = shop.getPermission() != null && !shop.getPermission().isEmpty()
                    ? (viewer.hasPermission(shop.getPermission()) ? "&a✔ Access granted" : "&c✘ No access")
                    : "&7Open to all";

            ItemStack icon = createItem(
                    shop.getIconMaterial() != null ? shop.getIconMaterial() : Material.CHEST,
                    "&6&l" + shop.getDisplayName(),
                    "&7" + categoryInfo,
                    "&8─────────────────",
                    permInfo,
                    "&eClick to browse!"
            );

            setItem(slot, icon, event -> {
                // Open the category selection GUI for this shop
                ShopCategoryGui categoryGui = new ShopCategoryGui(plugin, viewer, shop);
                categoryGui.open();
            });
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the array of content slot indices (rows 1-4, columns 1-7).
     *
     * @return array of 28 slot indices
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
     * Resolves the material name string from a shop's icon config.
     *
     * @param materialName the string from config (may be {@code null})
     * @param fallback     the fallback material if the name is invalid
     * @return a valid {@link Material}
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
