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
 * Now includes ClanHallGUI and ClanHallConfirmGUI.
 */
public class InventoryClickListener implements Listener {

    private final QuantumClan plugin;

    public InventoryClickListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory      = event.getInventory();
        InventoryHolder holder   = inventory.getHolder();

        if (!isQCHolder(holder)) return;

        event.setCancelled(true);

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

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(inventory)) return;

        int slot = event.getRawSlot();
        if (slot < 0) return;

        routeClick(holder, player, slot, click);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (isQCHolder(event.getInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    private void routeClick(InventoryHolder holder, Player player, int slot, ClickType click) {
        switch (holder) {
            case MainMenuGUI gui           -> gui.handleClick(player, slot, click);
            case ClanInfoGUI gui           -> gui.handleClick(player, slot, click);
            case ClanShopGUI gui           -> gui.handleClick(player, slot, click);
            case ShopConfirmGUI gui        -> gui.handleClick(player, slot, click);
            case ClanHomeGUI gui           -> gui.handleClick(player, slot, click);
            case BountyBoardGUI gui        -> gui.handleClick(player, slot, click);
            case ClanTopGUI gui            -> gui.handleClick(player, slot, click);
            case UpgradeGUI gui            -> gui.handleClick(player, slot, click);
            case WarGUI gui                -> gui.handleClick(player, slot, click);
            case ContributionShopGUI gui   -> gui.handleClick(player, slot, click);
            case DisbandConfirmGUI gui     -> gui.handleClick(player, slot, click);
            case CoinsShopGUI gui          -> gui.handleClick(player, slot, click);
            case ClanHallGUI gui           -> gui.handleClick(player, slot, click);       // NEW
            case ClanHallConfirmGUI gui    -> gui.handleClick(player, slot, click);       // NEW
            default -> { /* Unrecognised QC GUI — already cancelled */ }
        }
    }

    private boolean isQCHolder(InventoryHolder holder) {
        return holder instanceof MainMenuGUI
                || holder instanceof ClanInfoGUI
                || holder instanceof ClanShopGUI
                || holder instanceof ShopConfirmGUI
                || holder instanceof ClanHomeGUI
                || holder instanceof BountyBoardGUI
                || holder instanceof ClanTopGUI
                || holder instanceof UpgradeGUI
                || holder instanceof WarGUI
                || holder instanceof ContributionShopGUI
                || holder instanceof DisbandConfirmGUI
                || holder instanceof CoinsShopGUI
                || holder instanceof ClanHallGUI           // NEW
                || holder instanceof ClanHallConfirmGUI;  // NEW
    }
}