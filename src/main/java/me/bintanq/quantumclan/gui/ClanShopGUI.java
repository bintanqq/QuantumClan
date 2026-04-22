package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ShopItem;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Clan Shop GUI — displays all clan-shop items loaded from shop.yml.
 *
 * Layout (54 slots):
 *  Slots 10-16, 19-25, 28-34 — shop items (max 21 visible per page)
 *  Slot 49 — Close button
 *  Slot 45 — Prev page
 *  Slot 53 — Next page
 *
 * Each item shows:
 *  - Name and lore from shop.yml
 *  - Price in Clan Money
 *  - Type indicator (BUFF / CONSUMABLE / UTILITY)
 *
 * Clicking an item either:
 *  - Opens ShopConfirmGUI if item.confirm == true
 *  - Triggers purchase directly if item.confirm == false
 *
 * Anti-dupe: per-player processing flag prevents double-click spam.
 */
public class ClanShopGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private final Player viewer;
    private final int page;
    private final List<ShopItem> items;
    private Inventory inventory;

    public ClanShopGUI(QuantumClan plugin, Player viewer, Clan clan, int page) {
        this.plugin  = plugin;
        this.mm      = plugin.getMiniMessage();
        this.viewer  = viewer;
        this.clan    = clan;
        this.page    = Math.max(0, page);
        this.items   = plugin.getShopConfigManager().getClanShopItems();
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <gold>Clan Shop <dark_gray>]"));

        fillBorder();
        placeItems();
        setNavigation();
        setClanMoneyDisplay();

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            boolean isItemSlot = false;
            for (int s : ITEM_SLOTS) if (s == i) { isItemSlot = true; break; }
            if (!isItemSlot) inventory.setItem(i, glass);
        }
    }

    private void placeItems() {
        int totalPages = getTotalPages();
        int start      = page * ITEM_SLOTS.length;
        int end        = Math.min(start + ITEM_SLOTS.length, items.size());

        for (int i = start; i < end; i++) {
            ShopItem shopItem = items.get(i);
            int slotIndex     = i - start;
            inventory.setItem(ITEM_SLOTS[slotIndex], buildShopItem(shopItem));
        }
    }

    private ItemStack buildShopItem(ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.getMaterial());
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        // Name from shop.yml
        meta.displayName(mm.deserialize("<!italic>" + shopItem.getName()));

        // Lore: inject {price} placeholder then append type/cooldown info
        List<Component> lore = new ArrayList<>();
        for (String line : shopItem.getLore()) {
            String resolved = line
                    .replace("{price}", String.valueOf(shopItem.getPrice()))
                    .replace("{cost}",  String.valueOf(shopItem.getPrice()));
            lore.add(mm.deserialize("<!italic>" + resolved));
        }

        // Append stock info (affordable check)
        lore.add(Component.empty());
        if (clan.hasMoney(shopItem.getPrice())) {
            lore.add(mm.deserialize("<!italic><green>✔ Kas cukup"));
        } else {
            long deficit = shopItem.getPrice() - clan.getMoney();
            lore.add(mm.deserialize("<!italic><red>✘ Kas kurang <yellow>" + deficit));
        }

        // Type badge
        lore.add(mm.deserialize("<!italic><dark_gray>Tipe: <gray>" + shopItem.getType().name()));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void setNavigation() {
        int totalPages = getTotalPages();

        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(45, makeItem(Material.ARROW,
                    "<yellow>« Sebelumnya",
                    List.of(mm.deserialize("<gray>Halaman " + page + " / " + totalPages))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, makeItem(Material.ARROW,
                    "<yellow>Berikutnya »",
                    List.of(mm.deserialize("<gray>Halaman " + (page + 2) + " / " + totalPages))));
        }
    }

    private void setClanMoneyDisplay() {
        List<Component> kasLore = List.of(
                mm.deserialize("<gray>Kas tersedia untuk pembelian.")
        );
        inventory.setItem(4, makeItem(Material.GOLD_INGOT,
                "<gold>Kas Clan: <yellow>" + clan.getMoney(), kasLore));
    }

    // ── Click handler ─────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            // Navigation
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && page > 0) { openPage(player, page - 1); return; }
            if (slot == 53 && page < getTotalPages() - 1) { openPage(player, page + 1); return; }

            // Item click — find which shop item was clicked
            ShopItem clicked = getShopItemAtSlot(slot);
            if (clicked == null) return;

            // Re-fetch clan to get latest money (from cache)
            Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
            if (latestClan == null) { player.closeInventory(); return; }

            if (clicked.isConfirm()) {
                // Open confirm GUI
                player.closeInventory();
                ShopConfirmGUI confirmGui = new ShopConfirmGUI(plugin, player, latestClan, clicked);
                player.openInventory(confirmGui.build());
            } else {
                // Direct purchase (cheap items, no confirm needed)
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
        ClanShopGUI gui = new ClanShopGUI(plugin, player, latestClan, newPage);
        player.openInventory(gui.build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
    }

    // ── Static open helper ────────────────────────────────────

    public static void open(QuantumClan plugin, Player player, Clan clan) {
        ClanShopGUI gui = new ClanShopGUI(plugin, player, clan, 0);
        player.openInventory(gui.build());
    }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Item helper ───────────────────────────────────────────

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