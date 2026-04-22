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
 * Confirmation GUI for expensive clan shop purchases.
 *
 * Layout (27 slots):
 *  Slot 11 — YES (Green Wool) — confirm purchase
 *  Slot 13 — Item preview
 *  Slot 15 — NO  (Red Wool)   — cancel, back to shop
 *
 * Anti-dupe: per-player processing flag + purchase is delegated to
 * ClanShopManager which has its own atomic transaction guard.
 */
public class ShopConfirmGUI implements InventoryHolder {

    private static final int SIZE     = 27;
    private static final int SLOT_YES = 11;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_NO  = 15;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final Clan clan;
    private final ShopItem shopItem;
    private Inventory inventory;

    public ShopConfirmGUI(QuantumClan plugin, Player viewer, Clan clan, ShopItem shopItem) {
        this.plugin    = plugin;
        this.mm        = plugin.getMiniMessage();
        this.viewer    = viewer;
        this.clan      = clan;
        this.shopItem  = shopItem;
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <gold>Konfirmasi Pembelian <dark_gray>]"));

        // Border
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, glass);

        // YES button
        inventory.setItem(SLOT_YES, makeItem(Material.LIME_WOOL,
                "<green><bold>✔ BELI",
                List.of(
                        mm.deserialize("<gray>Konfirmasi pembelian:"),
                        mm.deserialize("<yellow>" + shopItem.getName()),
                        mm.deserialize("<gray>Harga: <gold>" + shopItem.getPrice() + " Clan Money")
                )));

        // Item preview
        ItemStack preview = new ItemStack(shopItem.getMaterial());
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            pm.displayName(mm.deserialize("<!italic>" + shopItem.getName()));
            List<Component> lore = new ArrayList<>();
            for (String line : shopItem.getLore()) {
                String resolved = line
                        .replace("{price}", String.valueOf(shopItem.getPrice()))
                        .replace("{cost}",  String.valueOf(shopItem.getPrice()));
                lore.add(mm.deserialize("<!italic>" + resolved));
            }
            pm.lore(lore);
            preview.setItemMeta(pm);
        }
        inventory.setItem(SLOT_PREVIEW, preview);

        // NO button
        inventory.setItem(SLOT_NO, makeItem(Material.RED_WOOL,
                "<red><bold>✘ BATAL",
                List.of(mm.deserialize("<gray>Kembali ke toko."))));

        return inventory;
    }

    // ── Click handler ─────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            switch (slot) {
                case SLOT_YES -> {
                    player.closeInventory();
                    // Delegate to ClanShopManager — handles balance check, deduct, reward
                    Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
                    if (latestClan == null) return;
                    plugin.getClanShopManager().purchase(player, latestClan, shopItem);
                }
                case SLOT_NO -> {
                    // Go back to shop
                    player.closeInventory();
                    Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
                    if (latestClan == null) return;
                    ClanShopGUI.open(plugin, player, latestClan);
                }
                default -> { /* Border — ignore */ }
            }
        } finally {
            processing.remove(uuid);
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Helper ────────────────────────────────────────────────

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