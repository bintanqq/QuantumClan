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
 * Contribution Shop GUI — Bug 7: back button support.
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
    private final GUINavigation backAction;
    private Inventory inventory;

    public ContributionShopGUI(QuantumClan plugin, Player viewer, int page, GUINavigation backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.viewer     = viewer;
        this.page       = Math.max(0, page);
        this.items      = plugin.getShopConfigManager().getContributionShopItems();
        this.backAction = backAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(gc.getContribShopTitle()));

        ItemStack glass = makeItem(gc.getContribShopFiller(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isItemSlot(i) && i != gc.getContribShopPointsSlot()
                    && i != gc.getContribShopCloseSlot()
                    && i != gc.getContribShopPrevSlot()
                    && i != gc.getContribShopNextSlot())
                inventory.setItem(i, glass);
        }

        setPointsDisplay();
        placeItems();
        setNavigation();

        return inventory;
    }

    private void setPointsDisplay() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        ClanMember member = plugin.getClanManager().getMember(viewer.getUniqueId());
        int points = member != null ? member.getContributionPoints() : 0;

        inventory.setItem(gc.getContribShopPointsSlot(), makeItem(gc.getContribShopPointsMat(),
                gc.getContribShopPointsName(),
                List.of(
                        mm.deserialize(msg.get("contribution.points-display",
                                "{points}", String.valueOf(points))),
                        mm.deserialize(msg.get("contribution.points-hint"))
                )));
    }

    private void placeItems() {
        var msg = plugin.getMessagesManager();
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
                lore.add(mm.deserialize("<!italic>" + line.replace("{cost}", String.valueOf(ci.getCostPoints()))));
            }
            lore.add(Component.empty());
            if (playerPoints >= ci.getCostPoints()) {
                lore.add(mm.deserialize("<!italic>" + msg.get("contribution.can-afford")));
            } else {
                lore.add(mm.deserialize("<!italic>" + msg.get("contribution.cannot-afford",
                        "{value}", String.valueOf(ci.getCostPoints() - playerPoints))));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(ITEM_SLOTS[slotIndex], item);
        }
    }

    private void setNavigation() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));

        String closeOrBack = backAction != null ? msg.get("gui.back") : msg.get("gui.close");
        inventory.setItem(gc.getContribShopCloseSlot(),
                makeItem(Material.BARRIER, closeOrBack, Collections.emptyList()));

        if (page > 0)
            inventory.setItem(gc.getContribShopPrevSlot(), makeItem(Material.ARROW,
                    msg.get("gui.prev"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page), "{total}", String.valueOf(totalPages))))));
        if (page < totalPages - 1)
            inventory.setItem(gc.getContribShopNextSlot(), makeItem(Material.ARROW,
                    msg.get("gui.next"),
                    List.of(mm.deserialize(msg.get("gui.page-info",
                            "{current}", String.valueOf(page + 2), "{total}", String.valueOf(totalPages))))));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();
            int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));

            if (slot == gc.getContribShopCloseSlot()) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
            if (slot == gc.getContribShopPrevSlot() && page > 0) { openPage(player, page - 1); return; }
            if (slot == gc.getContribShopNextSlot() && page < totalPages - 1) { openPage(player, page + 1); return; }

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
        player.openInventory(new ContributionShopGUI(plugin, player, newPage, backAction).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new ContributionShopGUI(plugin, player, 0, null).build());
    }

    public static void openFromMenu(QuantumClan plugin, Player player, GUINavigation backAction) {
        player.openInventory(new ContributionShopGUI(plugin, player, 0, backAction).build());
    }

    private boolean isItemSlot(int s) { for (int x : ITEM_SLOTS) if (x == s) return true; return false; }
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