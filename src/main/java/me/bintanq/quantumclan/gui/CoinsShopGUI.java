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
 *
 * BUG FIX #3: Price was shown twice — once from shop.yml lore (containing {cost})
 * and once from the shop-price-label appended below. Now the price label is ONLY
 * appended if the item's lore doesn't already contain a {cost} placeholder.
 * Standardized approach: shop.yml lore handles description, the GUI appends
 * a single formatted price/affordability line.
 *
 * ADDITION: backAction field + openFromMenu() for back-navigation support.
 * When navigating pages internally, backAction is forwarded so the close button
 * always returns to the parent menu regardless of which page the player is on.
 */
public class CoinsShopGUI implements InventoryHolder {

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan  plugin;
    private final MiniMessage  mm;
    private final Player       viewer;
    private final int          page;
    private final List<CoinsItem> items;
    private final Runnable     backAction; // null = just close inventory
    private Inventory          inventory;

    // ── Constructors ────────────────────────────────────────────────────────

    public CoinsShopGUI(QuantumClan plugin, Player viewer, int page, Runnable backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.viewer     = viewer;
        this.page       = Math.max(0, page);
        this.items      = plugin.getShopConfigManager().getCoinsShopItems();
        this.backAction = backAction;
    }

    /** Backward-compatible constructor (no back navigation). */
    public CoinsShopGUI(QuantumClan plugin, Player viewer, int page) {
        this(plugin, viewer, page, null);
    }

    // ── Build ────────────────────────────────────────────────────────────────

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

            // BUG FIX #3: Resolve lore from shop.yml — these lines may already contain
            // {cost}/{coins-name} as description text. We resolve them but do NOT
            // add a separate price line afterwards to avoid duplication.
            for (String line : ci.getLore()) {
                String resolved = line
                        .replace("{cost}", String.valueOf(ci.getCostCoins()))
                        .replace("{coins-name}", coinsName);
                lore.add(mm.deserialize("<!italic>" + resolved));
            }

            // Only append the price line if the shop.yml lore doesn't already show it.
            boolean loreAlreadyHasPrice = ci.getLore().stream()
                    .anyMatch(l -> l.contains("{cost}") || l.toLowerCase().contains("cost:"));

            if (!loreAlreadyHasPrice) {
                lore.add(Component.empty());
                lore.add(mm.deserialize("<!italic>" + plugin.getMessagesManager()
                        .get("coins.shop-price-label",
                                "{cost}", String.valueOf(ci.getCostCoins()),
                                "{coins-name}", coinsName)));
            }

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
                        plugin.getMessagesManager().get("gui.close"),
                        Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getCoinsShopPrevSlot(),
                    makeItem(Material.ARROW, plugin.getMessagesManager().get("gui.prev"),
                            List.of(mm.deserialize(plugin.getMessagesManager()
                                    .get("gui.page-info",
                                            "{current}", String.valueOf(page),
                                            "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getCoinsShopNextSlot(),
                    makeItem(Material.ARROW, plugin.getMessagesManager().get("gui.next"),
                            List.of(mm.deserialize(plugin.getMessagesManager()
                                    .get("gui.page-info",
                                            "{current}", String.valueOf(page + 2),
                                            "{total}", String.valueOf(totalPages))))));
        }
    }

    // ── Click handler ────────────────────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = plugin.getGuiConfigManager();

            if (slot == gc.getCoinsShopCloseSlot()) {
                // If a backAction is registered, delegate to it (e.g. reopen parent menu).
                if (backAction != null) backAction.run();
                else player.closeInventory();
                return;
            }

            // Page navigation — forward backAction so close still works on new page
            if (slot == gc.getCoinsShopPrevSlot() && page > 0) {
                openPage(player, page - 1);
                return;
            }
            if (slot == gc.getCoinsShopNextSlot() && page < getTotalPages() - 1) {
                openPage(player, page + 1);
                return;
            }

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

    /** Internal page flip — carries backAction forward. */
    private void openPage(Player player, int newPage) {
        player.closeInventory();
        player.openInventory(new CoinsShopGUI(plugin, player, newPage, backAction).build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SLOTS.length));
    }

    // ── Static openers ───────────────────────────────────────────────────────

    /** Opens page 0 without back navigation (original behaviour). */
    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new CoinsShopGUI(plugin, player, 0).build());
    }

    /**
     * Opens from a parent menu. The close/back button will invoke {@code backAction}
     * instead of simply closing, and backAction is forwarded on page flips.
     *
     * <pre>{@code
     * CoinsShopGUI.openFromMenu(plugin, player, () -> MainMenuGUI.open(plugin, player));
     * }</pre>
     */
    public static void openFromMenu(QuantumClan plugin, Player player, Runnable backAction) {
        player.openInventory(new CoinsShopGUI(plugin, player, 0, backAction).build());
    }

    // ── InventoryHolder ──────────────────────────────────────────────────────

    @Override public Inventory getInventory() { return inventory; }

    // ── Helpers ──────────────────────────────────────────────────────────────

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