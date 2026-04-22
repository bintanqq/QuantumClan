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
 * Propagates the backAction so Cancel returns to the shop correctly.
 */
public class ShopConfirmGUI implements InventoryHolder {

    private static final int SIZE        = 27;
    private static final int SLOT_YES    = 11;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_NO     = 15;

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Player viewer;
    private final Clan clan;
    private final ShopItem shopItem;
    private final GUINavigation shopBackAction; // the back action of the shop (to return to main menu)
    private Inventory inventory;

    public ShopConfirmGUI(QuantumClan plugin, Player viewer, Clan clan, ShopItem shopItem, GUINavigation shopBackAction) {
        this.plugin         = plugin;
        this.mm             = plugin.getMiniMessage();
        this.viewer         = viewer;
        this.clan           = clan;
        this.shopItem       = shopItem;
        this.shopBackAction = shopBackAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, gc.getShopConfirmSize(),
                mm.deserialize(gc.getShopConfirmTitle()));

        ItemStack glass = makeItem(gc.getShopConfirmFiller(), " ", Collections.emptyList());
        for (int i = 0; i < gc.getShopConfirmSize(); i++) inventory.setItem(i, glass);

        // YES button
        List<Component> yesLore = new ArrayList<>();
        yesLore.add(mm.deserialize("<gray>Confirm purchase:"));
        yesLore.add(mm.deserialize("<white>" + shopItem.getName()));
        yesLore.add(mm.deserialize("<gray>Price: <gold>" + plugin.getEconomyProvider().format(shopItem.getPrice()) + " treasury"));
        inventory.setItem(gc.getShopConfirmYesSlot(),
                makeItem(gc.getShopConfirmYesMat(), gc.getShopConfirmYesName(), yesLore));

        // Item preview
        ItemStack preview = new ItemStack(shopItem.getMaterial());
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            pm.displayName(mm.deserialize("<!italic>" + shopItem.getName()));
            List<Component> lore = new ArrayList<>();
            for (String line : shopItem.getLore()) {
                lore.add(mm.deserialize("<!italic>" + line
                        .replace("{price}", String.valueOf(shopItem.getPrice()))
                        .replace("{cost}", String.valueOf(shopItem.getPrice()))));
            }
            pm.lore(lore);
            preview.setItemMeta(pm);
        }
        inventory.setItem(gc.getShopConfirmPreviewSlot(), preview);

        // NO button
        inventory.setItem(gc.getShopConfirmNoSlot(),
                makeItem(gc.getShopConfirmNoMat(), gc.getShopConfirmNoName(),
                        List.of(mm.deserialize("<gray>Return to shop."))));

        return inventory;
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            var gc = plugin.getGuiConfigManager();
            if (slot == gc.getShopConfirmYesSlot()) {
                player.closeInventory();
                Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
                if (latestClan == null) return;
                plugin.getClanShopManager().purchase(player, latestClan, shopItem);
            } else if (slot == gc.getShopConfirmNoSlot()) {
                // Go back to shop
                Clan latestClan = plugin.getClanManager().getClanById(clan.getId());
                if (latestClan == null) { player.closeInventory(); return; }
                player.closeInventory();
                ClanShopGUI.openFromMenu(plugin, player, latestClan, shopBackAction);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

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