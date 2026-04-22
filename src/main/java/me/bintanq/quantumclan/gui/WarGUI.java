package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.WarSession;
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
 * War GUI — shows current war status, registered clans, and registration button.
 *
 * BUG FIX #2: All text from messages.yml/gui.yml — no hardcoded strings.
 * BUG FIX #7: Back button when opened from main menu.
 */
public class WarGUI implements InventoryHolder {

    private static final int SLOT_STATUS = 11;
    private static final int SLOT_ACTION = 13;
    private static final int SLOT_CLANS  = 15;
    private static final int SLOT_CLOSE  = 22;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final GUINavigation backAction;
    private Inventory inventory;

    public WarGUI(QuantumClan plugin, GUINavigation backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.backAction = backAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, gc.getWarSize(), mm.deserialize(gc.getWarTitle()));

        ItemStack glass = makeItem(gc.getWarFiller(), " ", Collections.emptyList());
        for (int i = 0; i < gc.getWarSize(); i++) inventory.setItem(i, glass);

        WarSession war = plugin.getWarManager().getActiveSession();

        buildStatusItem(war);
        buildActionItem(war);
        buildClanListItem(war);

        String closeOrBack = backAction != null ? msg.get("gui.back") : msg.get("gui.war-close");
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, closeOrBack, Collections.emptyList()));

        return inventory;
    }

    private void buildStatusItem(WarSession war) {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        if (war == null) {
            String nextTime = plugin.getWarScheduler().getNextWarTime();
            inventory.setItem(SLOT_STATUS, makeItem(gc.getWarStatusNoWarMat(),
                    msg.get("gui.war-no-active"),
                    List.of(mm.deserialize(msg.get("gui.war-next-time", "{time}", nextTime)))));
            return;
        }

        String stateStr = switch (war.getState()) {
            case REGISTRATION -> msg.get("gui.war-state-registration");
            case COUNTDOWN    -> msg.get("gui.war-state-countdown");
            case ACTIVE       -> msg.get("gui.war-state-active");
            case ENDED        -> msg.get("gui.war-state-ended");
        };

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(msg.get("gui.war-format-lore",
                "{format}", plugin.getWarConfigManager().getWarFormat().name())));
        lore.add(mm.deserialize(msg.get("gui.war-clans-lore",
                "{count}", String.valueOf(war.getRegisteredClanIds().size()))));
        if (war.isActive()) {
            lore.add(mm.deserialize(msg.get("gui.war-duration-lore",
                    "{duration}", String.valueOf(plugin.getWarConfigManager().getWarDurationMinutes()))));
        }
        lore.add(mm.deserialize(stateStr));

        inventory.setItem(SLOT_STATUS, makeItem(gc.getWarStatusActiveMat(),
                msg.get("gui.war-status-name"), lore));
    }

    private void buildActionItem(WarSession war) {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        if (war == null || war.getState() != WarSession.State.REGISTRATION) {
            inventory.setItem(SLOT_ACTION, makeItem(gc.getWarActionClosedMat(),
                    msg.get("gui.war-action-closed"),
                    List.of(mm.deserialize(msg.get("gui.war-action-lore1")))));
            return;
        }

        inventory.setItem(SLOT_ACTION, makeItem(gc.getWarActionOpenMat(),
                msg.get("gui.war-action-open"),
                List.of(
                        mm.deserialize(msg.get("gui.war-action-lore1")),
                        mm.deserialize(msg.get("gui.war-action-lore2")),
                        mm.deserialize(msg.get("gui.war-action-lore3",
                                "{min}", String.valueOf(plugin.getWarConfigManager().getMinMembersOnline())))
                )));
    }

    private void buildClanListItem(WarSession war) {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        if (war == null || war.getRegisteredClanIds().isEmpty()) {
            inventory.setItem(SLOT_CLANS, makeItem(Material.PAPER,
                    msg.get("gui.war-clans-empty"), Collections.emptyList()));
            return;
        }

        List<Component> lore = new ArrayList<>();
        for (String clanId : war.getRegisteredClanIds()) {
            Clan c = plugin.getClanManager().getClanById(clanId);
            if (c == null) continue;
            int online = plugin.getClanManager().getOnlineCount(clanId);
            lore.add(mm.deserialize(c.getColoredTag() + " <white>" + c.getName()
                    + " <dark_gray>(" + online + " online)"));
        }

        inventory.setItem(SLOT_CLANS, makeItem(Material.PAPER,
                msg.get("gui.war-clans-name", "{count}", String.valueOf(war.getRegisteredClanIds().size())),
                lore));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (slot == SLOT_CLOSE) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
            if (slot != SLOT_ACTION) return;

            player.closeInventory();

            WarSession war = plugin.getWarManager().getActiveSession();
            if (war == null || war.getState() != WarSession.State.REGISTRATION) {
                plugin.sendMessage(player, "war.register-no-war"); return;
            }

            Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
            if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }

            if (war.isClanRegistered(clan.getId())) {
                plugin.getWarManager().unregisterClan(player, clan);
            } else {
                plugin.getWarManager().registerClan(player, clan);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    /** Open directly from command (no back button). */
    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new WarGUI(plugin, null).build());
    }

    /** Open from parent menu (shows back button). */
    public static void openFromMenu(QuantumClan plugin, Player player, GUINavigation backAction) {
        player.openInventory(new WarGUI(plugin, backAction).build());
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