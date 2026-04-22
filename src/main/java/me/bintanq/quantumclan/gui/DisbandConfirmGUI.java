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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Confirmation GUI before disbanding a clan.
 * If disband.refund-treasury is true in config, refunds the clan treasury to the leader.
 * If disband.refund-creation-cost is true, also refunds the creation cost.
 */
public class DisbandConfirmGUI implements InventoryHolder {

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private Inventory inventory;

    public DisbandConfirmGUI(QuantumClan plugin, Clan clan) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.clan   = clan;
    }

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        int size = gc.getDisbandConfirmSize();
        inventory = Bukkit.createInventory(this, size, mm.deserialize(gc.getDisbandConfirmTitle()));

        // Fill border
        ItemStack filler = makeItem(gc.getDisbandConfirmFiller(), " ", List.of());
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Confirm (YES) button
        List<String> rawYesLore = gc.getDisbandConfirmYesLore();
        List<Component> yesLore = rawYesLore.stream().map(mm::deserialize).collect(Collectors.toList());

        // Add refund info to lore dynamically
        if (plugin.getConfigManager().isDisbandRefundTreasury() && clan.getMoney() > 0) {
            yesLore = new java.util.ArrayList<>(yesLore);
            yesLore.add(Component.empty());
            yesLore.add(mm.deserialize("<aqua>Treasury refund: <white>" +
                    plugin.getEconomyProvider().format(clan.getMoney())));
        }
        if (plugin.getConfigManager().isDisbandRefundCreationCost()) {
            double cost = plugin.getConfigManager().getClanCreationCost();
            if (cost > 0) {
                yesLore = new java.util.ArrayList<>(yesLore);
                yesLore.add(mm.deserialize("<aqua>Creation cost refund: <white>" +
                        plugin.getEconomyProvider().format(cost)));
            }
        }

        inventory.setItem(gc.getDisbandConfirmYesSlot(),
                makeItem(gc.getDisbandConfirmYesMat(), gc.getDisbandConfirmYesName(), yesLore));

        // Cancel (NO) button
        inventory.setItem(gc.getDisbandConfirmNoSlot(),
                makeItem(gc.getDisbandConfirmNoMat(), gc.getDisbandConfirmNoName(),
                        List.of(mm.deserialize("<white>Keep your clan."))));

        return inventory;
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();

            if (slot == gc.getDisbandConfirmYesSlot()) {
                player.closeInventory();
                processDisbandConfirm(player);
            } else if (slot == gc.getDisbandConfirmNoSlot()) {
                player.closeInventory();
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void processDisbandConfirm(Player player) {
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        if (latest == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        if (!latest.getLeaderUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "gui.disband-leader-only");
            return;
        }

        String clanName = latest.getName();
        long treasury   = latest.getMoney();

        plugin.getClanManager().disbandClan(latest.getId()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) return;

                    plugin.sendMessage(player, "clan.disband-success", "{clan}", clanName);
                    plugin.broadcast(plugin.getMessagesManager().get("clan.disband-broadcast",
                            "{clan}", clanName));

                    // Refund treasury to leader
                    if (plugin.getConfigManager().isDisbandRefundTreasury() && treasury > 0) {
                        plugin.getEconomyProvider().deposit(player, treasury);
                        plugin.sendMessage(player, "clan.disband-refunded",
                                "{value}", plugin.getEconomyProvider().format(treasury));
                    }

                    // Refund creation cost
                    if (plugin.getConfigManager().isDisbandRefundCreationCost()) {
                        double cost = plugin.getConfigManager().getClanCreationCost();
                        if (cost > 0) {
                            plugin.getEconomyProvider().deposit(player, cost);
                            plugin.sendMessage(player, "clan.disband-refunded-creation",
                                    "{value}", plugin.getEconomyProvider().format(cost));
                        }
                    }
                }));
    }

    public static void open(QuantumClan plugin, Player player, Clan clan) {
        DisbandConfirmGUI gui = new DisbandConfirmGUI(plugin, clan);
        player.openInventory(gui.build());
    }

    @Override
    public Inventory getInventory() { return inventory; }

    private ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) {
            List<Component> noItalic = lore.stream()
                    .map(c -> Component.empty().append(c)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .collect(Collectors.toList());
            meta.lore(noItalic);
        }
        item.setItemMeta(meta);
        return item;
    }
}