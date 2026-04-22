package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.ClanRole;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI that displays a clan's info: stats, member list, homes, and rank.
 *
 * Layout (54 slots):
 *  Slot  4  — Clan banner / tag display
 *  Slot 10  — Stats (level, money, reputation, member count)
 *  Slot 12  — Leader skull
 *  Slot 14  — Shield status
 *  Slot 16  — Clan rank
 *  Slots 19-43 — Member skulls (up to 18 members per page)
 *  Slot 45  — Previous page
 *  Slot 53  — Next page / Close
 *  Slot 49  — Close button
 */
public class ClanInfoGUI implements InventoryHolder {

    private static final int SIZE         = 54;
    private static final int PAGE_SLOTS   = 18;
    private static final int MEMBERS_START = 19;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    // Anti-dupe: processing flag per player
    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private final int page;
    private Inventory inventory;

    public ClanInfoGUI(QuantumClan plugin, Clan clan, int page) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.clan   = clan;
        this.page   = Math.max(0, page);
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        String title = "<dark_gray>[ <gold>" + clan.getName() + " <dark_gray>]";
        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(title));

        fillBorder();
        setStats();
        setMembersPage();
        setNavigationButtons();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        int[] border = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int slot : border) inventory.setItem(slot, glass);
    }

    private void setStats() {
        // ── Clan Tag / Banner ──────────────────────────────────
        List<Component> bannerLore = List.of(
                mm.deserialize("<gray>Tag: " + clan.getColoredTag()),
                mm.deserialize("<gray>Dibuat: <yellow>" + DATE_FMT.format(clan.getCreatedAt()))
        );
        inventory.setItem(4, makeItem(Material.YELLOW_BANNER,
                "<gold><bold>" + clan.getName(), bannerLore));

        // ── Stats ──────────────────────────────────────────────
        int maxMembers = plugin.getConfigManager().getMaxMembers(clan.getLevel());
        int maxHomes   = plugin.getConfigManager().getMaxHomes(clan.getLevel());
        int rank       = plugin.getClanManager().getClanRank(clan.getId());
        String rankStr = rank > 0 ? "#" + rank : "Unranked";

        List<Component> statsLore = List.of(
                mm.deserialize("<gray>Level: <yellow>" + clan.getLevel()
                        + " <dark_gray>/ <yellow>" + plugin.getConfigManager().getMaxLevel()),
                mm.deserialize("<gray>Member: <yellow>" + clan.getMemberCount()
                        + " <dark_gray>/ <yellow>" + maxMembers),
                mm.deserialize("<gray>Homes: <yellow>" + clan.getHomeCount()
                        + " <dark_gray>/ <yellow>" + maxHomes),
                mm.deserialize("<gray>Kas Clan: <gold>"
                        + plugin.getEconomyProvider().format(clan.getMoney())),
                mm.deserialize("<gray>Reputasi: <aqua>" + clan.getReputation()),
                mm.deserialize("<gray>Rank: <yellow>" + rankStr)
        );
        inventory.setItem(10, makeItem(Material.BOOK, "<aqua>Statistik Clan", statsLore));

        // ── Leader skull ───────────────────────────────────────
        OfflinePlayer leader = Bukkit.getOfflinePlayer(clan.getLeaderUuid());
        ItemStack leaderSkull = makeSkull(leader,
                "<gold>Leader: <yellow>" + leader.getName(),
                List.of(mm.deserialize("<gray>UUID: <dark_gray>" + clan.getLeaderUuid())));
        inventory.setItem(12, leaderSkull);

        // ── Shield ─────────────────────────────────────────────
        Component shieldStatus;
        if (clan.hasActiveShield()) {
            shieldStatus = mm.deserialize("<green>✔ Shield Aktif");
        } else {
            shieldStatus = mm.deserialize("<red>✘ Shield Tidak Aktif");
        }
        List<Component> shieldLore = new ArrayList<>();
        shieldLore.add(shieldStatus);
        if (clan.hasActiveShield() && clan.getShieldUntil() != null) {
            shieldLore.add(mm.deserialize("<gray>Berakhir: <yellow>"
                    + DATE_FMT.format(clan.getShieldUntil())));
        }
        inventory.setItem(14, makeItem(Material.SHIELD, "<blue>Status Shield", shieldLore));

        // ── Online count ───────────────────────────────────────
        int online = plugin.getClanManager().getOnlineCount(clan.getId());
        List<Component> onlineLore = List.of(
                mm.deserialize("<green>Online: <yellow>" + online),
                mm.deserialize("<gray>Total: <yellow>" + clan.getMemberCount())
        );
        inventory.setItem(16, makeItem(Material.EMERALD, "<green>Member Online", onlineLore));
    }

    private void setMembersPage() {
        List<UUID> members = new ArrayList<>(clan.getMemberUuids());
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) PAGE_SLOTS));
        int currentPage = Math.min(page, totalPages - 1);
        int start = currentPage * PAGE_SLOTS;
        int end   = Math.min(start + PAGE_SLOTS, members.size());

        // Clear member slots
        for (int i = 0; i < PAGE_SLOTS; i++) {
            int slot = MEMBERS_START + i;
            if (slot < 45) inventory.setItem(slot, null);
        }

        List<UUID> pageMembers = members.subList(start, end);
        for (int i = 0; i < pageMembers.size(); i++) {
            UUID uuid = pageMembers.get(i);
            ClanMember member = plugin.getClanManager().getMember(uuid);
            OfflinePlayer op  = Bukkit.getOfflinePlayer(uuid);

            String roleName = member != null ? member.getRole() : "member";
            ClanRole role   = plugin.getRolesConfigManager().getRole(roleName);
            String roleDisplay = role != null ? role.getDisplayName() : roleName;
            int contribution   = member != null ? member.getContributionPoints() : 0;
            boolean isOnline   = Bukkit.getPlayer(uuid) != null;

            List<Component> lore = List.of(
                    mm.deserialize("<gray>Role: " + roleDisplay),
                    mm.deserialize("<gray>Kontribusi: <aqua>" + contribution),
                    mm.deserialize(isOnline ? "<green>● Online" : "<red>● Offline")
            );

            ItemStack skull = makeSkull(op,
                    (isOnline ? "<green>" : "<gray>") + op.getName(), lore);
            int slot = MEMBERS_START + i;
            if (slot < 45) inventory.setItem(slot, skull);
        }
    }

    private void setNavigationButtons() {
        List<UUID> members = new ArrayList<>(clan.getMemberUuids());
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) PAGE_SLOTS));

        // Close button
        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));

        // Prev page
        if (page > 0) {
            inventory.setItem(45, makeItem(Material.ARROW,
                    "<yellow>« Halaman Sebelumnya",
                    List.of(mm.deserialize("<gray>Halaman " + page + " / " + totalPages))));
        }

        // Next page
        if (page < totalPages - 1) {
            inventory.setItem(53, makeItem(Material.ARROW,
                    "<yellow>Halaman Berikutnya »",
                    List.of(mm.deserialize("<gray>Halaman " + (page + 2) + " / " + totalPages))));
        }
    }

    // ── Click handler ─────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return; // Already processing
        try {
            switch (slot) {
                case 49 -> player.closeInventory();
                case 45 -> {
                    if (page > 0) openPage(player, page - 1);
                }
                case 53 -> {
                    List<UUID> members = new ArrayList<>(clan.getMemberUuids());
                    int totalPages = Math.max(1,
                            (int) Math.ceil(members.size() / (double) PAGE_SLOTS));
                    if (page < totalPages - 1) openPage(player, page + 1);
                    else player.closeInventory();
                }
                default -> { /* Info items — no action */ }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        ClanInfoGUI gui = new ClanInfoGUI(plugin, clan, newPage);
        player.openInventory(gui.build());
    }

    // ── Static open helper ────────────────────────────────────

    public static void open(QuantumClan plugin, Player viewer, Clan clan) {
        ClanInfoGUI gui = new ClanInfoGUI(plugin, clan, 0);
        viewer.openInventory(gui.build());
    }

    // ── Inventory holder ──────────────────────────────────────

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ── Item helpers ──────────────────────────────────────────

    private ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) {
            List<Component> noItalic = new ArrayList<>();
            for (Component line : lore) {
                noItalic.add(Component.empty().append(line)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            meta.lore(noItalic);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSkull(OfflinePlayer op, String name, List<Component> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        meta.setOwningPlayer(op);
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) {
            List<Component> noItalic = new ArrayList<>();
            for (Component line : lore) {
                noItalic.add(Component.empty().append(line)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            meta.lore(noItalic);
        }
        skull.setItemMeta(meta);
        return skull;
    }
}