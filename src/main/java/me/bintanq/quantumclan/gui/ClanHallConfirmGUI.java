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
 * Purchase confirmation GUI for Clan Hall access.
 * Opens after clicking "Buy" in ClanHallGUI.
 */
public class ClanHallConfirmGUI implements InventoryHolder {

    private static final int SIZE      = 27;
    private static final int SLOT_YES  = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_NO   = 15;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final Clan clan;
    private final long cost;
    private Inventory inventory;

    public ClanHallConfirmGUI(QuantumClan plugin, Player viewer, Clan clan, long cost) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.viewer = viewer;
        this.clan   = clan;
        this.cost   = cost;
    }

    public Inventory build() {
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize(msg.get("hall.confirm-title")));

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        // YES
        List<Component> yesLore = new ArrayList<>();
        yesLore.add(mm.deserialize(msg.get("hall.confirm-cost",
                "{cost}", plugin.getEconomyProvider().format(cost))));
        yesLore.add(mm.deserialize(msg.get("hall.confirm-treasury",
                "{balance}", plugin.getEconomyProvider().format(clan.getMoney()))));
        boolean isPermanent = plugin.getHallConfigManager().isPermanentMode();
        int days = plugin.getHallConfigManager().getDurationDays();
        yesLore.add(mm.deserialize(msg.get("hall.confirm-duration",
                "{duration}", isPermanent
                        ? msg.get("hall.mode-permanent")
                        : msg.get("hall.mode-duration", "{days}", String.valueOf(days)))));
        inventory.setItem(SLOT_YES, makeItem(Material.LIME_WOOL,
                msg.get("hall.confirm-yes"), yesLore));

        // Info preview
        inventory.setItem(SLOT_INFO, makeItem(Material.BEACON,
                msg.get("hall.confirm-info-name"),
                List.of(mm.deserialize(msg.get("hall.confirm-info-lore",
                        "{clan}", clan.getName())))));

        // NO
        inventory.setItem(SLOT_NO, makeItem(Material.RED_WOOL,
                msg.get("hall.confirm-no"),
                List.of(mm.deserialize(msg.get("hall.confirm-no-lore")))));

        return inventory;
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            switch (slot) {
                case SLOT_YES -> {
                    player.closeInventory();
                    plugin.getClanHallManager().purchaseAccess(player)
                            .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (ok) {
                                    plugin.sendMessage(player, "hall.purchase-success",
                                            "{cost}", plugin.getEconomyProvider().format(cost));
                                } else {
                                    plugin.sendMessage(player, "hall.purchase-failed");
                                }
                            }));
                }
                case SLOT_NO -> {
                    player.closeInventory();
                    ClanHallGUI.open(plugin, player);
                }
            }
        } finally {
            processing.remove(uuid);
        }
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