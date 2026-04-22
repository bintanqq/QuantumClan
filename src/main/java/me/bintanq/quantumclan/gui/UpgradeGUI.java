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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgrade GUI — shows current level stats and next level upgrade info.
 *
 * Layout (27 slots):
 *  Slot 11 — Current level info
 *  Slot 13 — UPGRADE button (green if affordable, red if not)
 *  Slot 15 — Next level preview
 *  Slot 22 — Close
 */
public class UpgradeGUI implements InventoryHolder {

    private static final int SIZE        = 27;
    private static final int SLOT_CURRENT = 11;
    private static final int SLOT_UPGRADE = 13;
    private static final int SLOT_NEXT    = 15;
    private static final int SLOT_CLOSE   = 22;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private Inventory inventory;

    public UpgradeGUI(QuantumClan plugin, Clan clan) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.clan   = clan;
    }

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <aqua>Upgrade Clan <dark_gray>]"));

        // Border
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, glass);

        int current  = clan.getLevel();
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        int next     = current + 1;

        // ── Current level ──────────────────────────────────────
        inventory.setItem(SLOT_CURRENT, makeItem(Material.EXPERIENCE_BOTTLE,
                "<aqua>Level Saat Ini: <yellow>" + current,
                List.of(
                        mm.deserialize("<gray>Max Member: <yellow>"
                                + plugin.getConfigManager().getMaxMembers(current)),
                        mm.deserialize("<gray>Max Home: <yellow>"
                                + plugin.getConfigManager().getMaxHomes(current)),
                        mm.deserialize("<gray>Kas Clan: <gold>" + clan.getMoney())
                )));

        // ── Upgrade button ─────────────────────────────────────
        if (current >= maxLevel) {
            inventory.setItem(SLOT_UPGRADE, makeItem(Material.BARRIER,
                    "<red>Level Maksimum Tercapai",
                    List.of(mm.deserialize("<gray>Clan sudah di level tertinggi."))));
        } else {
            long cost        = plugin.getConfigManager().getLevelCost(next);
            boolean canAfford = clan.hasMoney(cost);
            Material mat     = canAfford ? Material.LIME_WOOL : Material.RED_WOOL;
            String status    = canAfford ? "<green>✔ UPGRADE" : "<red>✘ KAS TIDAK CUKUP";

            List<Component> upgradeLore = new java.util.ArrayList<>();
            upgradeLore.add(mm.deserialize("<gray>Biaya: <gold>" + cost + " Clan Money"));
            upgradeLore.add(mm.deserialize("<gray>Kas saat ini: <gold>" + clan.getMoney()));
            if (!canAfford) {
                upgradeLore.add(mm.deserialize("<red>Kurang: <yellow>" + (cost - clan.getMoney())));
            }
            upgradeLore.add(Component.empty());
            upgradeLore.add(mm.deserialize(canAfford
                    ? "<green>Klik untuk upgrade!"
                    : "<red>Isi kas clan terlebih dahulu."));

            inventory.setItem(SLOT_UPGRADE, makeItem(mat, status, upgradeLore));
        }

        // ── Next level preview ─────────────────────────────────
        if (current < maxLevel) {
            inventory.setItem(SLOT_NEXT, makeItem(Material.BEACON,
                    "<gold>Preview Level " + next,
                    List.of(
                            mm.deserialize("<gray>Max Member: <yellow>"
                                    + plugin.getConfigManager().getMaxMembers(next)),
                            mm.deserialize("<gray>Max Home: <yellow>"
                                    + plugin.getConfigManager().getMaxHomes(next)),
                            mm.deserialize("<gray>Biaya Naik: <gold>"
                                    + plugin.getConfigManager().getLevelCost(next))
                    )));
        }

        // Close
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, "<red>Tutup",
                Collections.emptyList()));

        return inventory;
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
            if (slot != SLOT_UPGRADE) return;

            Clan latest = plugin.getClanManager().getClanById(clan.getId());
            if (latest == null) { player.closeInventory(); return; }

            if (latest.getLevel() >= plugin.getConfigManager().getMaxLevel()) {
                plugin.sendMessage(player, "clan.upgrade-max"); return;
            }

            player.closeInventory();
            plugin.getClanManager().upgradeClan(latest.getId())
                    .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (ok) {
                            plugin.sendMessage(player, "clan.upgrade-success",
                                    "{value}", String.valueOf(latest.getLevel() + 1));
                        } else {
                            long cost = plugin.getConfigManager()
                                    .getLevelCost(latest.getLevel() + 1);
                            long deficit = cost - latest.getMoney();
                            plugin.sendMessage(player, "clan.upgrade-insufficient",
                                    "{value}", String.valueOf(deficit));
                        }
                    }));
        } finally {
            processing.remove(uuid);
        }
    }

    public static void open(QuantumClan plugin, Player player, Clan clan) {
        player.openInventory(new UpgradeGUI(plugin, clan).build());
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