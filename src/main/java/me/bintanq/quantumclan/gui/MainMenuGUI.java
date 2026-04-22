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
 * BUG FIX #2: Separated in-clan and not-in-clan menus completely.
 * The click handler uses a flag (isInClan) to route clicks correctly,
 * so glass filler slots in the not-in-clan view never trigger in-clan actions.
 *
 * All slot assignments come from gui.yml via GuiConfigManager.
 * All messages come from messages.yml — no hardcoded strings.
 */
public class MainMenuGUI implements InventoryHolder {

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;

    /**
     * Snapshot of clan membership at GUI open time.
     * Used to decide which layout to render AND which click actions to allow.
     * BUG FIX #2: store this as a final field so handleClick uses the same state as build().
     */
    private final boolean isInClan;
    private final String clanId; // null if not in clan

    private Inventory inventory;

    // ── Slot constants for NOT-IN-CLAN layout ─────────────────
    // These must NOT overlap with any slot used in the IN-CLAN layout
    // that comes from gui.yml. We use hardcoded center slot 13 for
    // the "Create Clan" button only when NOT in a clan.
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
        inventory    = Bukkit.createInventory(this, size, mm.deserialize(title));

        // Fill all slots with filler first
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

    private void buildInClanMenu() {
        var gc = plugin.getGuiConfigManager();
        Clan clan = clanId != null ? plugin.getClanManager().getClanById(clanId) : null;

        placeConfigItem("clan-info");
        placeConfigItem("clan-shop");
        placeConfigItem("clan-home");
        placeConfigItem("clan-upgrade");
        placeConfigItem("clan-top");
        placeConfigItem("bounty-board");
        placeConfigItem("war-menu");
        placeConfigItem("contribution-shop");
        placeConfigItem("coins-shop");

        // Disband — only show if leader
        if (clan != null && clan.getLeaderUuid().equals(viewer.getUniqueId())) {
            placeConfigItem("disband");
        }
    }

    private void buildNotInClanMenu() {
        var gc = plugin.getGuiConfigManager();

        // Create Clan button at center
        // Name and lore from messages.yml
        String createName = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-create-name");
        String lore1 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore1");
        String lore2 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore2");
        String lore3 = plugin.getMessagesManager().get("gui.main-menu-not-in-clan-lore3");

        List<Component> loreCpts = new ArrayList<>();
        if (lore1 != null && !lore1.startsWith("<red>[Missing")) loreCpts.add(mm.deserialize(lore1));
        if (lore2 != null && !lore2.startsWith("<red>[Missing")) loreCpts.add(mm.deserialize(lore2));
        if (lore3 != null && !lore3.startsWith("<red>[Missing")) {
            loreCpts.add(Component.empty());
            loreCpts.add(mm.deserialize(lore3));
        }

        inventory.setItem(SLOT_CREATE_CLAN,
                makeItem(Material.PLAYER_HEAD,
                        createName != null && !createName.startsWith("<red>[Missing")
                                ? createName : "<aqua>Create a Clan",
                        loreCpts));

        // These features are accessible even without a clan
        placeConfigItem("clan-top");
        placeConfigItem("bounty-board");
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
            // BUG FIX #2: Route clicks based on isInClan flag captured at build time.
            // This completely separates in-clan and not-in-clan click handling.
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

        // Re-fetch clan to get latest state
        Clan latestClan = clanId != null ? plugin.getClanManager().getClanById(clanId) : null;

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
        }
        // Any other slot (filler glass) — do nothing
    }

    private void handleNotInClanClick(Player player, int slot) {
        var gc = plugin.getGuiConfigManager();

        // BUG FIX #2: Only react to the specific slots we placed items on.
        // All other slots (filler glass) are intentionally ignored.

        if (slot == SLOT_CREATE_CLAN) {
            // Create clan
            player.closeInventory();
            plugin.getCommand("qclan").execute(player, "qclan", new String[]{"create"});

        } else if (slot == gc.getMainMenuSlot("clan-top")) {
            player.closeInventory();
            ClanTopGUI.open(plugin, player);

        } else if (slot == gc.getMainMenuSlot("bounty-board")) {
            player.closeInventory();
            BountyBoardGUI.open(plugin, player);

        } else if (slot == gc.getMainMenuSlot("coins-shop")) {
            player.closeInventory();
            CoinsShopGUI.open(plugin, player);
        }
        // All other slots (including in-clan feature slots) → do nothing
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