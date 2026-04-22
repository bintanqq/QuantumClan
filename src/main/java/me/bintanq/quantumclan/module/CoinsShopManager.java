package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.CoinsItem;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all Coins shop purchase logic.
 * Anti-dupe: per-player processing flag prevents double-click concurrent purchases.
 */
public class CoinsShopManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public CoinsShopManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void purchase(Player player, CoinsItem item) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) {
            plugin.sendMessage(player, "error.transaction-processing");
            return;
        }

        String coinsName = plugin.getConfigManager().getCoinsName();
        long cost = item.getCostCoins();

        // Check and deduct coins atomically
        plugin.getCoinsProvider().withdraw(uuid, cost).thenAccept(spent -> {
            if (!spent) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    processing.remove(uuid);
                    plugin.getCoinsProvider().getCoins(uuid).thenAccept(balance ->
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    plugin.sendMessage(player, "coins.spend-insufficient",
                                            "{value}", String.valueOf(balance),
                                            "{coins-name}", coinsName)));
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    deliverReward(player, item);
                    plugin.sendMessage(player, "coins.spend-success",
                            "{value}", String.valueOf(cost),
                            "{item}", item.getName(),
                            "{coins-name}", coinsName);
                } catch (Exception e) {
                    // Rollback
                    plugin.getCoinsProvider().deposit(uuid, cost, "refund-failed-purchase");
                    plugin.sendMessage(player, "shop.purchase-failed");
                    plugin.getLogger().warning("[CoinsShopManager] Reward failed, rolled back: " + e.getMessage());
                } finally {
                    processing.remove(uuid);
                }
            });
        });
    }

    private void deliverReward(Player player, CoinsItem item) {
        switch (item.getType()) {
            case BUFF_PERSONAL -> {
                PotionEffectType effectType = PotionEffectType.getByName(item.getEffect().toUpperCase());
                if (effectType == null) {
                    plugin.getLogger().warning("[CoinsShopManager] Unknown effect: " + item.getEffect());
                    return;
                }
                int ticks = item.getDuration() * 20;
                player.addPotionEffect(new PotionEffect(
                        effectType, ticks, item.getAmplifier(), true, true, true));
            }
            case ITEM -> {
                // Check for special actions first
                String action = item.getAction();
                if ("TAG_COLOR".equalsIgnoreCase(action)) {
                    applyTagColor(player, item.getActionValue());
                    return;
                }

                // Generic item give
                ItemStack stack = new ItemStack(item.getMaterial(), item.getAmount());
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                    plugin.sendMessage(player, "shop.inventory-full-drop", "{value}", item.getName());
                } else {
                    player.getInventory().addItem(stack);
                }
            }
        }
    }

    private void applyTagColor(Player player, String colorTag) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        // Only leaders can change tag color
        if (!clan.getLeaderUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        clan.setTagColor(colorTag);
        plugin.getClanDAO().update(clan);

        plugin.sendMessage(player, "coins.spend-success",
                "{value}", "tag color",
                "{item}", colorTag + "[" + clan.getTag() + "]",
                "{coins-name}", plugin.getConfigManager().getCoinsName());
    }
}