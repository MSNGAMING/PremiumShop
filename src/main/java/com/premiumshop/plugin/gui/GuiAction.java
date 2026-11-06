package com.premiumshop.plugin.gui;

import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Functional interface representing an action executed when a player
 * clicks a specific slot inside a plugin-managed {@link AbstractGui}.
 *
 * <p>Implementations are registered per-slot via
 * {@link AbstractGui#setItem(int, org.bukkit.inventory.ItemStack, GuiAction)}
 * and are invoked automatically by {@link AbstractGui#handleClick(InventoryClickEvent)}.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * setItem(slot, icon, event -> {
 *     event.getWhoClicked().sendMessage("You clicked slot " + slot + "!");
 * });
 * }</pre>
 *
 * @author PremiumShop
 * @since 1.0.0
 */
@FunctionalInterface
public interface GuiAction {

    /**
     * Executes the action associated with the clicked slot.
     *
     * <p>The event is <em>always</em> cancelled before this method is called
     * (handled by {@link AbstractGui#handleClick(InventoryClickEvent)}),
     * so implementations do not need to cancel it themselves.</p>
     *
     * @param event the {@link InventoryClickEvent} that triggered this action;
     *              never {@code null}
     */
    void execute(InventoryClickEvent event);
}
