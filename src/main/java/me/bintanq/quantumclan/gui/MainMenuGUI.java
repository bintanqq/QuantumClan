package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MainMenuGUI extends AbstractClanGUI {
    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Player viewer;
    private final boolean isInClan;
    private final String clanId;

    public MainMenuGUI(QuantumClan plugin, Player viewer) {
        super(plugin);
        this.viewer = viewer;
        Clan clan = plugin.getClanManager().getClanByPlayer(viewer.getUniqueId());
        this.isInClan = (clan != null);
        this.clanId = (clan != null) ? clan.getId() : null;
    }

    // ─── BUILD MENU (DINAMIS) ─────────────────────────────────────
    @Override
    public Inventory build() {
        var gc = plugin.getGuiConfigManager();

        if (!isInClan) {
            // Gunakan config "noclan-menu"
            int size = gc.getNoClanMenuSize();
            inventory = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(gc.getNoClanMenuTitle()));
            Material filler = gc.getNoClanMenuFiller();
            ItemStack glass = makeItem(filler, " ", Collections.emptyList());
            for (int i = 0; i < size; i++) inventory.setItem(i, glass);
            buildNotInClanMenu();
        } else {
            // Gunakan config "main-menu"
            int size = gc.getMainMenuSize();
            inventory = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(gc.getMainMenuTitle()));
            Material filler = gc.getMainMenuFiller();
            ItemStack glass = makeItem(filler, " ", Collections.emptyList());
            for (int i = 0; i < size; i++) inventory.setItem(i, glass);
            buildInClanMenu();
        }
        return inventory;
    }

    // ─── ISI MENU CLAN MEMBER ─────────────────────────────────────
    private void buildInClanMenu() {
        // Tambahin item config satu-satu (biar logic placeConfigItem jalan)
        placeConfigItem("clan-info");

        var cm = plugin.getConfigManager();
        if (cm.isClanShopEnabled()) placeConfigItem("clan-shop");
        if (cm.isHomesEnabled()) placeConfigItem("clan-home");
        if (cm.isLevelingEnabled()) placeConfigItem("clan-upgrade");
        if (cm.isVaultEnabled()) placeConfigItem("clan-vault");

        placeConfigItem("clan-top");
        if (cm.isBountiesEnabled()) placeConfigItem("bounty-board");
        if (cm.isWarsEnabled()) placeConfigItem("war-menu");
        if (cm.isContributionsEnabled()) placeConfigItem("contribution-shop");
        if (cm.isCoinsShopEnabled()) placeConfigItem("coins-shop");

        Clan clan = clanId != null ? plugin.getClanManager().getClanById(clanId) : null;
        if (clan != null && clan.getLeaderUuid().equals(viewer.getUniqueId())) {
            placeConfigItem("disband");
        }
    }

    // ─── ISI MENU NO-CLAN (DINAMIS DARI YAML) ─────────────────────
    private void buildNotInClanMenu() {
        ConfigurationSection items = plugin.getGuiConfigManager().getSection("noclan-menu.items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            placeNoClanConfigItem(key);
        }
    }

    // Helper untuk item Clan Menu
    private void placeConfigItem(String key) {
        var gc = plugin.getGuiConfigManager();
        int slot = gc.getMainMenuSlot(key);
        if (slot <= 0) return;
        inventory.setItem(slot, makeItem(
                gc.getMainMenuMaterial(key),
                gc.getMainMenuItemName(key),
                gc.getMainMenuItemLore(key).stream().map(MiniMessage.miniMessage()::deserialize).collect(Collectors.toList())
        ));
    }

    // Helper untuk item No-Clan Menu
    private void placeNoClanConfigItem(String key) {
        var gc = plugin.getGuiConfigManager();
        int slot = gc.getNoClanMenuSlot(key);
        if (slot < 0) return;

        Material mat = gc.getNoClanMenuMaterial(key);
        String name = gc.getNoClanMenuName(key);
        List<String> rawLore = gc.getNoClanMenuLore(key);
        List<Component> lore = new ArrayList<>();
        for (String line : rawLore) {
            line = line.replace("{cost}", String.valueOf(plugin.getConfigManager().getClanCreationCost()));
            lore.add(MiniMessage.miniMessage().deserialize(line));
        }
        inventory.setItem(slot, makeItem(mat, name, lore));
    }

    // ─── HANDLE CLICK ─────────────────────────────────────────────
    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (isInClan) handleInClanClick(player, slot);
            else handleNotInClanClick(player, slot);
        } finally {
            processing.remove(uuid);
        }
    }

    private void handleInClanClick(Player player, int slot) {
        var gc = plugin.getGuiConfigManager();
        UUID uuid = player.getUniqueId();
        Clan latestClan = clanId != null ? plugin.getClanManager().getClanById(clanId) : null;

        if (slot == gc.getMainMenuSlot("clan-info")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            player.closeInventory();
            ClanInfoGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("clan-shop")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!plugin.getClanManager().hasRolePermission(uuid, "can-access-shop")) {
                plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(uuid).getRole());
                return;
            }
            player.closeInventory();
            ClanShopGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("clan-home")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (latestClan.getHomes().isEmpty()) {
                plugin.sendMessage(player, "home.no-homes");
                return;
            }
            player.closeInventory();
            ClanHomeGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("clan-upgrade")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!plugin.getClanManager().hasRolePermission(uuid, "can-upgrade")) {
                plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(uuid).getRole());
                return;
            }
            player.closeInventory();
            UpgradeGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("clan-top")) {
            player.closeInventory();
            ClanTopGUI.openFromMenu(plugin, player, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("bounty-board")) {
            player.closeInventory();
            BountyBoardGUI.openFromMenu(plugin, player, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("war-menu")) {
            player.closeInventory();
            WarGUI.openFromMenu(plugin, player, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("contribution-shop")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            player.closeInventory();
            ContributionShopGUI.openFromMenu(plugin, player, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("coins-shop")) {
            player.closeInventory();
            CoinsShopGUI.openFromMenu(plugin, player, () -> open(plugin, player));
        } else if (slot == gc.getMainMenuSlot("clan-vault")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!plugin.getClanManager().hasRolePermission(uuid, "can-open-vault")) {
                plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(uuid).getRole());
                return;
            }
            player.closeInventory();
            plugin.getClanVaultManager().openVault(player, latestClan);
        } else if (slot == gc.getMainMenuSlot("disband")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!latestClan.getLeaderUuid().equals(uuid)) {
                plugin.sendMessage(player, "gui.disband-leader-only");
                return;
            }
            player.closeInventory();
            DisbandConfirmGUI.open(plugin, player, latestClan);
        }
    }

    // LOGIC KLIK NO-CLAN (Dinamis)
    private void handleNotInClanClick(Player player, int slot) {
        var gc = plugin.getGuiConfigManager();
        var items = gc.getSection("noclan-menu.items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            if (gc.getNoClanMenuSlot(key) == slot) {
                executeNoClanAction(player, key);
                return;
            }
        }
    }

    private void executeNoClanAction(Player player, String key) {
        player.closeInventory();
        switch (key) {
            case "create-clan":
                plugin.getCommand("qclan").execute(player, "qclan", new String[]{"create"});
                break;
            case "clan-top":
                ClanTopGUI.openFromMenu(plugin, player, () -> open(plugin, player));
                break;
            default:
                break;
        }
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new MainMenuGUI(plugin, player).build());
    }
}