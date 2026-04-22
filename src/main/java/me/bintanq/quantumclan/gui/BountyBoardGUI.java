package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.BountyEntry;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounty Board GUI — shows all active bounties from BountyManager cache.
 *
 * Layout (54 slots):
 *  Slots 10-16, 19-25, 28-34 — bounty entries (up to 21 per page)
 *  Slot  4 — info (total active bounties)
 *  Slot 49 — Close
 *  Slot 45 — Prev page
 *  Slot 53 — Next page
 *
 * Each bounty shows:
 *  - Target player skull
 *  - Amount
 *  - Posted clan (not poster player — hidden)
 *  - Time remaining
 *
 * Clicking a bounty entry does nothing (board is informational only).
 * Submit is done via /qclan bounty submit command.
 */
public class BountyBoardGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final int page;
    private final List<BountyEntry> bounties;
    private Inventory inventory;

    public BountyBoardGUI(QuantumClan plugin, int page) {
        this.plugin   = plugin;
        this.mm       = plugin.getMiniMessage();
        this.page     = Math.max(0, page);
        // Pull from BountyManager cache — no DB query on open
        this.bounties = plugin.getBountyManager().getActiveBountiesSnapshot();
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <dark_red>☠ Bounty Board <dark_gray>]"));

        fillBorder();
        placeEntries();
        setNavigation();
        setInfoItem();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.RED_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isEntrySlot(i) && i != 4 && i != 45 && i != 49 && i != 53) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void setInfoItem() {
        inventory.setItem(4, makeItem(Material.PLAYER_HEAD,
                "<dark_red>☠ Bounty Board",
                List.of(
                        mm.deserialize("<gray>Bounty aktif: <red>" + bounties.size()),
                        mm.deserialize("<gray>Submit kepala: <yellow>/qclan bounty submit"),
                        mm.deserialize("<gray>Pasang bounty: <yellow>/qclan bounty place <player>")
                )));
    }

    private void placeEntries() {
        int start = page * ENTRY_SLOTS.length;
        int end   = Math.min(start + ENTRY_SLOTS.length, bounties.size());

        for (int i = start; i < end; i++) {
            BountyEntry entry  = bounties.get(i);
            int slotIndex      = i - start;
            inventory.setItem(ENTRY_SLOTS[slotIndex], buildEntryItem(entry));
        }
    }

    private ItemStack buildEntryItem(BountyEntry entry) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTargetUuid());
        String targetName    = target.getName() != null ? target.getName() : "Unknown";

        // Poster clan name (not player name — hidden by design)
        Clan posterClan = plugin.getClanManager().getClanById(entry.getClanIdPoster());
        String posterClanName = posterClan != null ? posterClan.getColoredTag() : "<gray>Unknown";

        // Time remaining
        long secondsLeft = entry.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        String timeLeft  = formatTimeLeft(secondsLeft);

        List<Component> lore = List.of(
                mm.deserialize("<gray>Target: <red><bold>" + targetName),
                mm.deserialize("<gray>Hadiah: <gold>" +
                        plugin.getEconomyProvider().format(entry.getAmount())),
                mm.deserialize("<gray>Dipasang oleh: " + posterClanName),
                mm.deserialize("<gray>Berakhir: <yellow>" + timeLeft),
                Component.empty(),
                mm.deserialize("<dark_gray>Bunuh target & submit kepala untuk klaim.")
        );

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(mm.deserialize("<!italic><red>☠ <yellow>" + targetName));
            meta.lore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void setNavigation() {
        int totalPages = getTotalPages();

        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(45, makeItem(Material.ARROW,
                    "<yellow>« Sebelumnya",
                    List.of(mm.deserialize("<gray>Halaman " + page + " / " + totalPages))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, makeItem(Material.ARROW,
                    "<yellow>Berikutnya »",
                    List.of(mm.deserialize("<gray>Halaman " + (page + 2) + " / " + totalPages))));
        }
    }

    // ── Click handler ─────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { openPage(player, page - 1); return; }
            if (slot == 53 && page < getTotalPages() - 1) { openPage(player, page + 1); return; }
            // Entry clicks — no action, board is info-only
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        BountyBoardGUI gui = new BountyBoardGUI(plugin, newPage);
        player.openInventory(gui.build());
    }

    // ── Static open ───────────────────────────────────────────

    public static void open(QuantumClan plugin, Player player) {
        BountyBoardGUI gui = new BountyBoardGUI(plugin, 0);
        player.openInventory(gui.build());
    }

    // ── Helpers ───────────────────────────────────────────────

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(bounties.size() / (double) ENTRY_SLOTS.length));
    }

    private boolean isEntrySlot(int slot) {
        for (int s : ENTRY_SLOTS) if (s == slot) return true;
        return false;
    }

    private String formatTimeLeft(long seconds) {
        if (seconds <= 0) return "Expired";
        long hours = seconds / 3600;
        long mins  = (seconds % 3600) / 60;
        if (hours > 0) return hours + "j " + mins + "m";
        return mins + "m " + (seconds % 60) + "d";
    }

    @Override
    public Inventory getInventory() { return inventory; }

    private ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}