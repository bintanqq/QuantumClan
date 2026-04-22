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
 * Upgrade GUI — shows current level stats and next level upgrade info.
 *
 * ADDITION: backAction field + openFromMenu() static method.
 * When backAction is non-null, closing the GUI calls backAction.run() instead
 * of player.closeInventory() — useful for returning to a parent menu.
 */
public class UpgradeGUI extends AbstractClanGUI {

    private static final int SIZE         = 27;
    private static final int SLOT_CURRENT = 11;
    private static final int SLOT_UPGRADE = 13;
    private static final int SLOT_NEXT    = 15;
    private static final int SLOT_CLOSE   = 22;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final Clan         clan;
    private final GUINavigation backAction; // null = just close inventory

    // ── Constructors ────────────────────────────────────────────────────────

    /** Full constructor with backAction support. */
    public UpgradeGUI(QuantumClan plugin, Clan clan, GUINavigation backAction) {
        super(plugin);
        this.clan       = clan;
        this.backAction = backAction;
    }

    /** Backward-compatible constructor (no back navigation). */
    public UpgradeGUI(QuantumClan plugin, Clan clan) {
        this(plugin, clan, null);
    }

    // ── Build ────────────────────────────────────────────────────────────────

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(gc.getUpgradeTitle()));

        // Border
        ItemStack glass = makeItem(gc.getUpgradeFiller(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, glass);

        int current  = clan.getLevel();
        int maxLevel = plugin.getConfigManager().getMaxLevel();
        int next     = current + 1;

        // ── Current level ──────────────────────────────────────
        List<Component> currentLore = new ArrayList<>();
        currentLore.add(mm.deserialize(plugin.getMessagesManager().get(
                "gui.upgrade-current-max-members", "{members}", String.valueOf(plugin.getConfigManager().getMaxMembers(current)))));
        currentLore.add(mm.deserialize(plugin.getMessagesManager().get(
                "gui.upgrade-current-max-homes", "{homes}", String.valueOf(plugin.getConfigManager().getMaxHomes(current)))));
        currentLore.add(mm.deserialize(plugin.getMessagesManager().get(
                "gui.upgrade-current-treasury", "{money}", plugin.getEconomyProvider().format(clan.getMoney()))));

        String currentName = plugin.getMessagesManager().get("gui.upgrade-current-name",
                "{level}", String.valueOf(current));
        inventory.setItem(SLOT_CURRENT, makeItem(gc.getUpgradeCurrentMat(), currentName, currentLore));

        // ── Upgrade button ─────────────────────────────────────
        if (current >= maxLevel) {
            List<Component> maxLore = List.of(mm.deserialize(plugin.getMessagesManager().get("gui.upgrade-max-lore2")));
            inventory.setItem(SLOT_UPGRADE, makeItem(gc.getUpgradeMaxMat(), gc.getUpgradeMaxName(), maxLore));
        } else {
            long cost         = plugin.getConfigManager().getLevelCost(next);
            boolean canAfford = clan.hasMoney(cost);
            Material mat      = canAfford ? gc.getUpgradeCanMat() : gc.getUpgradeCannotMat();
            String btnName    = canAfford ? gc.getUpgradeCanName() : gc.getUpgradeCannotName();

            List<Component> upgradeLore = new ArrayList<>();
            upgradeLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    "gui.upgrade-cost-lore", "{cost}", plugin.getEconomyProvider().format(cost))));
            upgradeLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    "gui.upgrade-current-treasury-lore", "{money}", plugin.getEconomyProvider().format(clan.getMoney()))));
            if (!canAfford) {
                long deficit = cost - clan.getMoney();
                upgradeLore.add(mm.deserialize(plugin.getMessagesManager().get(
                        "gui.upgrade-shortage-lore", "{amount}", plugin.getEconomyProvider().format(deficit))));
            }
            upgradeLore.add(Component.empty());
            upgradeLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    canAfford ? "gui.upgrade-click-lore" : "gui.upgrade-fill-lore")));

            inventory.setItem(SLOT_UPGRADE, makeItem(mat, btnName, upgradeLore));
        }

        // ── Next level preview ─────────────────────────────────
        if (current < maxLevel) {
            List<Component> nextLore = new ArrayList<>();
            nextLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    "gui.upgrade-preview-members", "{members}", String.valueOf(plugin.getConfigManager().getMaxMembers(next)))));
            nextLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    "gui.upgrade-preview-homes", "{homes}", String.valueOf(plugin.getConfigManager().getMaxHomes(next)))));
            nextLore.add(mm.deserialize(plugin.getMessagesManager().get(
                    "gui.upgrade-preview-cost", "{cost}", plugin.getEconomyProvider().format(plugin.getConfigManager().getLevelCost(next)))));

            String nextName = plugin.getMessagesManager().get("gui.upgrade-next-name",
                    "{level}", String.valueOf(next));
            inventory.setItem(SLOT_NEXT, makeItem(gc.getUpgradeNextMat(), nextName, nextLore));
        }

        // ── Close / Back button ────────────────────────────────
        String closeName = plugin.getMessagesManager().get("gui.upgrade-close");
        inventory.setItem(SLOT_CLOSE, makeItem(Material.BARRIER, closeName, Collections.emptyList()));

        return inventory;
    }

    // ── Click handler ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            if (slot == SLOT_CLOSE) {
                // If a backAction is registered, delegate to it (e.g. reopen parent menu).
                // Otherwise just close the inventory normally.
                if (backAction != null) backAction.navigate();
                else player.closeInventory();
                return;
            }

            if (slot != SLOT_UPGRADE) return;

            Clan latest = plugin.getClanManager().getClanById(clan.getId());
            if (latest == null) { player.closeInventory(); return; }

            if (latest.getLevel() >= plugin.getConfigManager().getMaxLevel()) {
                plugin.sendMessage(player, "clan.upgrade-max"); return;
            }

            // Capture the intended new level BEFORE the async call.
            // upgradeClan() mutates latest.level in memory, so after the call
            // latest.getLevel() is already the new level. Capturing newLevel here
            // gives us the correct value to display in the success message.
            final int newLevel = latest.getLevel() + 1;

            player.closeInventory();
            plugin.getClanManager().upgradeClan(latest.getId())
                    .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (ok) {
                            plugin.sendMessage(player, "clan.upgrade-success",
                                     "{value}", String.valueOf(newLevel));
                        } else {
                            long cost    = plugin.getConfigManager().getLevelCost(newLevel);
                            long deficit = cost - latest.getMoney();
                            plugin.sendMessage(player, "clan.upgrade-insufficient",
                                    "{value}", plugin.getEconomyProvider().format(deficit));
                        }
                    }));
        } finally {
            processing.remove(uuid);
        }
    }

    // ── Static openers ───────────────────────────────────────────────────────

    /**
     * Opens the GUI without any back-navigation (original behaviour).
     */
    public static void open(QuantumClan plugin, Player player, Clan clan) {
        player.openInventory(new UpgradeGUI(plugin, clan).build());
    }

    /**
     * Opens the GUI from a parent menu.
     * Pressing the close/back button will invoke {@code backAction} instead of
     * simply closing the inventory, allowing seamless return to the caller.
     *
     * <pre>{@code
     * // Example usage from ClanMainGUI:
     * UpgradeGUI.openFromMenu(plugin, player, clan,
     *         () -> ClanMainGUI.open(plugin, player, clan));
     * }</pre>
     */
    public static void openFromMenu(QuantumClan plugin, Player player, Clan clan, GUINavigation backAction) {
        player.openInventory(new UpgradeGUI(plugin, clan, backAction).build());
    }

}