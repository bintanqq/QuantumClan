package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ContribItem;
import me.bintanq.quantumclan.model.ClanMember;
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
 * Contribution Shop GUI — exchange Contribution Points for personal rewards.
 *
 * Layout (54 slots):
 *  Slot  4 — Player's current contribution points
 *  Slots 10-16, 19-25, 28-34 — contribution shop items
 *  Slot 49 — Close
 *  Slot 45 — Prev / Slot 53 — Next
 */
public class ContributionShopGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final int page;
    private final List<ContribItem> items;
    private Inventory inventory;

    public ContributionShopGUI(QuantumClan plugin, Player viewer, int page) {
        this.plugin  = plugin;
        this.mm      = plugin.getMiniMessage();
        this.viewer  = viewer;
        this.page    = Math.max(0, page);
        this.items   = plugin.getShopConfigManager().getContributionShopItems();
    }

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <aqua>Contribution Shop <dark_gray>]"));

        fillBorder();
        setPointsDisplay();
        placeItems();
        setNavigation();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isItemSlot(i) && i != 4 && i != 45 && i != 49 && i != 53)
                inventory.setItem(i, glass);
        }
    }

    private void setPointsDisplay() {
        ClanMember member = plugin.getClanManager().getMember(viewer.getUniqueId());
        int points = member != null ? member.getContributionPoints() : 0;

        inventory.setItem(4, makeItem(Material.NETHER_STAR,
                "<aqua>Contribution Points Kamu",
                List.of(
                        mm.deserialize("<yellow>" + points + " <aqua>Poin"),
                        mm.deserialize("<gray>Dapatkan poin dari deposit, bounty, dan war.")
                )));
    }

    private void placeItems() {
        ClanMember member = plugin.getClanManager().getMember(viewer.getUniqueId());
        int playerPoints  = member != null ? member.getContributionPoints() : 0;

        int start = page * ITEM_SLOTS.length;
        int end   = Math.min(start + ITEM_SLOTS.length, items.size());

        for (int i = start; i < end; i++) {
            ContribItem ci = items.get(i);
            int slotIndex  = i - start;

            ItemStack item = new ItemStack(ci.getMaterial());
            ItemMeta meta  = item.getItemMeta();
            if (meta == null) continue;

            meta.displayName(mm.deserialize("<!italic>" + ci.getName()));

            List<Component> lore = new ArrayList<>();
            for (String line : ci.getLore()) {
                String resolved = line
                        .replace("{cost}", String.valueOf(ci.getCostPoints()));
                lore.add(mm.deserialize("<!italic>" + resolved));
            }
            lore.add(Component.empty());
            if (playerPoints >= ci.getCostPoints()) {
                lore.add(mm.deserialize("<!italic><green>✔ Poin cukup"));
            } else {
                lore.add(mm.deserialize("<!italic><red>✘ Poin kurang <yellow>"
                        + (ci.getCostPoints() - playerPoints)));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(ITEM_SLOTS[slotIndex], item);
        }
    }

    private void setNavigation() {
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));
        if (page > 0)
            inventory.setItem(45, makeItem(Material.ARROW, "<yellow>« Sebelumnya",
                    List.of(mm.deserialize("<gray>Hal. " + page + "/" + totalPages))));
        if (page < totalPages - 1)
            inventory.setItem(53, makeItem(Material.ARROW, "<yellow>Berikutnya »",
                    List.of(mm.deserialize("<gray>Hal. " + (page + 2) + "/" + totalPages))));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            int totalPages = Math.max(1,
                    (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));

            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { openPage(player, page - 1); return; }
            if (slot == 53 && page < totalPages - 1) { openPage(player, page + 1); return; }

            // Find clicked contrib item
            for (int i = 0; i < ITEM_SLOTS.length; i++) {
                if (ITEM_SLOTS[i] == slot) {
                    int itemIndex = page * ITEM_SLOTS.length + i;
                    if (itemIndex >= items.size()) return;
                    ContribItem ci = items.get(itemIndex);
                    player.closeInventory();
                    plugin.getContributionManager().purchase(player, ci);
                    return;
                }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        player.openInventory(new ContributionShopGUI(plugin, player, newPage).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new ContributionShopGUI(plugin, player, 0).build());
    }

    private boolean isItemSlot(int s) {
        for (int x : ITEM_SLOTS) if (x == s) return true;
        return false;
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