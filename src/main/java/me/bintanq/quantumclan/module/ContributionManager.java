package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ContribItem;
import me.bintanq.quantumclan.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles contribution point redemptions in the contribution shop.
 * Points are deducted BEFORE reward is given. Rollback if reward fails.
 */
public class ContributionManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public ContributionManager(QuantumClan plugin) {
        this.plugin = plugin;
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

        // Deduct points first
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
                            deliverReward(player, item);
                            plugin.getContributionDAO().logContribution(
                                    uuid, member.getClanId(), -cost, "contribution-shop:" + item.getId());
                            plugin.sendMessage(player, "contribution.shop-purchase-success",
                                    "{value}", String.valueOf(cost),
                                    "{item}", item.getName());
                        } catch (Exception e) {
                            // Rollback
                            plugin.getClanManager().addContributionPoints(uuid, cost);
                            plugin.sendMessage(player, "contribution.shop-purchase-failed");
                            plugin.getLogger().warning("[ContributionManager] Reward failed, rolled back: " + e.getMessage());
                        } finally {
                            processing.remove(uuid);
                        }
                    });
                });
    }

    private void deliverReward(Player player, ContribItem item) {
        switch (item.getType()) {
            case BUFF_PERSONAL -> {
                PotionEffectType effectType = PotionEffectType.getByName(item.getEffect().toUpperCase());
                if (effectType == null) return;
                int ticks = item.getDuration() * 20;
                player.addPotionEffect(new PotionEffect(effectType, ticks, item.getAmplifier(), true, true, true));
            }
            case ITEM -> {
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
}