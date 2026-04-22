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
 * Clan Hall information and purchase GUI.
 *
 * Layout (27 slots):
 *  Slot 11 — Hall Info (status, access, duration)
 *  Slot 13 — Purchase / Already Owned button
 *  Slot 15 — Teleport button (if clan has access)
 *  Slot 22 — Close
 *
 * All text sourced from messages.yml — zero hardcoded strings.
 */
public class ClanHallGUI implements InventoryHolder {

    private static final int SIZE          = 27;
    private static final int SLOT_INFO     = 11;
    private static final int SLOT_ACTION   = 13;
    private static final int SLOT_TP       = 15;
    private static final int SLOT_CLOSE    = 22;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private Inventory inventory;

    public ClanHallGUI(QuantumClan plugin, Player viewer) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.viewer = viewer;
    }

    public Inventory build() {
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize(msg.get("hall.gui-title")));

        // Filler
        ItemStack filler = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        Clan clan = plugin.getClanManager().getClanByPlayer(viewer.getUniqueId());
        boolean hasAccess = clan != null && plugin.getClanHallManager().hasAccess(clan.getId());
        long cost = plugin.getHallConfigManager().getPurchaseCost();
        boolean isPermanent = plugin.getHallConfigManager().isPermanentMode();
        int durationDays = plugin.getHallConfigManager().getDurationDays();
        int discountPct = hasAccess ? 0 :
                plugin.getClanHallManager().getDiscountPercent(viewer, "clan-shop"); // not applicable for hall itself

        // ── Hall Info ────────────────────────────────────────
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(mm.deserialize(msg.get("hall.gui-info-access",
                "{status}", hasAccess
                        ? msg.get("hall.gui-access-yes")
                        : msg.get("hall.gui-access-no"))));
        infoLore.add(mm.deserialize(msg.get("hall.gui-info-cost",
                "{cost}", plugin.getEconomyProvider().format(cost))));
        infoLore.add(mm.deserialize(msg.get("hall.gui-info-mode",
                "{mode}", isPermanent ? msg.get("hall.mode-permanent") : msg.get("hall.mode-duration",
                        "{days}", String.valueOf(durationDays)))));
        if (clan != null) {
            infoLore.add(mm.deserialize(msg.get("hall.gui-info-treasury",
                    "{balance}", plugin.getEconomyProvider().format(clan.getMoney()))));
        }
        inventory.setItem(SLOT_INFO, makeItem(Material.BEACON,
                msg.get("hall.gui-info-name"), infoLore));

        // ── Action button ─────────────────────────────────────
        if (hasAccess) {
            List<Component> ownedLore = List.of(
                    mm.deserialize(msg.get("hall.gui-owned-lore")));
            inventory.setItem(SLOT_ACTION, makeItem(Material.LIME_WOOL,
                    msg.get("hall.gui-owned-name"), ownedLore));
        } else {
            boolean canAfford = clan != null && clan.hasMoney(cost);
            List<Component> buyLore = new ArrayList<>();
            buyLore.add(mm.deserialize(msg.get("hall.gui-buy-cost",
                    "{cost}", plugin.getEconomyProvider().format(cost))));
            if (!canAfford) {
                buyLore.add(mm.deserialize(msg.get("hall.gui-buy-insufficient")));
            } else {
                buyLore.add(mm.deserialize(msg.get("hall.gui-buy-click")));
            }
            Material mat = canAfford ? Material.GREEN_WOOL : Material.RED_WOOL;
            inventory.setItem(SLOT_ACTION, makeItem(mat,
                    msg.get("hall.gui-buy-name"), buyLore));
        }

        // ── Teleport button ───────────────────────────────────
        if (hasAccess) {
            List<Component> tpLore = List.of(
                    mm.deserialize(msg.get("hall.gui-tp-lore")));
            inventory.setItem(SLOT_TP, makeItem(Material.ENDER_PEARL,
                    msg.get("hall.gui-tp-name"), tpLore));
        }

        // ── Close ────────────────────────────────────────────
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER,
                msg.get("gui.close"), Collections.emptyList()));

        return inventory;
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            switch (slot) {
                case SLOT_CLOSE -> player.closeInventory();

                case SLOT_ACTION -> {
                    Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
                    if (clan == null) {
                        plugin.sendMessage(player, "clan.not-in-clan");
                        player.closeInventory();
                        return;
                    }
                    if (plugin.getClanHallManager().hasAccess(clan.getId())) {
                        plugin.sendMessage(player, "hall.already-has-access");
                        player.closeInventory();
                        return;
                    }
                    // Open purchase confirmation
                    player.closeInventory();
                    openPurchaseConfirm(player, clan);
                }

                case SLOT_TP -> {
                    player.closeInventory();
                    Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
                    if (clan == null || !plugin.getClanHallManager().hasAccess(clan.getId())) {
                        plugin.sendMessage(player, "hall.no-access");
                        return;
                    }
                    // War check
                    var war = plugin.getWarManager().getActiveSession();
                    if (war != null && war.isActive()
                            && war.isMemberParticipating(uuid) && war.isMemberAlive(uuid)) {
                        plugin.sendMessage(player, "hall.war-blocked");
                        return;
                    }
                    var loc = plugin.getHallConfigManager().getTeleportLocation();
                    if (loc == null) {
                        plugin.sendMessage(player, "hall.region-not-set");
                        return;
                    }
                    player.teleport(loc);
                    plugin.sendMessage(player, "hall.teleported");
                }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPurchaseConfirm(Player player, Clan clan) {
        long cost = plugin.getHallConfigManager().getPurchaseCost();
        // Open a simple confirm GUI
        ClanHallConfirmGUI confirm = new ClanHallConfirmGUI(plugin, player, clan, cost);
        player.openInventory(confirm.build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new ClanHallGUI(plugin, player).build());
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