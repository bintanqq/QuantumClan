package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ShopItem;
import me.bintanq.quantumclan.model.Clan;
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
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all clan shop purchase logic.
 *
 * BUG FIX #4: Removed all "KAS" references — replaced with "Treasury".
 * BUG FIX #5: Clan Banner now creates a dye-colored banner with clan-specific
 *             patterns based on the clan's tag color. Each banner is unique per clan.
 * BUG FIX #11: All messages come from messages.yml — zero hardcoded player text.
 */
public class ClanShopManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final NamespacedKey keySpyTarget;
    private final NamespacedKey keyClanTag;

    public ClanShopManager(QuantumClan plugin) {
        this.plugin      = plugin;
        keySpyTarget = new NamespacedKey(plugin, "qc_spy_target");
        keyClanTag   = new NamespacedKey(plugin, "qc_clan_tag");
    }

    public void purchase(Player player, Clan clan, ShopItem item) {
        UUID uuid = player.getUniqueId();

        if (!processing.add(uuid)) {
            plugin.sendMessage(player, "error.transaction-processing");
            return;
        }

        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        if (latest == null) {
            processing.remove(uuid);
            plugin.sendMessage(player, "clan.not-found", "{clan}", clan.getName());
            return;
        }

        if (!plugin.getClanManager().hasRolePermission(uuid, "can-access-shop")) {
            processing.remove(uuid);
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(uuid).getRole());
            return;
        }

        long cost = item.getPrice();

        plugin.getClanManager().spendClanMoney(latest.getId(), cost)
                .thenAccept(spent -> {
                    if (!spent) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            processing.remove(uuid);
                            plugin.sendMessage(player, "error.not-enough-clan-money",
                                    "{value}", plugin.getEconomyProvider().format(latest.getMoney()));
                        });
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            deliverReward(player, latest, item);
                            plugin.sendMessage(player, "shop.purchase-success",
                                    "{value}", item.getName());
                        } catch (Exception e) {
                            plugin.getClanManager().depositMoney(uuid, cost);
                            plugin.sendMessage(player, "shop.purchase-failed");
                            plugin.getLogger().warning("[ClanShopManager] Reward delivery failed, rolled back: "
                                    + e.getMessage());
                        } finally {
                            processing.remove(uuid);
                        }
                    });
                });
    }

    private void deliverReward(Player player, Clan clan, ShopItem item) {
        switch (item.getType()) {
            case BUFF       -> deliverBuff(player, clan, item);
            case CONSUMABLE -> deliverConsumable(player, clan, item);
            case UTILITY    -> deliverUtility(player, clan, item);
        }
    }

    private void deliverBuff(Player player, Clan clan, ShopItem item) {
        PotionEffectType effectType = PotionEffectType.getByName(item.getEffect().toUpperCase());
        if (effectType == null) {
            plugin.getLogger().warning("[ClanShopManager] Unknown effect: " + item.getEffect());
            return;
        }

        int durationSec = item.getDuration();
        int amplifier   = item.getAmplifier();

        plugin.getBuffTracker().applyClanBuff(clan.getId(), effectType, amplifier, durationSec);

        Instant expiresAt = Instant.now().plusSeconds(durationSec);
        for (UUID memberUuid : clan.getMemberUuids()) {
            if (Bukkit.getPlayer(memberUuid) != null) continue;
            String buffId = UUID.randomUUID().toString();
            plugin.getMemberDAO().insertPendingBuff(
                    buffId, memberUuid, item.getEffect().toUpperCase(),
                    amplifier, durationSec, expiresAt);
        }

        plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                plugin.sendMessage(m, "shop.buff-received",
                        "{value}", item.getName(),
                        "{duration}", String.valueOf(durationSec / 60)));
    }

    private void deliverConsumable(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "spy_scroll" -> {
                if (!plugin.getConfigManager().isSpyScrollEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                ItemStack scroll = makeSpyScrollItem(player);
                giveOrDrop(player, scroll, item.getName());
            }
            case "clan_banner" -> {
                // BUG FIX #5: Create a proper clan-colored banner with patterns
                ItemStack banner = makeClanBannerItem(clan);
                giveOrDrop(player, banner, item.getName());
            }
            case "clan_announcement" -> {
                if (!plugin.getConfigManager().isAnnouncementsEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
                    long remaining = plugin.getBuffTracker().getAnnounceRemainingCooldown(clan.getId());
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "clan.announce-cooldown", "{value}", String.valueOf(remaining));
                    return;
                }
                plugin.getChatInputManager().prompt(player,
                        plugin.getMessagesManager().get("shop.announcement-prompt"),
                        msg -> {
                            plugin.getBuffTracker().setAnnounceCooldown(clan.getId());
                            String broadcast = plugin.getMessagesManager()
                                    .get("clan.announce-broadcast",
                                            "{tag}", clan.getFormattedTag(),
                                            "{message}", msg);
                            String[] lines = broadcast.split("\\{newline\\}");
                            net.kyori.adventure.text.Component bc = net.kyori.adventure.text.Component.empty();
                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i];
                                if (!line.isBlank()) bc = bc.append(plugin.getMiniMessage().deserialize(line));
                                if (i < lines.length - 1) bc = bc.append(net.kyori.adventure.text.Component.newline());
                            }
                            final net.kyori.adventure.text.Component finalBc = bc;
                            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalBc));
                        },
                        () -> {
                            plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                            plugin.sendMessage(player, "clan.create-cancelled");
                        });
            }
            case "death_protection" -> {
                if (!plugin.getConfigManager().isDeathProtectionEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                if (plugin.getBuffTracker().isOnDeathProtectionCooldown(player.getUniqueId())) {
                    long remaining = plugin.getBuffTracker().getDeathProtectionRemainingCooldown(player.getUniqueId());
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "shop.purchase-cooldown", "{value}", String.valueOf(remaining));
                    return;
                }
                plugin.getBuffTracker().grantDeathProtection(player.getUniqueId());
            }
            default -> {
                ItemStack generic = new ItemStack(item.getMaterial());
                giveOrDrop(player, generic, item.getName());
            }
        }
    }

    private void deliverUtility(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "xp_boost" -> {
                if (!plugin.getConfigManager().isXpBoostEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                plugin.getBuffTracker().activateXpBoost(clan.getId(), item.getDuration());
                String boostLabel = plugin.getMessagesManager().get("shop.xp-boost-label",
                        "{multiplier}", String.valueOf(plugin.getConfigManager().getXpBoostMultiplier()));
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}", boostLabel));
            }
            case "clan_shield" -> {
                if (!plugin.getConfigManager().isClanShieldEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                clan.applyShield(item.getDuration());
                plugin.getClanDAO().updateShield(clan.getId(), clan.getShieldUntil());
                String shieldLabel = plugin.getMessagesManager().get("shop.shield-label");
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}", shieldLabel));
            }
            default -> plugin.getLogger().warning("[ClanShopManager] Unknown UTILITY id: " + item.getId());
        }
    }

    private ItemStack makeSpyScrollItem(Player buyer) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-name")));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore1")),
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore2",
                                    "{duration}", String.valueOf(plugin.getConfigManager().getSpyScrollDuration() / 60)))
            ));
            meta.getPersistentDataContainer().set(keySpyTarget,
                    PersistentDataType.STRING, buyer.getUniqueId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * BUG FIX #5: Creates a clan-specific banner with the clan's color and
     * a unique pattern combination derived from the clan's tag characters.
     * Each clan gets a distinct visual banner instead of a plain white one.
     */
    private ItemStack makeClanBannerItem(Clan clan) {
        // Determine base color from tag color
        DyeColor baseColor = tagColorToDyeColor(clan.getTagColor());
        DyeColor accentColor = baseColor == DyeColor.WHITE ? DyeColor.LIGHT_GRAY : DyeColor.WHITE;

        Material bannerMaterial = dyeColorToBannerMaterial(baseColor);
        ItemStack item = new ItemStack(bannerMaterial);
        BannerMeta meta = (BannerMeta) item.getItemMeta();

        if (meta != null) {
            // Add clan-specific decorative patterns
            // Base gradient
            meta.addPattern(new Pattern(accentColor, PatternType.GRADIENT));
            // Border pattern
            meta.addPattern(new Pattern(accentColor, PatternType.BORDER));
            // Clan initial letter pattern (based on first char of tag)
            PatternType letterPattern = getLetterPattern(clan.getTag());
            if (letterPattern != null) {
                meta.addPattern(new Pattern(baseColor, letterPattern));
            }
            // Diagonal stripe as signature
            meta.addPattern(new Pattern(accentColor, PatternType.DIAGONAL_LEFT));

            // Name and lore from messages.yml
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic>" + plugin.getMessagesManager().get("shop.banner-name",
                            "{tag}", clan.getColoredTag())));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.banner-lore",
                                    "{clan}", clan.getName()))
            ));
            meta.getPersistentDataContainer().set(keyClanTag,
                    PersistentDataType.STRING, clan.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Converts a MiniMessage color tag to a DyeColor for the banner base.
     */
    private DyeColor tagColorToDyeColor(String tagColor) {
        if (tagColor == null || tagColor.isBlank()) return DyeColor.WHITE;
        String lower = tagColor.toLowerCase();
        if (lower.contains("gold"))         return DyeColor.YELLOW;
        if (lower.contains("red"))          return DyeColor.RED;
        if (lower.contains("blue"))         return DyeColor.BLUE;
        if (lower.contains("green"))        return DyeColor.GREEN;
        if (lower.contains("aqua") || lower.contains("cyan")) return DyeColor.CYAN;
        if (lower.contains("purple") || lower.contains("light_purple")) return DyeColor.PURPLE;
        if (lower.contains("dark_green"))   return DyeColor.GREEN;
        if (lower.contains("dark_red"))     return DyeColor.RED;
        if (lower.contains("dark_blue"))    return DyeColor.BLUE;
        if (lower.contains("dark_aqua"))    return DyeColor.CYAN;
        if (lower.contains("dark_purple"))  return DyeColor.PURPLE;
        if (lower.contains("yellow"))       return DyeColor.YELLOW;
        if (lower.contains("white"))        return DyeColor.WHITE;
        if (lower.contains("gray") || lower.contains("grey")) return DyeColor.LIGHT_GRAY;
        if (lower.contains("black"))        return DyeColor.BLACK;
        if (lower.contains("orange"))       return DyeColor.ORANGE;
        if (lower.contains("pink"))         return DyeColor.PINK;
        return DyeColor.WHITE;
    }

    private Material dyeColorToBannerMaterial(DyeColor color) {
        return switch (color) {
            case RED          -> Material.RED_BANNER;
            case BLUE         -> Material.BLUE_BANNER;
            case GREEN        -> Material.GREEN_BANNER;
            case YELLOW       -> Material.YELLOW_BANNER;
            case CYAN         -> Material.CYAN_BANNER;
            case PURPLE       -> Material.PURPLE_BANNER;
            case ORANGE       -> Material.ORANGE_BANNER;
            case PINK         -> Material.PINK_BANNER;
            case GRAY         -> Material.GRAY_BANNER;
            case LIGHT_GRAY   -> Material.LIGHT_GRAY_BANNER;
            case BLACK        -> Material.BLACK_BANNER;
            case BROWN        -> Material.BROWN_BANNER;
            case LIME         -> Material.LIME_BANNER;
            case MAGENTA      -> Material.MAGENTA_BANNER;
            case LIGHT_BLUE   -> Material.LIGHT_BLUE_BANNER;
            default           -> Material.WHITE_BANNER;
        };
    }

    /**
     * Returns a decorative pattern loosely based on the clan tag's first character.
     * Not a real letter — just a unique visual marker per clan.
     */
    private PatternType getLetterPattern(String tag) {
        if (tag == null || tag.isEmpty()) return PatternType.CROSS;
        char first = Character.toUpperCase(tag.charAt(0));
        // Cycle through some visually distinct patterns based on char code
        PatternType[] patterns = {
                PatternType.RHOMBUS, PatternType.CROSS, PatternType.FLOWER,
                PatternType.CREEPER, PatternType.SKULL, PatternType.MOJANG,
                PatternType.TRIANGLE_TOP, PatternType.TRIANGLE_BOTTOM,
                PatternType.SQUARE_TOP_LEFT, PatternType.SQUARE_TOP_RIGHT,
                PatternType.HALF_HORIZONTAL, PatternType.HALF_VERTICAL,
                PatternType.CURLY_BORDER, PatternType.BRICKS
        };
        return patterns[(first - 'A') % patterns.length];
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