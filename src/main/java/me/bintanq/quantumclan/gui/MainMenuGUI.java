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
 *
 * BUG FIX #6: When an action fails (e.g. no homes), the GUI no longer closes —
 * the error message is sent and the player stays in the GUI.
 *
 * BUG FIX #7: Sub-GUIs opened FROM the main menu receive a back-button
 * supplier so they can return here instead of just closing.
 *
 * All text from messages.yml — no hardcoded strings.
 */
public class MainMenuGUI implements InventoryHolder {

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;

    private final boolean isInClan;
    private final String clanId;

    // Slot for the "Create Clan" button when not in a clan
    private static final int SLOT_CREATE_CLAN = 13;

    public MainMenuGUI(QuantumClan plugin, Player viewer) {
        this.plugin    = plugin;
        this.mm        = plugin.getMiniMessage();
        this.viewer    = viewer;
        Clan clan = plugin.getClanManager().getClanByPlayer(viewer.getUniqueId());
        this.isInClan  = (clan != null);
        this.clanId    = (clan != null) ? clan.getId() : null;
    }

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        String title = gc.getMainMenuTitle();
        int size     = gc.getMainMenuSize();
        Inventory inventory = Bukkit.createInventory(this, size, mm.deserialize(title));
        this.inventory = inventory;

        Material filler = gc.getMainMenuFiller();
        ItemStack glass = makeItem(filler, " ", Collections.emptyList());
        for (int i = 0; i < size; i++) inventory.setItem(i, glass);

        if (isInClan) {
            buildInClanMenu();
        } else {
            buildNotInClanMenu();
        }

        return inventory;
    }

    private Inventory inventory;

    private void buildInClanMenu() {
        var gc = plugin.getGuiConfigManager();
        Clan clan = clanId != null ? plugin.getClanManager().getClanById(clanId) : null;

        placeConfigItem("clan-info");
        if (plugin.getConfigManager().isClanShopEnabled()) placeConfigItem("clan-shop");
        if (plugin.getConfigManager().isHomesEnabled())    placeConfigItem("clan-home");
        if (plugin.getConfigManager().isLevelingEnabled()) placeConfigItem("clan-upgrade");
        placeConfigItem("clan-top");
        if (plugin.getConfigManager().isBountiesEnabled())     placeConfigItem("bounty-board");
        if (plugin.getConfigManager().isWarsEnabled())         placeConfigItem("war-menu");
        if (plugin.getConfigManager().isContributionsEnabled()) placeConfigItem("contribution-shop");
        if (plugin.getConfigManager().isCoinsShopEnabled())    placeConfigItem("coins-shop");

        if (clan != null && clan.getLeaderUuid().equals(viewer.getUniqueId())) {
            placeConfigItem("disband");
        }
    }

    private void buildNotInClanMenu() {
        String createName = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-create-name");
        String lore1 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore1");
        String lore2 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore2");
        String lore3 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore3");

        List<Component> loreCpts = new ArrayList<>();
        if (!lore1.startsWith("<red>[Missing")) loreCpts.add(mm.deserialize(lore1));
        if (!lore2.startsWith("<red>[Missing")) loreCpts.add(mm.deserialize(lore2));
        if (!lore3.startsWith("<red>[Missing")) { loreCpts.add(Component.empty()); loreCpts.add(mm.deserialize(lore3)); }

        inventory.setItem(SLOT_CREATE_CLAN, makeItem(Material.PLAYER_HEAD,
                !createName.startsWith("<red>[Missing") ? createName : "<aqua>Create a Clan",
                loreCpts));

        placeConfigItem("clan-top");
        if (plugin.getConfigManager().isBountiesEnabled())  placeConfigItem("bounty-board");
        if (plugin.getConfigManager().isCoinsShopEnabled()) placeConfigItem("coins-shop");
    }

    private void placeConfigItem(String key) {
        var gc = plugin.getGuiConfigManager();
        int slot = gc.getMainMenuSlot(key);
        if (slot <= 0) return;
        Material mat = gc.getMainMenuMaterial(key);
        String name  = gc.getMainMenuItemName(key);
        List<String> rawLore = gc.getMainMenuItemLore(key);
        List<Component> lore = rawLore.stream().map(mm::deserialize).collect(Collectors.toList());
        inventory.setItem(slot, makeItem(mat, name, lore));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (isInClan) {
                handleInClanClick(player, slot);
            } else {
                handleNotInClanClick(player, slot);
            }
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
            // BUG FIX #7: Pass back-supplier so ClanInfoGUI can return to main menu
            ClanInfoGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));

        } else if (slot == gc.getMainMenuSlot("clan-shop")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!plugin.getClanManager().hasRolePermission(uuid, "can-access-shop")) {
                plugin.sendMessage(player, "error.role-no-permission",
                        "{role}", plugin.getClanManager().getMember(uuid).getRole());
                return;
            }
            player.closeInventory();
            ClanShopGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));

        } else if (slot == gc.getMainMenuSlot("clan-home")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            // BUG FIX #6: Don't close if no homes — send message and stay in GUI
            if (latestClan.getHomes().isEmpty()) {
                plugin.sendMessage(player, "home.no-homes");
                return; // Stay in main menu
            }
            player.closeInventory();
            ClanHomeGUI.openFromMenu(plugin, player, latestClan, () -> open(plugin, player));

        } else if (slot == gc.getMainMenuSlot("clan-upgrade")) {
            if (latestClan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
            if (!plugin.getClanManager().hasRolePermission(uuid, "can-upgrade")) {
                plugin.sendMessage(player, "error.role-no-permission",
                        "{role}", plugin.getClanManager().getMember(uuid).getRole());
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

    private void handleNotInClanClick(Player player, int slot) {
        var gc = plugin.getGuiConfigManager();

        if (slot == SLOT_CREATE_CLAN) {
            player.closeInventory();
            plugin.getCommand("qclan").execute(player, "qclan", new String[]{"create"});

        } else if (slot == gc.getMainMenuSlot("clan-top")) {
            player.closeInventory();
            ClanTopGUI.openFromMenu(plugin, player, () -> open(plugin, player));

        } else if (slot == gc.getMainMenuSlot("bounty-board")) {
            player.closeInventory();
            BountyBoardGUI.openFromMenu(plugin, player, () -> open(plugin, player));

        } else if (slot == gc.getMainMenuSlot("coins-shop")) {
            player.closeInventory();
            CoinsShopGUI.openFromMenu(plugin, player, () -> open(plugin, player));
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