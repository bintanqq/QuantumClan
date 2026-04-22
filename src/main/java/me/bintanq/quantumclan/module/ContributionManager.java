package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ShopConfigManager.ContribItem;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.util.BannerUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles contribution point redemptions.
 * Banner creation delegated to BannerUtil (DRY).
 */
public class ContributionManager {

    private final QuantumClan plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final NamespacedKey keyClanTag;

    public ContributionManager(QuantumClan plugin) {
        this.plugin   = plugin;
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
                player.addPotionEffect(new PotionEffect(
                        effectType, item.getDuration() * 20, item.getAmplifier(), true, true, true));
            }
            case ITEM -> {
                if ("contrib_banner".equals(item.getId()) || "clan_banner".equals(item.getId())) {
                    Clan clan = plugin.getClanManager().getClanById(member.getClanId());
                    if (clan != null) {
                        giveOrDrop(player, BannerUtil.makeClanBanner(plugin, clan, keyClanTag), item.getName());
                        return;
                    }
                }
                giveOrDrop(player, new ItemStack(item.getMaterial(), item.getAmount()), item.getName());
            }
        }
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