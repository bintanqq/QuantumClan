package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClanTopGUI — leaderboard of top clans by reputation.
 * Data is pulled from ClanManager leaderboard cache (no DB query on open).
 *
 * Layout (54 slots):
 *  Slot  4  — Info item
 *  Slots 10-16, 19-25, 28-34 — clan entries (top 21 per page)
 *  Slot 49 — Close
 *  Slot 45 — Prev / Slot 53 — Next
 */
public class ClanTopGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final int page;
    private final List<Clan> leaderboard;
    private Inventory inventory;

    public ClanTopGUI(QuantumClan plugin, int page) {
        this.plugin      = plugin;
        this.mm          = plugin.getMiniMessage();
        this.page        = Math.max(0, page);
        this.leaderboard = new ArrayList<>(plugin.getClanManager().getLeaderboard());
    }

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <gold>⭐ Top Clan <dark_gray>]"));

        fillBorder();
        setInfoItem();
        placeEntries();
        setNavigation();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.YELLOW_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isEntrySlot(i) && i != 4 && i != 45 && i != 49 && i != 53)
                inventory.setItem(i, glass);
        }
    }

    private void setInfoItem() {
        inventory.setItem(4, makeItem(Material.NETHER_STAR,
                "<gold>⭐ Top Clan Leaderboard",
                List.of(mm.deserialize("<gray>Total clan: <yellow>" + leaderboard.size()),
                        mm.deserialize("<gray>Diurutkan berdasarkan <aqua>Reputasi</aqua>."))));
    }

    private void placeEntries() {
        int start = page * ENTRY_SLOTS.length;
        int end   = Math.min(start + ENTRY_SLOTS.length, leaderboard.size());

        for (int i = start; i < end; i++) {
            Clan clan      = leaderboard.get(i);
            int rank       = i + 1;
            int slotIndex  = i - start;
            int onlineCount = plugin.getClanManager().getOnlineCount(clan.getId());

            String rankPrefix = switch (rank) {
                case 1 -> "<gold>#1 ";
                case 2 -> "<gray>#2 ";
                case 3 -> "<dark_red>#3 ";
                default -> "<dark_gray>#" + rank + " ";
            };

            List<Component> lore = List.of(
                    mm.deserialize("<gray>Tag: " + clan.getColoredTag()),
                    mm.deserialize("<gray>Level: <yellow>" + clan.getLevel()),
                    mm.deserialize("<gray>Reputasi: <aqua>" + clan.getReputation()),
                    mm.deserialize("<gray>Member: <yellow>" + clan.getMemberCount()
                            + " <dark_gray>(<green>" + onlineCount + " online<dark_gray>)"),
                    mm.deserialize("<gray>Kas: <gold>" +
                            plugin.getEconomyProvider().format(clan.getMoney()))
            );

            Material mat = switch (rank) {
                case 1 -> Material.GOLD_BLOCK;
                case 2 -> Material.IRON_BLOCK;
                case 3 -> Material.COPPER_BLOCK;
                default -> Material.STONE;
            };

            inventory.setItem(ENTRY_SLOTS[slotIndex],
                    makeItem(mat, rankPrefix + clan.getName(), lore));
        }
    }

    private void setNavigation() {
        int totalPages = Math.max(1, (int) Math.ceil(leaderboard.size() / (double) ENTRY_SLOTS.length));
        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));
        if (page > 0)
            inventory.setItem(45, makeItem(Material.ARROW, "<yellow>« Sebelumnya",
                    List.of(mm.deserialize("<gray>Hal. " + page + "/" + totalPages))));
        if (page < totalPages - 1)
            inventory.setItem(53, makeItem(Material.ARROW, "<yellow>Berikutnya »",
                    List.of(mm.deserialize("<gray>Hal. " + (page + 2) + "/" + totalPages))));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            int totalPages = Math.max(1,
                    (int) Math.ceil(leaderboard.size() / (double) ENTRY_SLOTS.length));
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { openPage(player, page - 1); return; }
            if (slot == 53 && page < totalPages - 1) { openPage(player, page + 1); return; }

            // Click on a clan entry — open clan info
            for (int i = 0; i < ENTRY_SLOTS.length; i++) {
                if (ENTRY_SLOTS[i] == slot) {
                    int clanIndex = page * ENTRY_SLOTS.length + i;
                    if (clanIndex < leaderboard.size()) {
                        Clan clan = leaderboard.get(clanIndex);
                        player.closeInventory();
                        ClanInfoGUI.open(plugin, player, clan);
                    }
                    return;
                }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        player.openInventory(new ClanTopGUI(plugin, newPage).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new ClanTopGUI(plugin, 0).build());
    }

    private boolean isEntrySlot(int s) {
        for (int x : ENTRY_SLOTS) if (x == s) return true;
        return false;
    }

    @Override public Inventory getInventory() { return inventory; }

    private ItemStack makeItem(Material m, String name, List<Component> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}