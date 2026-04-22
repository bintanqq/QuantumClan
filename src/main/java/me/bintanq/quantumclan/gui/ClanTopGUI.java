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
 *
 * BUG FIX #2: All text from messages.yml.
 * BUG FIX #7: Back button when opened from main menu.
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
    private final GUINavigation backAction;
    private Inventory inventory;

    public ClanTopGUI(QuantumClan plugin, int page, GUINavigation backAction) {
        this.plugin      = plugin;
        this.mm          = plugin.getMiniMessage();
        this.page        = Math.max(0, page);
        this.leaderboard = new ArrayList<>(plugin.getClanManager().getLeaderboard());
        this.backAction  = backAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(gc.getClanTopTitle()));

        ItemStack glass = makeItem(gc.getClanTopFiller(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isEntrySlot(i) && i != gc.getClanTopInfoSlot()
                    && i != gc.getClanTopCloseSlot()
                    && i != gc.getClanTopPrevSlot()
                    && i != gc.getClanTopNextSlot())
                inventory.setItem(i, glass);
        }

        // Info item
        inventory.setItem(gc.getClanTopInfoSlot(), makeItem(Material.NETHER_STAR,
                msg.get("gui.top-info-name"),
                List.of(
                        mm.deserialize(msg.get("gui.top-info-lore1", "{count}", String.valueOf(leaderboard.size()))),
                        mm.deserialize(msg.get("gui.top-info-lore2"))
                )));

        placeEntries();
        setNavigation();

        return inventory;
    }

    private void placeEntries() {
        var msg = plugin.getMessagesManager();
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
                    mm.deserialize(msg.get("gui.top-clan-tag", "{tag}", clan.getColoredTag())),
                    mm.deserialize(msg.get("gui.top-clan-level", "{level}", String.valueOf(clan.getLevel()))),
                    mm.deserialize(msg.get("gui.top-clan-rep", "{rep}", String.valueOf(clan.getReputation()))),
                    mm.deserialize(msg.get("gui.top-clan-members",
                            "{count}", String.valueOf(clan.getMemberCount()),
                            "{online}", String.valueOf(onlineCount))),
                    mm.deserialize(msg.get("gui.top-clan-treasury",
                            "{money}", plugin.getEconomyProvider().format(clan.getMoney())))
            );

            Material mat = switch (rank) {
                case 1 -> plugin.getGuiConfigManager().getClanTopRank1Mat();
                case 2 -> plugin.getGuiConfigManager().getClanTopRank2Mat();
                case 3 -> plugin.getGuiConfigManager().getClanTopRank3Mat();
                default -> plugin.getGuiConfigManager().getClanTopDefaultMat();
            };

            inventory.setItem(ENTRY_SLOTS[slotIndex],
                    makeItem(mat, rankPrefix + clan.getName(), lore));
        }
    }

    private void setNavigation() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        int totalPages = Math.max(1, (int) Math.ceil(leaderboard.size() / (double) ENTRY_SLOTS.length));

        String closeOrBack = backAction != null ? msg.get("gui.back") : msg.get("gui.close");
        inventory.setItem(gc.getClanTopCloseSlot(),
                makeItem(Material.BARRIER, closeOrBack, Collections.emptyList()));

        if (page > 0)
            inventory.setItem(gc.getClanTopPrevSlot(), makeItem(Material.ARROW,
                    msg.get("gui.prev"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page),
                            "{total}", String.valueOf(totalPages))))));

        if (page < totalPages - 1)
            inventory.setItem(gc.getClanTopNextSlot(), makeItem(Material.ARROW,
                    msg.get("gui.next"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page + 2),
                            "{total}", String.valueOf(totalPages))))));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();
            int totalPages = Math.max(1, (int) Math.ceil(leaderboard.size() / (double) ENTRY_SLOTS.length));

            if (slot == gc.getClanTopCloseSlot()) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
            if (slot == gc.getClanTopPrevSlot() && page > 0) { openPage(player, page - 1); return; }
            if (slot == gc.getClanTopNextSlot() && page < totalPages - 1) { openPage(player, page + 1); return; }

            for (int i = 0; i < ENTRY_SLOTS.length; i++) {
                if (ENTRY_SLOTS[i] == slot) {
                    int clanIndex = page * ENTRY_SLOTS.length + i;
                    if (clanIndex < leaderboard.size()) {
                        Clan clan = leaderboard.get(clanIndex);
                        player.closeInventory();
                        ClanInfoGUI.openFromMenu(plugin, player, clan,
                                () -> openFromMenu(plugin, player, backAction));
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
        player.openInventory(new ClanTopGUI(plugin, newPage, backAction).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new ClanTopGUI(plugin, 0, null).build());
    }

    public static void openFromMenu(QuantumClan plugin, Player player, GUINavigation backAction) {
        player.openInventory(new ClanTopGUI(plugin, 0, backAction).build());
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