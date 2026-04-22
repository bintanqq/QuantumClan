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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Clan Vault GUI.
 *
 * Layout:
 *   Rows 1..N   = storage slots (N = vault-rows from config for clan level)
 *   Last row    = navigation bar (not storage):
 *     slot 0  = close button
 *     slot 4  = vault info (level, slots used / total)
 *     slot 8  = (empty / future use)
 *
 * Admin inspect mode: items are visible but all click types are cancelled.
 *
 * The GUI registers itself as a Bukkit Listener so it can catch
 * InventoryCloseEvent for its own inventory — this avoids needing
 * to add vault-specific logic to a global listener.
 */
public class ClanVaultGUI implements InventoryHolder, Listener {

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final Clan clan;
    /** The live storage array passed in from ClanVaultManager. */
    private final ItemStack[] storageContents;
    private final boolean adminMode;

    private final int storageRows;
    private final int storageSlots;
    /** Total inventory size = storage rows + 1 (nav bar) */
    private final int totalSize;

    private Inventory inventory;

    // Nav bar slot offsets from the start of the last row
    private static final int NAV_CLOSE_OFFSET = 0;
    private static final int NAV_INFO_OFFSET  = 4;

    public ClanVaultGUI(QuantumClan plugin, Player viewer, Clan clan,
                        ItemStack[] storageContents, boolean adminMode) {
        this.plugin           = plugin;
        this.mm               = plugin.getMiniMessage();
        this.viewer           = viewer;
        this.clan             = clan;
        this.storageContents  = storageContents;
        this.adminMode        = adminMode;

        int rows = plugin.getConfigManager().getVaultRows(clan.getLevel());
        this.storageRows  = Math.max(1, Math.min(6, rows));
        this.storageSlots = storageRows * 9;
        this.totalSize    = (storageRows + 1) * 9; // +1 for nav bar
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

        // Build nav bar
        buildNavBar();

        // Register self as listener so we catch our own InventoryCloseEvent
        Bukkit.getPluginManager().registerEvents(this, plugin);

        return inventory;
    }

    private void buildNavBar() {
        var msg = plugin.getMessagesManager();
        int navStart = storageSlots; // first slot of the nav row

        // Fill entire nav row with glass pane filler
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = navStart; i < totalSize; i++) {
            inventory.setItem(i, filler);
        }

        // Close button
        Material closeMat = adminMode ? Material.BARRIER
                : plugin.getGuiConfigManager().getMaterial("vault.close-material", Material.BARRIER);
        String closeName = adminMode
                ? msg.get("gui.close")
                : msg.get("vault.close-name", "gui.close");
        if (closeName.startsWith("<red>[Missing")) closeName = msg.get("gui.close");
        inventory.setItem(navStart + NAV_CLOSE_OFFSET,
                makeItem(closeMat, closeName, Collections.emptyList()));

        // Info item
        int used = countUsedSlots();
        List<Component> infoLore = List.of(
                mm.deserialize(msg.get("vault.info-level",   "{level}",  String.valueOf(clan.getLevel()))),
                mm.deserialize(msg.get("vault.info-slots",   "{used}",   String.valueOf(used),
                        "{total}", String.valueOf(storageSlots))),
                adminMode
                        ? mm.deserialize(msg.get("vault.info-read-only"))
                        : Component.empty()
        );
        String infoName = adminMode
                ? msg.get("vault.info-name-admin")
                : msg.get("vault.info-name");
        inventory.setItem(navStart + NAV_INFO_OFFSET,
                makeItem(Material.CHEST, infoName, infoLore));
    }

    // ── Click handler ─────────────────────────────────────────

    /**
     * Called by InventoryClickListener when a click happens on a QC GUI.
     * Navigation bar clicks are handled here; storage clicks pass through
     * unless in admin mode.
     */
    public void handleClick(Player player, int rawSlot, ClickType click) {
        int navStart = storageSlots;

        // ── Admin mode: block everything ──────────────────────
        if (adminMode) {
            return; // InventoryClickListener already called setCancelled(true)
        }

        // ── Storage area ──────────────────────────────────────
        if (rawSlot < storageSlots) {
            // Permission check for deposit/withdraw
            boolean isDeposit = player.getItemOnCursor() != null
                    && player.getItemOnCursor().getType() != Material.AIR
                    && inventory.getItem(rawSlot) == null;

            if (isDeposit) {
                if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-deposit-vault")) {
                    plugin.sendMessage(player, "vault.no-permission-deposit");
                    return;
                }
            } else {
                if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-withdraw-vault")) {
                    plugin.sendMessage(player, "vault.no-permission-withdraw");
                    return;
                }
            }

            // Allow the click to proceed — InventoryClickListener should NOT cancel
            // storage clicks. We achieve this by NOT calling event.setCancelled(false)
            // here (the listener already cancelled; we need to un-cancel for storage).
            // The solution: InventoryClickListener checks isNavSlot() before cancelling.
            // We expose isNavSlot() as a helper below.
            return;
        }

        // ── Nav bar ───────────────────────────────────────────
        if (rawSlot == navStart + NAV_CLOSE_OFFSET) {
            player.closeInventory();
        }
        // NAV_INFO_OFFSET: info item — no action
    }

    /**
     * Returns true if the given raw slot is in the navigation bar.
     * Used by InventoryClickListener to decide whether to cancel clicks
     * in the storage area (storage clicks should NOT be cancelled so items can move).
     */
    public boolean isNavSlot(int rawSlot) {
        return rawSlot >= storageSlots;
    }

    // ── InventoryCloseEvent ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() != this) return;

        // Unregister self to prevent memory leaks
        InventoryCloseEvent.getHandlerList().unregister(this);

        // Sync storage array from closed inventory
        for (int i = 0; i < storageSlots; i++) {
            storageContents[i] = event.getInventory().getItem(i);
        }

        // Delegate save/cleanup to manager
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

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}