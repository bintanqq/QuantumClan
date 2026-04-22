package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.CoinsItem;
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
 * Coins Shop GUI — allows players to spend built-in Coins on items.
 * Items are loaded from the coins-shop section in shop.yml.
 *
 * Layout (54 slots):
 *  Slot 4  — balance display
 *  Slots 10-16, 19-25, 28-34 — shop items
 *  Slot 49 — Close
 *  Slot 45 — Prev / Slot 53 — Next
 */
public class CoinsShopGUI implements InventoryHolder {

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
    private final List<CoinsItem> items;
    private Inventory inventory;

    public CoinsShopGUI(QuantumClan plugin, Player viewer, int page) {
        this.plugin  = plugin;
        this.mm      = plugin.getMiniMessage();
        this.viewer  = viewer;
        this.page    = Math.max(0, page);
        this.items   = plugin.getShopConfigManager().getCoinsShopItems();
    }

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        int size = gc.getCoinsShopSize();
        inventory = Bukkit.createInventory(this, size, mm.deserialize(gc.getCoinsShopTitle()));

        // Filler
        ItemStack filler = makeItem(gc.getCoinsShopFiller(), " ", Collections.emptyList());
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        placeItems();
        setNavigation();
        setBalanceDisplay();

        return inventory;
    }

    private void setBalanceDisplay() {
        var gc = plugin.getGuiConfigManager();
        String coinsName = plugin.getConfigManager().getCoinsName();

        // Get balance synchronously from cache is not possible — show async loaded
        plugin.getCoinsProvider().getCoins(viewer.getUniqueId()).thenAccept(balance ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (inventory == null) return;
                    String name = gc.getCoinsShopBalanceName()
                            .replace("{coins-name}", coinsName)
                            .replace("{balance}", String.valueOf(balance));
                    inventory.setItem(gc.getCoinsShopBalanceSlot(),
                            makeItem(gc.getCoinsShopBalanceMat(), name, Collections.emptyList()));
                }));
    }

    private void placeItems() {
        int start = page * ITEM_SLOTS.length;
        int end   = Math.min(start + ITEM_SLOTS.length, items.size());
        String coinsName = plugin.getConfigManager().getCoinsName();

        for (int i = start; i < end; i++) {
            CoinsItem ci = items.get(i);
            int slotIndex = i - start;

            ItemStack item = new ItemStack(ci.getMaterial());
            ItemMeta meta  = item.getItemMeta();
            if (meta == null) continue;

            meta.displayName(mm.deserialize("<!italic>" + ci.getName()));

            List<Component> lore = new ArrayList<>();
            for (String line : ci.getLore()) {
                String resolved = line
                        .replace("{cost}", String.valueOf(ci.getCostCoins()))
                        .replace("{coins-name}", coinsName);
                lore.add(mm.deserialize("<!italic>" + resolved));
            }

            // Add a "price" line and affordability indicator
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic>" + plugin.getMessagesManager()
                    .get("coins.shop-price-label",
                            "{cost}", String.valueOf(ci.getCostCoins()),
                            "{coins-name}", coinsName)));

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(ITEM_SLOTS[slotIndex], item);
        }
    }

    private void setNavigation() {
        var gc = plugin.getGuiConfigManager();
        int totalPages = getTotalPages();

        inventory.setItem(gc.getCoinsShopCloseSlot(),
                makeItem(Material.BARRIER,
                        plugin.getGuiConfigManager().getString("coins-shop.close.name", "<white>Close"),
                        Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getCoinsShopPrevSlot(),
                    makeItem(Material.ARROW, gc.getString("coins-shop.prev.name", "<white>« Previous"),
                            List.of(mm.deserialize(plugin.getMessagesManager()
                                    .get("gui.page-info",
                                            "{current}", String.valueOf(page),
                                            "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getCoinsShopNextSlot(),
                    makeItem(Material.ARROW, gc.getString("coins-shop.next.name", "<white>Next »"),
                            List.of(mm.deserialize(plugin.getMessagesManager()
                                    .get("gui.page-info",
                                            "{current}", String.valueOf(page + 2),
                                            "{total}", String.valueOf(totalPages))))));
        }
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();
            if (slot == gc.getCoinsShopCloseSlot()) { player.closeInventory(); return; }
            if (slot == gc.getCoinsShopPrevSlot() && page > 0) { openPage(player, page - 1); return; }
            if (slot == gc.getCoinsShopNextSlot() && page < getTotalPages() - 1) { openPage(player, page + 1); return; }

            for (int i = 0; i < ITEM_SLOTS.length; i++) {
                if (ITEM_SLOTS[i] == slot) {
                    int idx = page * ITEM_SLOTS.length + i;
                    if (idx >= items.size()) return;
                    CoinsItem ci = items.get(idx);
                    player.closeInventory();
                    plugin.getCoinsShopManager().purchase(player, ci);
                    return;
                }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        player.openInventory(new CoinsShopGUI(plugin, player, newPage).build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new CoinsShopGUI(plugin, player, 0).build());
    }

    @Override public Inventory getInventory() { return inventory; }

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