package me.bintanq.quantumclan.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base interface for all QuantumClan GUIs to simplify click routing.
 */
public interface BaseGUI extends InventoryHolder {
    /**
     * Handle a click event in this GUI.
     * @param player The player who clicked.
     * @param slot   The raw slot index.
     * @param click  The click type.
     */
    void handleClick(Player player, int slot, ClickType click);
}
