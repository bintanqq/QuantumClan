package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Central inventory click handler for all QuantumClan GUIs.
 *
 * Responsibilities:
 *  1. Block ALL inventory interactions (click, drag, shift-click) inside any
 *     QuantumClan GUI holder — prevents item duplication.
 *  2. Route valid left/right clicks to the appropriate GUI handler.
 *
 * Anti-dupe rules enforced here:
 *  - isCancelled(true) is set immediately for all events in QC GUIs.
 *  - Shift-click, double-click, middle-click, number-key are all blocked.
 *  - InventoryDragEvent inside any QC GUI is fully cancelled.
 *  - Per-player processing flag (in each GUI class) prevents double-click spam.
 */
public class InventoryClickListener implements Listener {

    private final QuantumClan plugin;

    public InventoryClickListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Click handler ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory  = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Not a QuantumClan GUI — ignore
        if (!isQCHolder(holder)) return;

        // Cancel everything by default — prevent any item movement
        event.setCancelled(true);

        // Block dangerous click types regardless of slot
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.MIDDLE
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click == ClickType.NUMBER_KEY
                || click == ClickType.CREATIVE
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.UNKNOWN) {
            return;
        }

        // Only process clicks in the top inventory (the GUI itself)
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(inventory)) return;

        int slot = event.getRawSlot();
        if (slot < 0) return;

        // Route to the correct GUI handler
        routeClick(holder, player, slot, click);
    }

    // ── Drag handler ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (isQCHolder(holder)) {
            event.setCancelled(true);
        }
    }

    // ── Routing ───────────────────────────────────────────────

    private void routeClick(InventoryHolder holder, Player player, int slot, ClickType click) {
        switch (holder) {
            case ClanInfoGUI gui        -> gui.handleClick(player, slot, click);
            case ClanShopGUI gui        -> gui.handleClick(player, slot, click);
            case ShopConfirmGUI gui     -> gui.handleClick(player, slot, click);
            case ClanHomeGUI gui        -> gui.handleClick(player, slot, click);
            case BountyBoardGUI gui     -> gui.handleClick(player, slot, click);
            case ClanTopGUI gui         -> gui.handleClick(player, slot, click);
            case UpgradeGUI gui         -> gui.handleClick(player, slot, click);
            case WarGUI gui             -> gui.handleClick(player, slot, click);
            case ContributionShopGUI gui -> gui.handleClick(player, slot, click);
            default -> { /* Unrecognised QC GUI — already cancelled, do nothing */ }
        }
    }

    // ── Helper ────────────────────────────────────────────────

    /**
     * Returns true if the given holder is a QuantumClan-managed GUI.
     */
    private boolean isQCHolder(InventoryHolder holder) {
        return holder instanceof ClanInfoGUI
                || holder instanceof ClanShopGUI
                || holder instanceof ShopConfirmGUI
                || holder instanceof ClanHomeGUI
                || holder instanceof BountyBoardGUI
                || holder instanceof ClanTopGUI
                || holder instanceof UpgradeGUI
                || holder instanceof WarGUI
                || holder instanceof ContributionShopGUI;
    }
}