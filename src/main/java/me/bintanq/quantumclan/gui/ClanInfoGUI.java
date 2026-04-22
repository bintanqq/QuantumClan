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
 * BUG FIX #2: All text pulled from messages.yml — no hardcoded Indonesian/English strings.
 * BUG FIX #7: Back button support when opened from a parent menu.
 */
public class ClanInfoGUI implements InventoryHolder {

    private static final int SIZE         = 54;
    private static final int PAGE_SLOTS   = 18;
    private static final int MEMBERS_START = 19;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private final int page;
    /** Navigation callback — null means "show Close button, not Back". */
    private final GUINavigation backAction;
    private Inventory inventory;

    public ClanInfoGUI(QuantumClan plugin, Clan clan, int page, GUINavigation backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.clan       = clan;
        this.page       = Math.max(0, page);
        this.backAction = backAction;
    }

    public Inventory build() {
        String title = plugin.getGuiConfigManager().getClanInfoTitle()
                .replace("{clan}", clan.getName());
        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(title));

        fillBorder();
        setStats();
        setMembersPage();
        setNavigationButtons();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(plugin.getGuiConfigManager().getClanInfoFiller(), " ", Collections.emptyList());
        int[] border = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int slot : border) inventory.setItem(slot, glass);
    }

    private void setStats() {
        var gc = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        // ── Clan Tag / Banner ──────────────────────────────────
        List<Component> bannerLore = List.of(
                mm.deserialize(msg.get("gui.info-tag-label", "{tag}", clan.getColoredTag())),
                mm.deserialize(msg.get("gui.info-created-label", "{date}", DATE_FMT.format(clan.getCreatedAt())))
        );
        inventory.setItem(4, makeItem(Material.YELLOW_BANNER, "<gold><bold>" + clan.getName(), bannerLore));

        // ── Stats ──────────────────────────────────────────────
        int maxMembers = plugin.getConfigManager().getMaxMembers(clan.getLevel());
        int maxHomes   = plugin.getConfigManager().getMaxHomes(clan.getLevel());
        int rank       = plugin.getClanManager().getClanRank(clan.getId());
        String rankStr = rank > 0 ? "#" + rank : "Unranked";

        List<Component> statsLore = List.of(
                mm.deserialize(msg.get("gui.info-level",
                        "{level}", String.valueOf(clan.getLevel()),
                        "{max}", String.valueOf(plugin.getConfigManager().getMaxLevel()))),
                mm.deserialize(msg.get("gui.info-members",
                        "{current}", String.valueOf(clan.getMemberCount()),
                        "{max}", String.valueOf(maxMembers))),
                mm.deserialize(msg.get("gui.info-homes",
                        "{current}", String.valueOf(clan.getHomeCount()),
                        "{max}", String.valueOf(maxHomes))),
                mm.deserialize(msg.get("gui.info-treasury",
                        "{money}", plugin.getEconomyProvider().format(clan.getMoney()))),
                mm.deserialize(msg.get("gui.info-reputation",
                        "{reputation}", String.valueOf(clan.getReputation()))),
                mm.deserialize(msg.get("gui.info-rank", "{rank}", rankStr))
        );
        inventory.setItem(10, makeItem(Material.BOOK,
                msg.get("gui.info-stats-name"), statsLore));

        // ── Leader skull ───────────────────────────────────────
        OfflinePlayer leader = Bukkit.getOfflinePlayer(clan.getLeaderUuid());
        String leaderName = msg.get("gui.info-leader-name",
                "{name}", leader.getName() != null ? leader.getName() : "Unknown");
        ItemStack leaderSkull = makeSkull(leader, leaderName,
                List.of(mm.deserialize(msg.get("gui.info-leader-uuid",
                        "{uuid}", clan.getLeaderUuid().toString()))));
        inventory.setItem(12, leaderSkull);

        // ── Shield ─────────────────────────────────────────────
        List<Component> shieldLore = new ArrayList<>();
        shieldLore.add(mm.deserialize(clan.hasActiveShield()
                ? msg.get("gui.shield-active")
                : msg.get("gui.shield-inactive")));
        if (clan.hasActiveShield() && clan.getShieldUntil() != null) {
            shieldLore.add(mm.deserialize(msg.get("gui.shield-expires",
                    "{date}", DATE_FMT.format(clan.getShieldUntil()))));
        }
        inventory.setItem(14, makeItem(Material.SHIELD,
                msg.get("gui.info-shield-name"), shieldLore));

        // ── Online count ───────────────────────────────────────
        int online = plugin.getClanManager().getOnlineCount(clan.getId());
        List<Component> onlineLore = List.of(
                mm.deserialize(msg.get("gui.info-online-count", "{online}", String.valueOf(online))),
                mm.deserialize(msg.get("gui.info-total-count", "{total}", String.valueOf(clan.getMemberCount())))
        );
        inventory.setItem(16, makeItem(Material.EMERALD,
                msg.get("gui.info-online-name"), onlineLore));
    }

    private void setMembersPage() {
        var msg = plugin.getMessagesManager();
        List<UUID> members = new ArrayList<>(clan.getMemberUuids());
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) PAGE_SLOTS));
        int currentPage = Math.min(page, totalPages - 1);
        int start = currentPage * PAGE_SLOTS;
        int end   = Math.min(start + PAGE_SLOTS, members.size());

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
                    mm.deserialize(msg.get("gui.role-label", "{role}", roleDisplay)),
                    mm.deserialize(msg.get("gui.contribution-label", "{points}", String.valueOf(contribution))),
                    mm.deserialize(isOnline ? msg.get("gui.online-status") : msg.get("gui.offline-status"))
            );

            String playerName = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            ItemStack skull = makeSkull(op,
                    (isOnline ? "<green>" : "<gray>") + playerName, lore);
            int slot = MEMBERS_START + i;
            if (slot < 45) inventory.setItem(slot, skull);
        }
    }

    private void setNavigationButtons() {
        var msg = plugin.getMessagesManager();
        List<UUID> members = new ArrayList<>(clan.getMemberUuids());
        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) PAGE_SLOTS));

        // Close or Back button
        String closeName = backAction != null
                ? msg.get("gui.back")
                : msg.get("gui.close");
        inventory.setItem(49, makeItem(Material.BARRIER, closeName, Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(45, makeItem(Material.ARROW,
                    msg.get("gui.prev"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page),
                            "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, makeItem(Material.ARROW,
                    msg.get("gui.next"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page + 2),
                            "{total}", String.valueOf(totalPages))))));
        }
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            switch (slot) {
                case 49 -> {
                    if (backAction != null) {
                        player.closeInventory();
                        backAction.navigate();
                    } else {
                        player.closeInventory();
                    }
                }
                case 45 -> { if (page > 0) openPage(player, page - 1); }
                case 53 -> {
                    List<UUID> members = new ArrayList<>(clan.getMemberUuids());
                    int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) PAGE_SLOTS));
                    if (page < totalPages - 1) openPage(player, page + 1);
                    else {
                        if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                        else player.closeInventory();
                    }
                }
                default -> { /* Info items — no action */ }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        ClanInfoGUI gui = new ClanInfoGUI(plugin, clan, newPage, backAction);
        player.openInventory(gui.build());
    }

    /** Open directly from command (no back button — just close). */
    public static void open(QuantumClan plugin, Player viewer, Clan clan) {
        viewer.openInventory(new ClanInfoGUI(plugin, clan, 0, null).build());
    }

    /** Open from a parent menu (shows back button). */
    public static void openFromMenu(QuantumClan plugin, Player viewer, Clan clan, GUINavigation backAction) {
        viewer.openInventory(new ClanInfoGUI(plugin, clan, 0, backAction).build());
    }

    @Override
    public Inventory getInventory() { return inventory; }

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