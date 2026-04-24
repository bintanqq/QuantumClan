package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Abstract base class for GUIs to reduce boilerplate code.
 */
public abstract class AbstractClanGUI implements BaseGUI {

    protected final QuantumClan plugin;
    protected final MiniMessage mm;
    protected Inventory inventory;

    protected AbstractClanGUI(QuantumClan plugin) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
    }

    /**
     * Helper to create a GUI item with MiniMessage support and no italics.
     */
    protected ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(c -> Component.empty().append(c).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack makeSkull(org.bukkit.OfflinePlayer op, String name, List<Component> lore) {
        ItemStack item = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setOwningPlayer(op);
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(c -> Component.empty().append(c).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract Inventory build();
}
