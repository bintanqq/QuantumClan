package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ShopItem;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.util.BannerUtil;
import net.kyori.adventure.text.Component;
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
 * Banner creation delegated to BannerUtil (DRY).
 * All player-facing messages come from messages.yml.
 */
public class ClanShopManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final NamespacedKey keySpyTarget;
    private final NamespacedKey keyClanTag;

    public ClanShopManager(QuantumClan plugin) {
        this.plugin   = plugin;
        keySpyTarget  = new NamespacedKey(plugin, "qc_spy_target");
        keyClanTag    = new NamespacedKey(plugin, "qc_clan_tag");
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
                            plugin.sendMessage(player, "shop.purchase-success", "{value}", item.getName());
                        } catch (Exception e) {
                            plugin.getClanManager().depositMoney(uuid, cost);
                            plugin.sendMessage(player, "shop.purchase-failed");
                            plugin.getLogger().warning("[ClanShopManager] Reward delivery failed, rolled back: " + e.getMessage());
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

    // ── BUFF ──────────────────────────────────────────────────

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
            plugin.getMemberDAO().insertPendingBuff(
                    UUID.randomUUID().toString(), memberUuid,
                    item.getEffect().toUpperCase(), amplifier, durationSec, expiresAt);
        }

        plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                plugin.sendMessage(m, "shop.buff-received",
                        "{value}", item.getName(),
                        "{duration}", String.valueOf(durationSec / 60)));
    }

    // ── CONSUMABLE ────────────────────────────────────────────

    private void deliverConsumable(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "spy_scroll" -> {
                if (!plugin.getConfigManager().isSpyScrollEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                giveOrDrop(player, makeSpyScrollItem(player), item.getName());
            }
            case "clan_banner" -> giveOrDrop(player,
                    BannerUtil.makeClanBanner(plugin, clan, keyClanTag), item.getName());

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
                            String raw = plugin.getMessagesManager().get("clan.announce-broadcast",
                                    "{tag}", clan.getFormattedTag(), "{message}", msg);
                            Component broadcast = buildBroadcastComponent(raw);
                            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
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
            default -> giveOrDrop(player, new ItemStack(item.getMaterial()), item.getName());
        }
    }

    // ── UTILITY ───────────────────────────────────────────────

    private void deliverUtility(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "xp_boost" -> {
                if (!plugin.getConfigManager().isXpBoostEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                plugin.getBuffTracker().activateXpBoost(clan.getId(), item.getDuration());
                String label = plugin.getMessagesManager().get("shop.xp-boost-label",
                        "{multiplier}", String.valueOf(plugin.getConfigManager().getXpBoostMultiplier()));
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}", label));
            }
            case "clan_shield" -> {
                if (!plugin.getConfigManager().isClanShieldEnabled()) {
                    plugin.sendMessage(player, "error.unknown-subcommand");
                    return;
                }
                clan.applyShield(item.getDuration());
                plugin.getClanDAO().updateShield(clan.getId(), clan.getShieldUntil());
                String label = plugin.getMessagesManager().get("shop.shield-label");
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}", label));
            }
            default -> plugin.getLogger().warning("[ClanShopManager] Unknown UTILITY id: " + item.getId());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private ItemStack makeSpyScrollItem(Player buyer) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(plugin.getMiniMessage().deserialize(
                "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-name")));
        meta.lore(List.of(
                plugin.getMiniMessage().deserialize(
                        "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore1")),
                plugin.getMiniMessage().deserialize(
                        "<!italic>" + plugin.getMessagesManager().get("shop.spy-scroll-lore2",
                                "{duration}", String.valueOf(plugin.getConfigManager().getSpyScrollDuration() / 60)))));
        meta.getPersistentDataContainer().set(keySpyTarget,
                PersistentDataType.STRING, buyer.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private Component buildBroadcastComponent(String raw) {
        String[] lines = raw.split("\\{newline\\}");
        Component bc = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) bc = bc.append(plugin.getMiniMessage().deserialize(lines[i]));
            if (i < lines.length - 1) bc = bc.append(Component.newline());
        }
        return bc;
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