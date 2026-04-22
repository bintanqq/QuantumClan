package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ContribItem;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles contribution point redemptions.
 */
public class ContributionManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final NamespacedKey keyClanTag;

    public ContributionManager(QuantumClan plugin) {
        this.plugin    = plugin;
        this.keyClanTag = new NamespacedKey(plugin, "qc_clan_tag");
    }

    public void purchase(Player player, ContribItem item) {
        UUID uuid = player.getUniqueId();

        if (!processing.add(uuid)) {
            plugin.sendMessage(player, "error.transaction-processing");
            return;
        }

        ClanMember member = plugin.getClanManager().getMember(uuid);
        if (member == null) {
            processing.remove(uuid);
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        int cost = item.getCostPoints();

        plugin.getClanManager().spendContributionPoints(uuid, cost)
                .thenAccept(spent -> {
                    if (!spent) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            processing.remove(uuid);
                            plugin.sendMessage(player, "contribution.shop-insufficient",
                                    "{value}", String.valueOf(member.getContributionPoints()));
                        });
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            deliverReward(player, item, member);
                            plugin.getContributionDAO().logContribution(
                                    uuid, member.getClanId(), -cost, "contribution-shop:" + item.getId());
                            plugin.sendMessage(player, "contribution.shop-purchase-success",
                                    "{value}", String.valueOf(cost),
                                    "{item}", item.getName());
                        } catch (Exception e) {
                            plugin.getClanManager().addContributionPoints(uuid, cost);
                            plugin.sendMessage(player, "contribution.shop-purchase-failed");
                            plugin.getLogger().warning("[ContributionManager] Reward failed, rolled back: " + e.getMessage());
                        } finally {
                            processing.remove(uuid);
                        }
                    });
                });
    }

    private void deliverReward(Player player, ContribItem item, ClanMember member) {
        switch (item.getType()) {
            case BUFF_PERSONAL -> {
                PotionEffectType effectType = PotionEffectType.getByName(item.getEffect().toUpperCase());
                if (effectType == null) return;
                int ticks = item.getDuration() * 20;
                player.addPotionEffect(new PotionEffect(effectType, ticks, item.getAmplifier(), true, true, true));
            }
            case ITEM -> {
                // BUG FIX 5: Check for clan_banner id and produce a proper clan banner
                if ("contrib_banner".equals(item.getId()) || "clan_banner".equals(item.getId())) {
                    Clan clan = plugin.getClanManager().getClanById(member.getClanId());
                    if (clan != null) {
                        ItemStack banner = makeClanBannerItem(clan);
                        giveOrDrop(player, banner, item.getName());
                        return;
                    }
                }
                ItemStack stack = new ItemStack(item.getMaterial(), item.getAmount());
                giveOrDrop(player, stack, item.getName());
            }
        }
    }

    // ── Banner helpers (mirror of ClanShopManager) ────────────

    private ItemStack makeClanBannerItem(Clan clan) {
        DyeColor baseColor   = resolveBaseColor(clan);
        DyeColor accentColor = resolveAccentColor(clan, baseColor);

        Material bannerMaterial = dyeColorToBannerMaterial(baseColor);
        ItemStack item = new ItemStack(bannerMaterial);
        BannerMeta meta = (BannerMeta) item.getItemMeta();

        if (meta != null) {
            meta.addPattern(new Pattern(accentColor, PatternType.GRADIENT));
            meta.addPattern(new Pattern(accentColor, PatternType.BORDER));
            PatternType uniquePattern = resolveUniquePattern(clan);
            meta.addPattern(new Pattern(baseColor, uniquePattern));
            meta.addPattern(new Pattern(accentColor, PatternType.DIAGONAL_LEFT));

            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic>" + plugin.getMessagesManager().get("shop.banner-name",
                            "{tag}", clan.getColoredTag())));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.banner-lore",
                                    "{clan}", clan.getName()))
            ));
            meta.getPersistentDataContainer().set(keyClanTag, PersistentDataType.STRING, clan.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private DyeColor resolveBaseColor(Clan clan) {
        String tagColor = clan.getTagColor();
        if (tagColor != null && !tagColor.isBlank()) return tagColorToDyeColor(tagColor);
        DyeColor[] all = DyeColor.values();
        return all[Math.abs(clan.getName().hashCode()) % all.length];
    }

    private DyeColor resolveAccentColor(Clan clan, DyeColor base) {
        DyeColor[] all = DyeColor.values();
        int idx = Math.abs(clan.getId().hashCode()) % all.length;
        DyeColor accent = all[idx];
        if (accent == base) accent = all[(idx + 1) % all.length];
        return accent;
    }

    private PatternType resolveUniquePattern(Clan clan) {
        List<PatternType> patterns = new ArrayList<>(
                org.bukkit.Registry.BANNER_PATTERN.stream().toList());
        int seed = (clan.getName() + clan.getTag()).hashCode();
        return patterns.get(Math.abs(seed) % patterns.size());
    }

    private DyeColor tagColorToDyeColor(String tagColor) {
        if (tagColor == null || tagColor.isBlank()) return DyeColor.WHITE;
        String lower = tagColor.toLowerCase();
        if (lower.contains("gold"))                                  return DyeColor.YELLOW;
        if (lower.contains("dark_red") || lower.contains("red"))     return DyeColor.RED;
        if (lower.contains("dark_blue") || lower.contains("blue"))   return DyeColor.BLUE;
        if (lower.contains("dark_green"))                            return DyeColor.GREEN;
        if (lower.contains("green"))                                 return DyeColor.LIME;
        if (lower.contains("dark_aqua") || lower.contains("cyan"))   return DyeColor.CYAN;
        if (lower.contains("aqua"))                                  return DyeColor.CYAN;
        if (lower.contains("dark_purple") || lower.contains("purple"))return DyeColor.PURPLE;
        if (lower.contains("light_purple"))                          return DyeColor.MAGENTA;
        if (lower.contains("yellow"))                                return DyeColor.YELLOW;
        if (lower.contains("dark_gray") || lower.contains("dark_grey")) return DyeColor.GRAY;
        if (lower.contains("gray") || lower.contains("grey"))        return DyeColor.LIGHT_GRAY;
        if (lower.contains("black"))                                 return DyeColor.BLACK;
        if (lower.contains("orange"))                                return DyeColor.ORANGE;
        if (lower.contains("pink"))                                  return DyeColor.PINK;
        return DyeColor.WHITE;
    }

    private Material dyeColorToBannerMaterial(DyeColor color) {
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

    private void giveOrDrop(Player player, ItemStack item, String itemName) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            plugin.sendMessage(player, "shop.inventory-full-drop", "{value}", itemName);
        } else {
            player.getInventory().addItem(item);
        }
    }
}