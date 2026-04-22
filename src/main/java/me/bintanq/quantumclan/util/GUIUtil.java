package me.bintanq.quantumclan.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared GUI item-building utility.
 *
 * Eliminates the identical makeItem() method duplicated across ~15 GUI classes.
 * All GUI classes should call GUIUtil.makeItem(...) instead of their own private copy.
 *
 * Usage:
 *   ItemStack item = GUIUtil.makeItem(mm, Material.STONE, "<aqua>Title", loreList);
 *   ItemStack item = GUIUtil.makeItem(mm, Material.STONE, "<aqua>Title");
 */
public final class GUIUtil {

    private GUIUtil() {}

    /**
     * Creates an ItemStack with a MiniMessage display name and lore.
     * Italic is disabled on both the name and each lore line.
     *
     * @param mm       MiniMessage instance (pass plugin.getMiniMessage())
     * @param material Item material
     * @param name     MiniMessage display name (<!italic> is prepended automatically)
     * @param lore     List of Adventure Components for lore
     * @return Configured ItemStack
     */
    public static ItemStack makeItem(MiniMessage mm, Material material,
                                     String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize("<!italic>" + name));

        if (lore != null && !lore.isEmpty()) {
            List<Component> noItalic = lore.stream()
                    .map(c -> Component.empty()
                            .append(c)
                            .decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList());
            meta.lore(noItalic);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates an ItemStack with a MiniMessage display name and no lore.
     */
    public static ItemStack makeItem(MiniMessage mm, Material material, String name) {
        return makeItem(mm, material, name, null);
    }

    /**
     * Creates a filler item (invisible name, no lore).
     */
    public static ItemStack makeFiller(MiniMessage mm, Material material) {
        return makeItem(mm, material, " ", null);
    }
}