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
 * Layout (27 slots):
 *  Slot 11 — War status info
 *  Slot 13 — Register / Unregister button
 *  Slot 15 — Registered clans list
 *  Slot 22 — Close
 */
public class WarGUI implements InventoryHolder {

    private static final int SLOT_STATUS   = 11;
    private static final int SLOT_ACTION   = 13;
    private static final int SLOT_CLANS    = 15;
    private static final int SLOT_CLOSE    = 22;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private Inventory inventory;

    public WarGUI(QuantumClan plugin) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
    }

    public Inventory build() {
        inventory = Bukkit.createInventory(this, 27,
                mm.deserialize("<dark_gray>[ <red>⚔ Clan War <dark_gray>]"));

        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 27; i++) inventory.setItem(i, glass);

        WarSession war = plugin.getWarManager().getActiveSession();

        buildStatusItem(war);
        buildActionItem(war);
        buildClanListItem(war);
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "<red>Tutup",
                Collections.emptyList()));

        return inventory;
    }

    private void buildStatusItem(WarSession war) {
        if (war == null) {
            String next = plugin.getWarScheduler().getNextWarTime();
            inventory.setItem(SLOT_STATUS, makeItem(Material.CLOCK,
                    "<gray>Tidak Ada War Aktif",
                    List.of(mm.deserialize("<gray>War berikutnya: <yellow>" + next))));
            return;
        }

        String stateStr = switch (war.getState()) {
            case REGISTRATION -> "<green>Registrasi Dibuka";
            case COUNTDOWN    -> "<yellow>Countdown...";
            case ACTIVE       -> "<red>⚔ War Berlangsung";
            case ENDED        -> "<gray>War Selesai";
        };

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Status: " + stateStr));
        lore.add(mm.deserialize("<gray>Format: <yellow>"
                + plugin.getWarConfigManager().getWarFormat().name()));
        lore.add(mm.deserialize("<gray>Clan terdaftar: <yellow>"
                + war.getRegisteredClanIds().size()));
        if (war.isActive()) {
            lore.add(mm.deserialize("<gray>Durasi: <yellow>"
                    + plugin.getWarConfigManager().getWarDurationMinutes() + " menit"));
        }

        inventory.setItem(SLOT_STATUS, makeItem(Material.IRON_SWORD, "<red>⚔ Status War", lore));
    }

    private void buildActionItem(WarSession war) {
        if (war == null || war.getState() != WarSession.State.REGISTRATION) {
            inventory.setItem(SLOT_ACTION, makeItem(Material.BARRIER,
                    "<gray>Registrasi Tutup",
                    List.of(mm.deserialize("<gray>Tidak ada war dalam periode registrasi."))));
            return;
        }

        // Check if viewer's clan is registered — need to find clan via non-player context
        // Action button is generic; actual clan-check happens on click
        inventory.setItem(SLOT_ACTION, makeItem(Material.GREEN_WOOL,
                "<green>⚔ Daftar / Keluar War",
                List.of(
                        mm.deserialize("<gray>Klik untuk mendaftar atau"),
                        mm.deserialize("<gray>membatalkan pendaftaran clan."),
                        mm.deserialize("<gray>Min. online: <yellow>"
                                + plugin.getWarConfigManager().getMinMembersOnline())
                )));
    }

    private void buildClanListItem(WarSession war) {
        if (war == null || war.getRegisteredClanIds().isEmpty()) {
            inventory.setItem(SLOT_CLANS, makeItem(Material.PAPER,
                    "<gray>Belum Ada Clan Terdaftar", Collections.emptyList()));
            return;
        }

        List<Component> lore = new ArrayList<>();
        for (String clanId : war.getRegisteredClanIds()) {
            Clan c = plugin.getClanManager().getClanById(clanId);
            if (c == null) continue;
            int online = plugin.getClanManager().getOnlineCount(clanId);
            lore.add(mm.deserialize(c.getColoredTag() + " <gray>" + c.getName()
                    + " <dark_gray>(" + online + " online)"));
        }

        inventory.setItem(SLOT_CLANS, makeItem(Material.PAPER,
                "<yellow>Clan Terdaftar (" + war.getRegisteredClanIds().size() + ")", lore));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
            if (slot != SLOT_ACTION) return;

            player.closeInventory();

            WarSession war = plugin.getWarManager().getActiveSession();
            if (war == null || war.getState() != WarSession.State.REGISTRATION) {
                plugin.sendMessage(player, "war.register-no-war"); return;
            }

            Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
            if (clan == null) {
                plugin.sendMessage(player, "clan.not-in-clan"); return;
            }

            if (war.isClanRegistered(clan.getId())) {
                // Unregister
                plugin.getWarManager().unregisterClan(player, clan);
            } else {
                // Register
                plugin.getWarManager().registerClan(player, clan);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new WarGUI(plugin).build());
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