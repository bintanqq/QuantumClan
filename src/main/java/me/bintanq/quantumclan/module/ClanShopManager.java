package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ShopItem;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
 * BUG FIX: All hardcoded messages replaced with messages.yml lookups.
 */
public class ClanShopManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final NamespacedKey keySpyTarget;
    private final NamespacedKey keyClanTag;

    public ClanShopManager(QuantumClan plugin) {
        this.plugin       = plugin;
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
                                    "{value}", String.valueOf(latest.getMoney()));
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

        // FIX: was hardcoded "menit" — use messages.yml
        plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                plugin.sendMessage(m, "shop.buff-received",
                        "{value}", item.getName(),
                        "{duration}", String.valueOf(durationSec / 60)));
    }

    private void deliverConsumable(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "spy_scroll" -> {
                ItemStack scroll = makeSpyScrollItem(player);
                giveOrDrop(player, scroll, item.getName());
            }
            case "clan_banner" -> {
                ItemStack banner = makeClanBannerItem(clan);
                giveOrDrop(player, banner, item.getName());
            }
            case "clan_announcement" -> {
                if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
                    long remaining = plugin.getBuffTracker()
                            .getAnnounceRemainingCooldown(clan.getId());
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "clan.announce-cooldown",
                            "{value}", String.valueOf(remaining));
                    return;
                }
                // FIX: was hardcoded "Masukkan pesan announcement"
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
                if (plugin.getBuffTracker().isOnDeathProtectionCooldown(player.getUniqueId())) {
                    long remaining = plugin.getBuffTracker()
                            .getDeathProtectionRemainingCooldown(player.getUniqueId());
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "shop.purchase-cooldown",
                            "{value}", String.valueOf(remaining));
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
                plugin.getBuffTracker().activateXpBoost(clan.getId(), item.getDuration());
                // FIX: was hardcoded "XP Boost x..."
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}",
                                plugin.getMessagesManager().get("shop.xp-boost-label",
                                        "{multiplier}",
                                        String.valueOf(plugin.getConfigManager().getXpBoostMultiplier()))));
            }
            case "clan_shield" -> {
                clan.applyShield(item.getDuration());
                plugin.getClanDAO().updateShield(clan.getId(), clan.getShieldUntil());
                // FIX: was hardcoded "Clan Shield"
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied",
                                "{value}", plugin.getMessagesManager().get("shop.shield-label")));
            }
            default -> plugin.getLogger().warning("[ClanShopManager] Unknown UTILITY id: "
                    + item.getId());
        }
    }

    private ItemStack makeSpyScrollItem(Player buyer) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            // FIX: use messages.yml for name and lore
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-name")));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore1")),
                    plugin.getMiniMessage().deserialize(
                            "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore2",
                                    "{duration}",
                                    String.valueOf(plugin.getConfigManager().getSpyScrollDuration() / 60)))
            ));
            meta.getPersistentDataContainer().set(keySpyTarget,
                    PersistentDataType.STRING, buyer.getUniqueId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeClanBannerItem(Clan clan) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            // FIX: use messages.yml
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

    private void giveOrDrop(Player player, ItemStack item, String itemName) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            plugin.sendMessage(player, "shop.inventory-full-drop", "{value}", itemName);
        } else {
            player.getInventory().addItem(item);
        }
    }
}