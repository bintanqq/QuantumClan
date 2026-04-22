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
 * FIX: ClanVaultGUI storage slots are now properly NOT cancelled so items
 *      can actually move in/out. Only destructive click types and nav bar
 *      clicks are cancelled.
 */
public class InventoryClickListener implements Listener {

    private final QuantumClan plugin;

    public InventoryClickListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory    = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (!isQCHolder(holder)) return;

        int slot        = event.getRawSlot();
        ClickType click = event.getClick();

        // ── Vault GUI: special handling ──────────────────────
        if (holder instanceof ClanVaultGUI vaultGui) {

            // Always block destructive click types everywhere in the vault
            if (click == ClickType.MIDDLE
                    || click == ClickType.NUMBER_KEY
                    || click == ClickType.CREATIVE
                    || click == ClickType.SWAP_OFFHAND
                    || click == ClickType.UNKNOWN) {
                event.setCancelled(true);
                return;
            }

            // Nav bar area: cancel and delegate
            if (vaultGui.isNavSlot(slot)) {
                event.setCancelled(true);
                vaultGui.handleClick(player, slot, click);
                return;
            }

            // Admin inspect mode: block everything in storage
            if (plugin.getClanVaultManager().isAdminInspector(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // Storage area — DO NOT cancel; let Bukkit handle item movement.
            // vaultGui.handleClick will check permissions and cancel internally
            // for DROP/CONTROL_DROP, but for standard left/right/shift clicks
            // the event must NOT be cancelled so items actually move.
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
                return;
            }

            // Valid storage interaction — delegate permission check to GUI.
            // The GUI does NOT cancel the event itself; it just checks perms
            // and sends a message. If perm fails we cancel here.
            boolean allowed = vaultGui.checkStoragePermission(player, slot, click);
            if (!allowed) {
                event.setCancelled(true);
            }
            // If allowed: event is NOT cancelled → Bukkit handles the item move normally.
            return;
        }

        // ── All other QC GUIs: cancel everything ─────────────
        event.setCancelled(true);

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

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(inventory)) return;
        if (slot < 0) return;

        routeClick(holder, player, slot, click);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!isQCHolder(holder)) return;

        if (holder instanceof ClanVaultGUI vaultGui) {
            if (plugin.getClanVaultManager().isAdminInspector(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            // Cancel drag if any slot is in the nav bar
            for (int dragSlot : event.getRawSlots()) {
                if (vaultGui.isNavSlot(dragSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Allow drag in storage area — permission check not needed for drag
            return;
        }

        event.setCancelled(true);
    }

    private void routeClick(InventoryHolder holder, Player player, int slot, ClickType click) {
        if (holder instanceof BaseGUI gui) {
            gui.handleClick(player, slot, click);
        }
    }

    private boolean isQCHolder(InventoryHolder holder) {
        return holder instanceof BaseGUI;
    }
}