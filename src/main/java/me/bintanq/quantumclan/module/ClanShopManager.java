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
 * Handles all clan shop purchase logic:
 *  - Atomic balance check → deduct → reward
 *  - Rollback on reward failure
 *  - BUFF: apply to online members + store pending for offline
 *  - CONSUMABLE: give item or activate consumable feature
 *  - UTILITY: permanent upgrades (XP boost, Shield)
 *
 * Anti-dupe: per-player processing flag prevents double-click concurrent purchases.
 */
public class ClanShopManager {

    private final QuantumClan plugin;

    /** Players currently processing a transaction. */
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    // PDC keys for special items
    private final NamespacedKey keySpyTarget;
    private final NamespacedKey keyClanTag;
    private final NamespacedKey keyDeathProtection;

    public ClanShopManager(QuantumClan plugin) {
        this.plugin = plugin;
        keySpyTarget      = new NamespacedKey(plugin, "qc_spy_target");
        keyClanTag        = new NamespacedKey(plugin, "qc_clan_tag");
        keyDeathProtection = new NamespacedKey(plugin, "qc_death_protection");
    }

    // ── Main purchase entry point ──────────────────────────────

    public void purchase(Player player, Clan clan, ShopItem item) {
        UUID uuid = player.getUniqueId();

        // Anti-dupe: one purchase at a time
        if (!processing.add(uuid)) {
            plugin.sendMessage(player, "error.transaction-processing");
            return;
        }

        // Re-fetch clan for latest money
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        if (latest == null) {
            processing.remove(uuid);
            plugin.sendMessage(player, "clan.not-found", "{clan}", clan.getName());
            return;
        }

        // Check role permission
        if (!plugin.getClanManager().hasRolePermission(uuid, "can-access-shop")) {
            processing.remove(uuid);
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(uuid).getRole());
            return;
        }

        long cost = item.getPrice();

        // Atomic: check + deduct Clan Money
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

                    // Deliver reward on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            deliverReward(player, latest, item);
                            plugin.sendMessage(player, "shop.purchase-success", "{value}", item.getName());
                        } catch (Exception e) {
                            // Rollback
                            plugin.getClanManager().depositMoney(uuid, cost);
                            plugin.sendMessage(player, "shop.purchase-failed");
                            plugin.getLogger().warning("[ClanShopManager] Reward delivery failed, rolled back: " + e.getMessage());
                        } finally {
                            processing.remove(uuid);
                        }
                    });
                });
    }

    // ── Reward delivery ───────────────────────────────────────

    private void deliverReward(Player player, Clan clan, ShopItem item) {
        switch (item.getType()) {
            case BUFF      -> deliverBuff(player, clan, item);
            case CONSUMABLE -> deliverConsumable(player, clan, item);
            case UTILITY   -> deliverUtility(player, clan, item);
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

        // Apply to all online members immediately
        plugin.getBuffTracker().applyClanBuff(clan.getId(), effectType, amplifier, durationSec);

        // Store pending buff for all offline members
        Instant expiresAt = Instant.now().plusSeconds(durationSec);
        for (UUID memberUuid : clan.getMemberUuids()) {
            if (Bukkit.getPlayer(memberUuid) != null) continue; // already applied
            String buffId = UUID.randomUUID().toString();
            plugin.getMemberDAO().insertPendingBuff(
                    buffId, memberUuid, item.getEffect().toUpperCase(),
                    amplifier, durationSec, expiresAt);
        }

        // Broadcast to online members
        plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                plugin.sendMessage(m, "shop.buff-received",
                        "{value}", item.getName(),
                        "{duration}", String.valueOf(durationSec / 60) + " menit"));
    }

    private void deliverConsumable(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "spy_scroll" -> {
                // Give Spy Scroll item with PDC
                ItemStack scroll = makeSpyScrollItem(player);
                giveOrDrop(player, scroll, item.getName());
            }
            case "clan_banner" -> {
                // Give clan banner item with clan tag PDC
                ItemStack banner = makeClanBannerItem(clan);
                giveOrDrop(player, banner, item.getName());
            }
            case "clan_announcement" -> {
                // Check cooldown
                if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
                    long remaining = plugin.getBuffTracker().getAnnounceRemainingCooldown(clan.getId());
                    // Rollback handled by caller — throw to trigger rollback
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "clan.announce-cooldown", "{value}", remaining);
                    return;
                }
                // Prompt for message via chat input
                plugin.getChatInputManager().prompt(player,
                        plugin.getMessagesManager().get("clan.create-prompt-name")
                                .replace("Masukkan nama clan", "Masukkan pesan announcement"),
                        msg -> {
                            plugin.getBuffTracker().setAnnounceCooldown(clan.getId());
                            String broadcast = plugin.getMessagesManager()
                                    .get("clan.announce-broadcast",
                                            "{tag}", clan.getColoredTag(),
                                            "{message}", msg);
                            plugin.broadcast(broadcast);
                        },
                        () -> {
                            // Refund if cancelled
                            plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                            plugin.sendMessage(player, "clan.create-cancelled");
                        });
            }
            case "death_protection" -> {
                if (plugin.getBuffTracker().isOnDeathProtectionCooldown(player.getUniqueId())) {
                    long remaining = plugin.getBuffTracker()
                            .getDeathProtectionRemainingCooldown(player.getUniqueId());
                    plugin.getClanManager().depositMoney(player.getUniqueId(), item.getPrice());
                    plugin.sendMessage(player, "shop.purchase-cooldown", "{value}", remaining);
                    return;
                }
                plugin.getBuffTracker().grantDeathProtection(player.getUniqueId());
            }
            default -> {
                // Generic consumable — give item
                ItemStack generic = new ItemStack(item.getMaterial());
                giveOrDrop(player, generic, item.getName());
            }
        }
    }

    private void deliverUtility(Player player, Clan clan, ShopItem item) {
        switch (item.getId()) {
            case "xp_boost" -> {
                plugin.getBuffTracker().activateXpBoost(clan.getId(), item.getDuration());
                // Notify all online members
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-received",
                                "{value}", "XP Boost x" + plugin.getConfigManager().getXpBoostMultiplier(),
                                "{duration}", String.valueOf(item.getDuration() / 60) + " menit"));
            }
            case "clan_shield" -> {
                clan.applyShield(item.getDuration());
                plugin.getClanDAO().updateShield(clan.getId(), clan.getShieldUntil());
                plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                        plugin.sendMessage(m, "shop.buff-applied", "{value}", "Clan Shield"));
            }
            default -> {
                plugin.getLogger().warning("[ClanShopManager] Unknown UTILITY id: " + item.getId());
            }
        }
    }

    // ── Item factories ────────────────────────────────────────

    private ItemStack makeSpyScrollItem(Player buyer) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize("<!italic><dark_aqua>Spy Scroll"));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize("<gray>Klik kanan target untuk melacak."),
                    plugin.getMiniMessage().deserialize("<gray>Durasi: <yellow>" +
                            plugin.getConfigManager().getSpyScrollDuration() / 60 + " menit")
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
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic><gold>Clan Banner " + clan.getColoredTag()));
            meta.lore(List.of(
                    plugin.getMiniMessage().deserialize("<gray>Banner resmi clan " + clan.getName())
            ));
            meta.getPersistentDataContainer().set(keyClanTag,
                    PersistentDataType.STRING, clan.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Inventory helper ──────────────────────────────────────

    private void giveOrDrop(Player player, ItemStack item, String itemName) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            plugin.sendMessage(player, "shop.inventory-full-drop", "{value}", itemName);
        } else {
            player.getInventory().addItem(item);
        }
    }
}