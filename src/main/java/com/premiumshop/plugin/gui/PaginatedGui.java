package com.premiumshop.plugin.gui;

import com.premiumshop.plugin.PremiumShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract extension of {@link AbstractGui} that adds multi-page support.
 *
 * <p>Subclasses provide the full list of page items via {@link #getPageItems()}
 * and can customise which inventory slots display page content, as well as the
 * positions of the previous/next navigation buttons and the page-indicator item.</p>
 *
 * <p>Default layout (54-slot inventory):</p>
 * <ul>
 *   <li>Content slots: 0–44 (rows 1–5)</li>
 *   <li>Previous-page button: slot 45</li>
 *   <li>Page-info indicator: slot 49</li>
 *   <li>Next-page button: slot 53</li>
 * </ul>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
public abstract class PaginatedGui extends AbstractGui {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The full list of {@code (ItemStack, GuiAction)} pairs that are distributed
     * across pages.  Populated by {@link #getPageItems()} and cached for the
     * lifetime of a single {@link #open()} / {@link #refresh()} cycle.
     */
    protected List<Map.Entry<ItemStack, GuiAction>> pageItems = new ArrayList<>();

    /** The zero-based index of the page currently displayed. */
    protected int currentPage = 0;

    /**
     * The inventory slot indices that are reserved for displaying page content.
     * Defaults to slots 0–44 (the first five rows of a 54-slot inventory).
     */
    protected int[] contentSlots;

    /** Inventory slot index for the "previous page" button. Default: 45. */
    protected int prevButtonSlot = 45;

    /** Inventory slot index for the "next page" button. Default: 53. */
    protected int nextButtonSlot = 53;

    /** Inventory slot index for the page-indicator item. Default: 49. */
    protected int pageInfoSlot = 49;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a new {@code PaginatedGui}.
     *
     * @param plugin the owning plugin instance
     * @param viewer the player viewing this GUI
     * @param title  the inventory title (supports {@code &} colour codes)
     * @param size   the inventory size (must be a positive multiple of 9, max 54)
     */
    protected PaginatedGui(PremiumShopPlugin plugin, Player viewer, String title, int size) {
        super(plugin, viewer, title, size);
        // Default content slots: rows 1-5 of a 54-slot inventory
        contentSlots = new int[45];
        for (int i = 0; i < 45; i++) {
            contentSlots[i] = i;
        }
    }

    // -------------------------------------------------------------------------
    // Abstract contract
    // -------------------------------------------------------------------------

    /**
     * Returns the complete (unpaged) list of items and their associated click
     * actions to be distributed across pages.
     *
     * <p>Each entry in the list represents a single slot on a page: the
     * {@link Map.Entry#getKey()} is the display {@link ItemStack} and
     * {@link Map.Entry#getValue()} is the {@link GuiAction} to fire on click
     * (may be {@code null} for display-only items).</p>
     *
     * @return a mutable list of item-action pairs; never {@code null}
     */
    protected abstract List<Map.Entry<ItemStack, GuiAction>> getPageItems();

    // -------------------------------------------------------------------------
    // AbstractGui overrides
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Refreshes the {@link #pageItems} list from {@link #getPageItems()},
     * then renders the current page via {@link #renderPage()}.</p>
     */
    @Override
    protected void populateInventory() {
        pageItems = getPageItems();
        renderPage();
    }

    // -------------------------------------------------------------------------
    // Pagination logic
    // -------------------------------------------------------------------------

    /**
     * Clears all content slots and re-renders the items for {@link #currentPage},
     * then updates the navigation buttons via {@link #setupNavigationButtons()}.
     */
    public void renderPage() {
        // Clear content slots
        for (int slot : contentSlots) {
            inventory.setItem(slot, null);
            slotActions.remove(slot);
            cachedItems.remove(slot);
        }

        int itemsPerPage = contentSlots.length;
        int startIndex = currentPage * itemsPerPage;

        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= pageItems.size()) break;

            Map.Entry<ItemStack, GuiAction> entry = pageItems.get(itemIndex);
            int slot = contentSlots[i];
            setItem(slot, entry.getKey(), entry.getValue());
        }

        setupNavigationButtons();
    }

    /**
     * Places the previous-page, next-page, and page-indicator items in the
     * navigation slots.
     *
     * <ul>
     *   <li>Previous arrow (slot {@link #prevButtonSlot}): shown only when a
     *       previous page exists; grey glass pane otherwise.</li>
     *   <li>Page indicator (slot {@link #pageInfoSlot}): displays the current
     *       and total page count.</li>
     *   <li>Next arrow (slot {@link #nextButtonSlot}): shown only when a next
     *       page exists; grey glass pane otherwise.</li>
     * </ul>
     */
    public void setupNavigationButtons() {
        int totalPages = getTotalPages();

        // ---- Previous button ----
        if (currentPage > 0) {
            ItemStack prevItem = createItem(
                    Material.ARROW,
                    "&e&l« Previous Page",
                    "&7Page " + currentPage + " of " + totalPages
            );
            setItem(prevButtonSlot, prevItem, event -> prevPage());
        } else {
            setItem(prevButtonSlot, buildGlassPane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // ---- Page indicator ----
        ItemStack pageInfo = createItem(
                Material.PAPER,
                "&f&lPage " + (currentPage + 1) + " / " + totalPages,
                "&7Total items: &e" + pageItems.size()
        );
        setItem(pageInfoSlot, pageInfo);

        // ---- Next button ----
        if (currentPage < totalPages - 1) {
            ItemStack nextItem = createItem(
                    Material.ARROW,
                    "&e&lNext Page »",
                    "&7Page " + (currentPage + 2) + " of " + totalPages
            );
            setItem(nextButtonSlot, nextItem, event -> nextPage());
        } else {
            setItem(nextButtonSlot, buildGlassPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    /**
     * Advances to the next page and re-renders, if a next page exists.
     */
    public void nextPage() {
        if (currentPage < getTotalPages() - 1) {
            currentPage++;
            renderPage();
        }
    }

    /**
     * Retreats to the previous page and re-renders, if a previous page exists.
     */
    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            renderPage();
        }
    }

    /**
     * Calculates the total number of pages required to display all
     * {@link #pageItems}, given the number of {@link #contentSlots} per page.
     *
     * @return total page count; always at least {@code 1}
     */
    public int getTotalPages() {
        if (pageItems.isEmpty() || contentSlots.length == 0) return 1;
        return (int) Math.ceil((double) pageItems.size() / contentSlots.length);
    }

    // -------------------------------------------------------------------------
    // Configuration setters
    // -------------------------------------------------------------------------

    /**
     * Overrides the default content slot array.
     *
     * <p>Must be called before {@link #open()} or {@link #refresh()} to take
     * effect on the current render cycle.</p>
     *
     * @param slots the slot indices to use for page content; must not be {@code null}
     */
    public void setContentSlots(int[] slots) {
        if (slots == null) throw new IllegalArgumentException("slots must not be null");
        this.contentSlots = Arrays.copyOf(slots, slots.length);
    }

    /**
     * Overrides the default navigation-button slot positions.
     *
     * @param prev slot index for the "previous page" button
     * @param next slot index for the "next page" button
     * @param info slot index for the page-indicator item
     */
    public void setNavSlots(int prev, int next, int info) {
        this.prevButtonSlot = prev;
        this.nextButtonSlot = next;
        this.pageInfoSlot   = info;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the zero-based index of the currently displayed page.
     *
     * @return current page index
     */
    public int getCurrentPage() {
        return currentPage;
    }
}
