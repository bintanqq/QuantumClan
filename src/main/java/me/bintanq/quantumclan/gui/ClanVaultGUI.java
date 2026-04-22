package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

/**
 * Clan Vault GUI.
 *
 *  - On open, if the clan's level grants more rows than what is stored,
 *    the inventory is automatically expanded.
 */
public class ClanVaultGUI extends AbstractClanGUI implements Listener {

    private final Player viewer;
    private final Clan clan;
    private final ItemStack[] storageContents;
    private final boolean adminMode;

    private final int storageRows;
    private final int storageSlots;
    private final int totalSize;

    private static final int NAV_CLOSE_OFFSET = 0;
    private static final int NAV_INFO_OFFSET  = 4;

    public ClanVaultGUI(QuantumClan plugin, Player viewer, Clan clan,
                        ItemStack[] storageContents, boolean adminMode) {
        super(plugin);
        this.viewer           = viewer;
        this.clan             = clan;
        this.storageContents  = storageContents;
        this.adminMode        = adminMode;

        // Re-read vault rows based on CURRENT clan level each time GUI opens
        int rows = plugin.getConfigManager().getVaultRows(clan.getLevel());
        this.storageRows  = Math.max(1, Math.min(6, rows));
        this.storageSlots = storageRows * 9;
        this.totalSize    = (storageRows + 1) * 9;
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        String rawTitle = plugin.getGuiConfigManager()
                .getString("vault.title", "<dark_gray>[ <gold>Clan Vault — {clan} <dark_gray>]");
        rawTitle = rawTitle.replace("{clan}", clan.getName());

        inventory = Bukkit.createInventory(this, totalSize, mm.deserialize(rawTitle));

        // Fill storage slots with existing content
        for (int i = 0; i < storageSlots && i < storageContents.length; i++) {
            inventory.setItem(i, storageContents[i]);
        }

        buildNavBar();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return inventory;
    }

    private void buildNavBar() {
        var msg = plugin.getMessagesManager();
        int navStart = storageSlots;

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = navStart; i < totalSize; i++) {
            inventory.setItem(i, filler);
        }

        // Close button
        Material closeMat = plugin.getGuiConfigManager().getMaterial("vault.close-material", Material.BARRIER);
        String closeName  = msg.getRaw("vault.close-name");
        if (closeName == null || closeName.isBlank()) closeName = msg.get("gui.close");
        inventory.setItem(navStart + NAV_CLOSE_OFFSET,
                makeItem(closeMat, closeName, Collections.emptyList()));

        // Info item
        int used = countUsedSlots();
        List<Component> infoLore = List.of(
                mm.deserialize(msg.get("vault.info-level",  "{level}",  String.valueOf(clan.getLevel()))),
                mm.deserialize(msg.get("vault.info-slots",  "{used}",   String.valueOf(used),
                        "{total}", String.valueOf(storageSlots))),
                adminMode
                        ? mm.deserialize(msg.get("vault.info-read-only"))
                        : Component.empty()
        );
        String infoName = adminMode ? msg.get("vault.info-name-admin") : msg.get("vault.info-name");
        inventory.setItem(navStart + NAV_INFO_OFFSET,
                makeItem(Material.CHEST, infoName, infoLore));
    }

    // ── Click handler ─────────────────────────────────────────

    /**
     * Called for nav-bar clicks and for reporting routing from InventoryClickListener.
     */
    public void handleClick(Player player, int rawSlot, ClickType click) {
        int navStart = storageSlots;

        if (adminMode) return; // admin: everything already cancelled by listener

        if (rawSlot == navStart + NAV_CLOSE_OFFSET) {
            player.closeInventory();
        }
    }

    /**
     * Returns true if the player is allowed to interact with this
     * storage slot. Called by InventoryClickListener BEFORE deciding to cancel.
     * Returns false (and sends message) when permission is denied.
     */
    public boolean checkStoragePermission(Player player, int rawSlot, ClickType click) {
        if (adminMode) return false; // admin inspect = read only

        // Determine action direction heuristically
        boolean hasItemOnCursor = player.getItemOnCursor() != null
                && player.getItemOnCursor().getType() != Material.AIR;
        ItemStack slotItem = inventory != null ? inventory.getItem(rawSlot) : null;
        boolean slotEmpty  = slotItem == null || slotItem.getType() == Material.AIR;

        // Shift-click always takes FROM the inventory
        boolean isWithdraw;
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            isWithdraw = true;
        } else if (hasItemOnCursor && slotEmpty) {
            isWithdraw = false; // depositing
        } else if (!hasItemOnCursor && !slotEmpty) {
            isWithdraw = true;  // picking up
        } else {
            // Swapping — require both perms; deny if either is missing
            boolean canDep = plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-deposit-vault");
            boolean canWith = plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-withdraw-vault");
            if (!canDep || !canWith) {
                plugin.sendMessage(player, "vault.no-permission-withdraw");
                return false;
            }
            return true;
        }

        if (!isWithdraw) {
            if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-deposit-vault")) {
                plugin.sendMessage(player, "vault.no-permission-deposit");
                return false;
            }
        } else {
            if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-withdraw-vault")) {
                plugin.sendMessage(player, "vault.no-permission-withdraw");
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the given raw slot is in the navigation bar.
     */
    public boolean isNavSlot(int rawSlot) {
        return rawSlot >= storageSlots;
    }

    // ── InventoryCloseEvent ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() != this) return;

        InventoryCloseEvent.getHandlerList().unregister(this);

        // Sync storage array from closed inventory
        for (int i = 0; i < storageSlots; i++) {
            storageContents[i] = event.getInventory().getItem(i);
        }

        plugin.getClanVaultManager().onVaultClose(p, clan);
    }

    // ── Helpers ───────────────────────────────────────────────

    private int countUsedSlots() {
        int used = 0;
        for (int i = 0; i < storageSlots && i < storageContents.length; i++) {
            ItemStack item = inventory != null ? inventory.getItem(i) : storageContents[i];
            if (item != null && item.getType() != Material.AIR) used++;
        }
        return used;
    }

}