package me.bintanq.quantumclan.util;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared clan banner creation utility.
 */
public final class BannerUtil {

    private BannerUtil() {}

    /**
     * Creates a clan-colored banner unique to the given clan.
     * Colors and patterns are derived from clan name/tag/ID hashes.
     */
    public static ItemStack makeClanBanner(QuantumClan plugin, Clan clan, NamespacedKey clanTagKey) {
        DyeColor base   = resolveBaseColor(clan);
        DyeColor accent = resolveAccentColor(clan, base);

        ItemStack item = new ItemStack(dyeColorToBannerMaterial(base));
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.addPattern(new Pattern(accent, PatternType.GRADIENT));
        meta.addPattern(new Pattern(accent, PatternType.BORDER));
        meta.addPattern(new Pattern(base, resolveUniquePattern(clan)));
        meta.addPattern(new Pattern(accent, PatternType.DIAGONAL_LEFT));

        MiniMessage mm = plugin.getMiniMessage();
        meta.displayName(mm.deserialize("<!italic>"
                + plugin.getMessagesManager().get("shop.banner-name", "{tag}", clan.getColoredTag())));
        meta.lore(List.of(mm.deserialize("<!italic>"
                + plugin.getMessagesManager().get("shop.banner-lore", "{clan}", clan.getName()))));
        meta.getPersistentDataContainer().set(clanTagKey, PersistentDataType.STRING, clan.getId());

        item.setItemMeta(meta);
        return item;
    }

    // ── Private helpers ───────────────────────────────────────

    private static DyeColor resolveBaseColor(Clan clan) {
        String tagColor = clan.getTagColor();
        if (tagColor != null && !tagColor.isBlank()) {
            return tagColorToDyeColor(tagColor);
        }
        DyeColor[] all = DyeColor.values();
        return all[Math.abs(clan.getName().hashCode()) % all.length];
    }

    private static DyeColor resolveAccentColor(Clan clan, DyeColor base) {
        DyeColor[] all = DyeColor.values();
        int idx = Math.abs(clan.getId().hashCode()) % all.length;
        DyeColor accent = all[idx];
        if (accent == base) accent = all[(idx + 1) % all.length];
        return accent;
    }

    private static PatternType resolveUniquePattern(Clan clan) {
        List<PatternType> patterns = new ArrayList<>(
                org.bukkit.Registry.BANNER_PATTERN.stream().toList());
        int seed = (clan.getName() + clan.getTag()).hashCode();
        return patterns.get(Math.abs(seed) % patterns.size());
    }

    private static DyeColor tagColorToDyeColor(String tagColor) {
        if (tagColor == null || tagColor.isBlank()) return DyeColor.WHITE;
        String lower = tagColor.toLowerCase();
        if (lower.contains("gold"))                                   return DyeColor.YELLOW;
        if (lower.contains("dark_red") || lower.contains("red"))      return DyeColor.RED;
        if (lower.contains("dark_blue") || lower.contains("blue"))    return DyeColor.BLUE;
        if (lower.contains("dark_green"))                             return DyeColor.GREEN;
        if (lower.contains("green"))                                  return DyeColor.LIME;
        if (lower.contains("dark_aqua") || lower.contains("cyan"))    return DyeColor.CYAN;
        if (lower.contains("aqua"))                                   return DyeColor.CYAN;
        if (lower.contains("dark_purple") || lower.contains("purple"))return DyeColor.PURPLE;
        if (lower.contains("light_purple"))                           return DyeColor.MAGENTA;
        if (lower.contains("yellow"))                                 return DyeColor.YELLOW;
        if (lower.contains("dark_gray") || lower.contains("dark_grey")) return DyeColor.GRAY;
        if (lower.contains("gray") || lower.contains("grey"))         return DyeColor.LIGHT_GRAY;
        if (lower.contains("black"))                                  return DyeColor.BLACK;
        if (lower.contains("orange"))                                 return DyeColor.ORANGE;
        if (lower.contains("pink"))                                   return DyeColor.PINK;
        return DyeColor.WHITE;
    }

    private static Material dyeColorToBannerMaterial(DyeColor color) {
        return switch (color) {
            case RED        -> Material.RED_BANNER;
            case BLUE       -> Material.BLUE_BANNER;
            case GREEN      -> Material.GREEN_BANNER;
            case YELLOW     -> Material.YELLOW_BANNER;
            case CYAN       -> Material.CYAN_BANNER;
            case PURPLE     -> Material.PURPLE_BANNER;
            case ORANGE     -> Material.ORANGE_BANNER;
            case PINK       -> Material.PINK_BANNER;
            case GRAY       -> Material.GRAY_BANNER;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_BANNER;
            case BLACK      -> Material.BLACK_BANNER;
            case BROWN      -> Material.BROWN_BANNER;
            case LIME       -> Material.LIME_BANNER;
            case MAGENTA    -> Material.MAGENTA_BANNER;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_BANNER;
            default         -> Material.WHITE_BANNER;
        };
    }
}