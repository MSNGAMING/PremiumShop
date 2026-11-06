package com.premiumshop.plugin.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a top-level shop that groups one or more {@link ShopCategory categories}.
 *
 * <p>A shop is the root entity of the PremiumShop data model:
 * <pre>
 *   Shop
 *   └── ShopCategory (1..n)
 *       └── ShopItem (1..n)
 * </pre>
 *
 * <p>All categories are stored in both a {@link List} (for ordered iteration)
 * and a {@link Map} (for O(1) look-up by ID).
 *
 * @author PremiumShop Development Team
 */
public final class Shop {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Unique identifier for this shop as declared in {@code shops.yml}. */
    private final String id;

    /** Human-readable display name (supports colour codes). */
    private String displayName;

    /** Bukkit permission required to open this shop. */
    private String permission;

    /** Message shown when permission check fails. */
    private String noPermissionMessage;

    /**
     * Ordered list of categories.  Order controls the navigation-menu
     * appearance inside the GUI.
     */
    private final List<ShopCategory> categories;

    /** Fast look-up: category ID → {@link ShopCategory}. */
    private final Map<String, ShopCategory> categoryMap;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs a {@code Shop} with no categories.
     * Use {@link #addCategory(ShopCategory)} or {@link #fromConfig} to populate it.
     *
     * @param id          unique shop identifier; must not be {@code null}
     * @param displayName human-readable name; {@code null} falls back to {@code id}
     */
    public Shop(final String id, final String displayName) {
        Objects.requireNonNull(id, "Shop id must not be null");
        this.id          = id;
        this.displayName = displayName != null ? displayName : id;
        this.categories  = new ArrayList<>();
        this.categoryMap = new HashMap<>();
    }

    /**
     * Constructs a {@code Shop} pre-populated with the given categories.
     *
     * @param id          unique shop identifier; must not be {@code null}
     * @param displayName human-readable name
     * @param categories  initial list of categories; {@code null} is treated as empty
     */
    public Shop(final String id, final String displayName, final List<ShopCategory> categories) {
        this(id, displayName);
        if (categories != null) {
            for (ShopCategory cat : categories) {
                addCategory(cat);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** @return the unique shop identifier */
    public String getId() { return id; }

    /** @return the human-readable display name */
    public String getDisplayName() { return displayName; }

    public org.bukkit.Material getIconMaterial() {
        return org.bukkit.Material.CHEST;
    }

    public String getPermission() { return permission; }

    public void setPermission(String permission) { this.permission = permission; }

    public String getNoPermissionMessage() { return noPermissionMessage; }

    public void setNoPermissionMessage(String msg) { this.noPermissionMessage = msg; }

    /**
     * Returns an unmodifiable ordered view of the categories.
     *
     * @return read-only list of {@link ShopCategory}
     */
    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    /**
     * Returns an unmodifiable view of the category ID-to-instance map.
     *
     * @return read-only map of category ID → {@link ShopCategory}
     */
    public Map<String, ShopCategory> getCategoryMap() {
        return Collections.unmodifiableMap(categoryMap);
    }

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------

    /**
     * Updates the display name.
     *
     * @param displayName new display name; {@code null} resets to the shop ID
     */
    public void setDisplayName(final String displayName) {
        this.displayName = displayName != null ? displayName : id;
    }

    // -----------------------------------------------------------------------
    // Category management
    // -----------------------------------------------------------------------

    /**
     * Adds (or replaces) a {@link ShopCategory} in this shop.
     *
     * <p>If a category with the same ID already exists it is replaced in-place
     * within the ordered list so that the GUI layout is preserved.
     *
     * @param category the category to add; must not be {@code null}
     */
    public void addCategory(final ShopCategory category) {
        Objects.requireNonNull(category, "ShopCategory must not be null");
        // Replace in list if ID already present
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId().equals(category.getId())) {
                categories.set(i, category);
                categoryMap.put(category.getId(), category);
                return;
            }
        }
        categories.add(category);
        categoryMap.put(category.getId(), category);
    }

    /**
     * Removes the category with the given ID from this shop.
     *
     * <p>If the ID is not found this method is a no-op.
     *
     * @param categoryId the ID of the category to remove
     */
    public void removeCategory(final String categoryId) {
        if (categoryId == null) return;
        categories.removeIf(cat -> cat.getId().equals(categoryId));
        categoryMap.remove(categoryId);
    }

    /**
     * Retrieves the {@link ShopCategory} with the specified ID.
     *
     * @param categoryId the category ID to look up
     * @return the category, or {@code null} if not present
     */
    public ShopCategory getCategory(final String categoryId) {
        if (categoryId == null) return null;
        return categoryMap.get(categoryId);
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Deserialises a {@code Shop} from a {@link ConfigurationSection} in
     * {@code shops.yml}.
     *
     * <p>The {@code itemFactory} parameter is accepted for future use
     * (e.g., constructing complex ItemStacks with PDC) but is not strictly
     * required for the base deserialisation path.
     *
     * @param shopId      the shop ID taken from the YAML section key
     * @param section     the YAML section containing the shop data
     * @param itemFactory Bukkit's {@link ItemFactory} (may be {@code null})
     * @return the constructed {@code Shop} instance
     */
    public static Shop fromConfig(
            final String shopId,
            final ConfigurationSection section,
            final ItemFactory itemFactory) {

        Objects.requireNonNull(shopId,  "shopId must not be null");
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        String displayName = section.getString("display-name", shopId);
        Shop shop = new Shop(shopId, displayName);
        shop.setPermission(section.getString("permission", null));
        shop.setNoPermissionMessage(section.getString("no-permission-message", null));

        ConfigurationSection catsSection = section.getConfigurationSection("categories");
        if (catsSection != null) {
            for (String catId : catsSection.getKeys(false)) {
                ConfigurationSection catSection = catsSection.getConfigurationSection(catId);
                if (catSection != null) {
                    try {
                        ShopCategory category = ShopCategory.fromConfigSection(catId, catSection);
                        shop.addCategory(category);
                    } catch (Exception ex) {
                        System.err.println("[PremiumShop] Failed to load category '" + catId
                                + "' for shop '" + shopId + "': " + ex.getMessage());
                    }
                }
            }
        }

        return shop;
    }

    // -----------------------------------------------------------------------
    // Serialisation
    // -----------------------------------------------------------------------

    /**
     * Serialises this shop into the supplied {@link ConfigurationSection}.
     *
     * @param section the YAML section to write into; must not be {@code null}
     */
    public void toConfig(final ConfigurationSection section) {
        Objects.requireNonNull(section, "ConfigurationSection must not be null");

        section.set("display-name", displayName);
        if (permission != null) section.set("permission", permission);
        if (noPermissionMessage != null) section.set("no-permission-message", noPermissionMessage);

        ConfigurationSection catsSection = section.createSection("categories");
        for (ShopCategory category : categories) {
            ConfigurationSection catSection = catsSection.createSection(category.getId());
            category.toConfigSection(catSection);
        }
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "Shop{id='" + id + "', categories=" + categories.size() + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Shop other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
