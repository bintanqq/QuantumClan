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
import java.util.stream.Collectors;

/**
 * Main Menu GUI — opened when a player types /qclan with no arguments.
 * Shows shortcuts to all major features.
 * If the player is not in a clan, shows limited options (create clan, top, etc.)
 */
public class MainMenuGUI implements InventoryHolder {

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final Clan clan;
    private Inventory inventory;

    public MainMenuGUI(QuantumClan plugin, Player viewer) {
        this.plugin  = plugin;
        this.mm      = plugin.getMiniMessage();
        this.viewer  = viewer;
        this.clan    = plugin.getClanManager().getClanByPlayer(viewer.getUniqueId());
    }

    public Inventory build() {
        String title = plugin.getGuiConfigManager().getMainMenuTitle();
        int size     = plugin.getGuiConfigManager().getMainMenuSize();
        inventory    = Bukkit.createInventory(this, size, mm.deserialize(title));

        Material filler = plugin.getGuiConfigManager().getMainMenuFiller();
        ItemStack glass = makeItem(filler, " ", Collections.emptyList());
        for (int i = 0; i < size; i++) inventory.setItem(i, glass);

        if (clan != null) {
            buildInClanMenu();
        } else {
            buildNotInClanMenu();
        }

        return inventory;
    }

    private void buildInClanMenu() {
        var gc = plugin.getGuiConfigManager();

        // Clan Info
        placeConfigItem("clan-info");
        // Clan Shop
        placeConfigItem("clan-shop");
        // Clan Home
        placeConfigItem("clan-home");
        // Clan Upgrade
        placeConfigItem("clan-upgrade");
        // Clan Top
        placeConfigItem("clan-top");
        // Bounty Board
        placeConfigItem("bounty-board");
        // War Menu
        placeConfigItem("war-menu");
        // Contribution Shop
        placeConfigItem("contribution-shop");
        // Coins Shop
        placeConfigItem("coins-shop");

        // Disband — only show if leader
        if (clan.getLeaderUuid().equals(viewer.getUniqueId())) {
            placeConfigItem("disband");
        }
    }

    private void buildNotInClanMenu() {
        var gc = plugin.getGuiConfigManager();

        // Create Clan button
        int slot = gc.getMainMenuSlot("clan-info");
        Material mat = Material.PLAYER_HEAD;
        inventory.setItem(13, makeItem(mat,
                "<aqua>Create a Clan",
                List.of(
                        mm.deserialize("<white>You are not in a clan."),
                        mm.deserialize("<white>Use <aqua>/qclan create</aqua> to start one."),
                        Component.empty(),
                        mm.deserialize("<white>» Left click to create")
                )));

        // Top clans still accessible
        placeConfigItem("clan-top");
        // Bounty board still accessible
        placeConfigItem("bounty-board");
        // Coins shop still accessible
        placeConfigItem("coins-shop");
    }

    private void placeConfigItem(String key) {
        var gc = plugin.getGuiConfigManager();
        int slot = gc.getMainMenuSlot(key);
        if (slot <= 0) return;
        Material mat = gc.getMainMenuMaterial(key);
        String name  = gc.getMainMenuItemName(key);
        List<String> rawLore = gc.getMainMenuItemLore(key);
        List<Component> lore = rawLore.stream()
                .map(mm::deserialize)
                .collect(Collectors.toList());
        inventory.setItem(slot, makeItem(mat, name, lore));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();
            Clan latestClan = plugin.getClanManager().getClanByPlayer(uuid);

            if (slot == gc.getMainMenuSlot("clan-info")) {
                player.closeInventory();
                if (latestClan != null) {
                    ClanInfoGUI.open(plugin, player, latestClan);
                } else {
                    plugin.sendMessage(player, "clan.not-in-clan");
                }
            } else if (slot == gc.getMainMenuSlot("clan-shop")) {
                player.closeInventory();
                if (latestClan != null && plugin.getClanManager().hasRolePermission(uuid, "can-access-shop")) {
                    ClanShopGUI.open(plugin, player, latestClan);
                } else if (latestClan == null) {
                    plugin.sendMessage(player, "clan.not-in-clan");
                } else {
                    plugin.sendMessage(player, "error.role-no-permission",
                            "{role}", plugin.getClanManager().getMember(uuid).getRole());
                }
            } else if (slot == gc.getMainMenuSlot("clan-home")) {
                player.closeInventory();
                if (latestClan != null) {
                    if (latestClan.getHomes().isEmpty()) {
                        plugin.sendMessage(player, "home.no-homes");
                    } else {
                        ClanHomeGUI.open(plugin, player, latestClan);
                    }
                } else {
                    plugin.sendMessage(player, "clan.not-in-clan");
                }
            } else if (slot == gc.getMainMenuSlot("clan-upgrade")) {
                player.closeInventory();
                if (latestClan != null && plugin.getClanManager().hasRolePermission(uuid, "can-upgrade")) {
                    UpgradeGUI.open(plugin, player, latestClan);
                } else if (latestClan == null) {
                    plugin.sendMessage(player, "clan.not-in-clan");
                } else {
                    plugin.sendMessage(player, "error.role-no-permission",
                            "{role}", plugin.getClanManager().getMember(uuid).getRole());
                }
            } else if (slot == gc.getMainMenuSlot("clan-top")) {
                player.closeInventory();
                ClanTopGUI.open(plugin, player);
            } else if (slot == gc.getMainMenuSlot("bounty-board")) {
                player.closeInventory();
                BountyBoardGUI.open(plugin, player);
            } else if (slot == gc.getMainMenuSlot("war-menu")) {
                player.closeInventory();
                WarGUI.open(plugin, player);
            } else if (slot == gc.getMainMenuSlot("contribution-shop")) {
                player.closeInventory();
                if (latestClan != null) {
                    ContributionShopGUI.open(plugin, player);
                } else {
                    plugin.sendMessage(player, "clan.not-in-clan");
                }
            } else if (slot == gc.getMainMenuSlot("coins-shop")) {
                player.closeInventory();
                CoinsShopGUI.open(plugin, player);
            } else if (slot == gc.getMainMenuSlot("disband")) {
                player.closeInventory();
                if (latestClan != null && latestClan.getLeaderUuid().equals(uuid)) {
                    DisbandConfirmGUI.open(plugin, player, latestClan);
                } else if (latestClan == null) {
                    plugin.sendMessage(player, "clan.not-in-clan");
                } else {
                    plugin.sendMessage(player, "gui.disband-leader-only");
                }
            } else if (slot == 13 && latestClan == null) {
                // Create clan button when not in clan
                player.closeInventory();
                plugin.getCommand("qclan").execute(player, "qclan",
                        new String[]{"create"});
            }
        } finally {
            processing.remove(uuid);
        }
    }

    public static void open(QuantumClan plugin, Player player) {
        MainMenuGUI gui = new MainMenuGUI(plugin, player);
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