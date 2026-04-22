package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ShopItem;
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
 * Clan Shop GUI.
 */
public class ClanShopGUI extends AbstractClanGUI {

    private static final int SIZE = 54;
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final Clan clan;
    private final Player viewer;
    private final int page;
    private final List<ShopItem> items;
    private final GUINavigation backAction;

    public ClanShopGUI(QuantumClan plugin, Player viewer, Clan clan, int page, GUINavigation backAction) {
        super(plugin);
        this.viewer     = viewer;
        this.clan       = clan;
        this.page       = Math.max(0, page);
        this.items      = plugin.getShopConfigManager().getClanShopItems();
        this.backAction = backAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(gc.getClanShopTitle()));

        ItemStack glass = makeItem(gc.getClanShopFiller(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            boolean isItemSlot = false;
            for (int s : ITEM_SLOTS) if (s == i) { isItemSlot = true; break; }
            if (!isItemSlot) inventory.setItem(i, glass);
        }

        placeItems();
        setNavigation();
        setTreasuryDisplay();

        return inventory;
    }

    private void placeItems() {
        int start = page * ITEM_SLOTS.length;
        int end   = Math.min(start + ITEM_SLOTS.length, items.size());

        for (int i = start; i < end; i++) {
            ShopItem shopItem = items.get(i);
            int slotIndex     = i - start;
            inventory.setItem(ITEM_SLOTS[slotIndex], buildShopItem(shopItem));
        }
    }

    private ItemStack buildShopItem(ShopItem shopItem) {
        var msg = plugin.getMessagesManager();
        ItemStack item = new ItemStack(shopItem.getMaterial());
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + shopItem.getName()));

        List<Component> lore = new ArrayList<>();
        for (String line : shopItem.getLore()) {
            String resolved = line
                    .replace("{price}", String.valueOf(shopItem.getPrice()))
                    .replace("{cost}", String.valueOf(shopItem.getPrice()));
            lore.add(mm.deserialize("<!italic>" + resolved));
        }

        lore.add(Component.empty());
        if (clan.hasMoney(shopItem.getPrice())) {
            lore.add(mm.deserialize("<!italic>" + msg.get("shop.can-afford")));
        } else {
            long deficit = shopItem.getPrice() - clan.getMoney();
            lore.add(mm.deserialize("<!italic>" + msg.get("shop.cannot-afford",
                    "{value}", plugin.getEconomyProvider().format(deficit))));
        }
        lore.add(mm.deserialize("<!italic>" + msg.get("shop.type-label",
                "{type}", shopItem.getType().name())));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setNavigation() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        int totalPages = getTotalPages();

        String closeName = backAction != null ? msg.get("gui.back") : msg.get("gui.close");
        inventory.setItem(gc.getClanShopCloseSlot(),
                makeItem(gc.getClanShopCloseMat(), closeName, Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getClanShopPrevSlot(),
                    makeItem(Material.ARROW, msg.get("gui.prev"),
                            List.of(mm.deserialize(msg.get("gui.page-info",
                                    "{current}", String.valueOf(page),
                                    "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getClanShopNextSlot(),
                    makeItem(Material.ARROW, msg.get("gui.next"),
                            List.of(mm.deserialize(msg.get("gui.page-info",
                                    "{current}", String.valueOf(page + 2),
                                    "{total}", String.valueOf(totalPages))))));
        }
    }

    private void setTreasuryDisplay() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        // Treasury display shows NEUTRAL info only.
        // "cannot-afford" used to leak here as lore. Now we show the balance
        // as the item name and a neutral description as lore.
        String treasuryName = gc.getClanShopTreasuryName()
                .replace("{money}", plugin.getEconomyProvider().format(clan.getMoney()));

        // Neutral lore: just explain what the treasury is
        List<Component> lore = List.of(
                mm.deserialize(msg.get("shop.treasury-lore",
                        "{money}", plugin.getEconomyProvider().format(clan.getMoney())))
        );

        inventory.setItem(gc.getClanShopTreasurySlot(),
                makeItem(gc.getClanShopTreasuryMat(), treasuryName, lore));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            var gc = plugin.getGuiConfigManager();

            if (slot == gc.getClanShopCloseSlot()) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
            if (slot == gc.getClanShopPrevSlot() && page > 0) { openPage(player, page - 1); return; }
            if (slot == gc.getClanShopNextSlot() && page < getTotalPages() - 1) { openPage(player, page + 1); return; }

            ShopItem clicked = getShopItemAtSlot(slot);
            if (clicked == null) return;

            Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
            if (latestClan == null) { player.closeInventory(); return; }

            if (clicked.isConfirm()) {
                player.closeInventory();
                ShopConfirmGUI confirmGui = new ShopConfirmGUI(plugin, player, latestClan, clicked, backAction);
                player.openInventory(confirmGui.build());
            } else {
                player.closeInventory();
                plugin.getClanShopManager().purchase(player, latestClan, clicked);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private ShopItem getShopItemAtSlot(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                int itemIndex = page * ITEM_SLOTS.length + i;
                if (itemIndex < items.size()) return items.get(itemIndex);
                return null;
            }
        }
        return null;
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
        if (latestClan == null) return;
        ClanShopGUI gui = new ClanShopGUI(plugin, player, latestClan, newPage, backAction);
        player.openInventory(gui.build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
    }

    public static void open(QuantumClan plugin, Player player, Clan clan) {
        player.openInventory(new ClanShopGUI(plugin, player, clan, 0, null).build());
    }

    public static void openFromMenu(QuantumClan plugin, Player player, Clan clan, GUINavigation backAction) {
        player.openInventory(new ClanShopGUI(plugin, player, clan, 0, backAction).build());
    }

}