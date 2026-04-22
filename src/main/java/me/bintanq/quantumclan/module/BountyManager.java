package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.BountyEntry;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.api.QuantumClanProvider;
import me.bintanq.quantumclan.api.event.BountyCompletedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the bounty system:
 *  - In-memory cache of active bounties
 *  - Placing, expiring, and completing bounties
 *  - Anti-dupe via bounty_heads table
 *  - Periodic expiry scheduler (1 minute interval)
 */
public class BountyManager {

    private final QuantumClan plugin;

    /** All active bounties, keyed by target UUID for O(1) lookup. */
    private final Map<UUID, BountyEntry> activeByTarget = new ConcurrentHashMap<>();

    /** Ordered snapshot list for board GUI — rebuilt after every change. */
    private final CopyOnWriteArrayList<BountyEntry> boardSnapshot = new CopyOnWriteArrayList<>();

    private int expiryTaskId = -1;

    public BountyManager(QuantumClan plugin) {
        this.plugin = plugin;
        loadActiveBounties();
    }

    // ── Startup load ──────────────────────────────────────────

    private void loadActiveBounties() {
        plugin.getBountyDAO().loadActiveBounties().thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                activeByTarget.clear();
                for (BountyEntry entry : list) {
                    if (entry.isActive()) {
                        activeByTarget.put(entry.getTargetUuid(), entry);
                    }
                }
                rebuildSnapshot();
                plugin.getLogger().info("[BountyManager] Loaded " + activeByTarget.size() + " active bounties.");
            });
        });
    }

    // ── Place bounty ──────────────────────────────────────────

    /**
     * Places a bounty on targetUuid from the given clan.
     * Economy deduction must be handled by the caller BEFORE calling this.
     * Returns CompletableFuture<Boolean> — false if placement failed.
     */
    public CompletableFuture<Boolean> placeBounty(UUID posterUuid, UUID targetUuid,
                                                  long amount) {
        ClanMember posterMember = plugin.getClanManager().getMember(posterUuid);
        ClanMember targetMember = plugin.getClanManager().getMember(targetUuid);

        if (posterMember == null || targetMember == null) {
            return CompletableFuture.completedFuture(false);
        }

        Clan posterClan = plugin.getClanManager().getClanById(posterMember.getClanId());
        Clan targetClan = plugin.getClanManager().getClanById(targetMember.getClanId());

        if (posterClan == null || targetClan == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check shield
        if (targetClan.hasActiveShield()) {
            return CompletableFuture.completedFuture(false);
        }

        // Check existing bounty
        if (activeByTarget.containsKey(targetUuid)) {
            return CompletableFuture.completedFuture(false);
        }

        long expireHours = plugin.getConfigManager().getBountyExpireHours();
        BountyEntry entry = BountyEntry.create(
                posterClan.getId(), targetClan.getId(), targetUuid, amount, expireHours);

        return plugin.getBountyDAO().insert(entry).thenApply(ok -> {
            if (ok) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    activeByTarget.put(targetUuid, entry);
                    rebuildSnapshot();
                });
            }
            return ok;
        });
    }

    // ── Complete bounty ───────────────────────────────────────

    /**
     * Completes a bounty by submitting a head item.
     * Validates PDC head_id from the item via BountyDAO.submitHead() (anti-dupe).
     * If valid: pays the hunter, adds reputation, marks bounty completed.
     *
     * @param hunter  The player submitting the head
     * @param headId  The head_id from PDC
     * @param bountyId The bounty_id from PDC
     * @return CompletableFuture<Boolean> — true if successful
     */
    public CompletableFuture<Boolean> submitHead(Player hunter, String headId, String bountyId) {
        return plugin.getBountyDAO().submitHead(headId).thenCompose(headValid -> {
            if (!headValid) return CompletableFuture.completedFuture(false);

            return plugin.getBountyDAO().findById(bountyId).thenCompose(entry -> {
                if (entry == null || entry.getStatus() != BountyEntry.Status.ACTIVE) {
                    return CompletableFuture.completedFuture(false);
                }

                long amount = entry.getAmount();
                String hunterClanId = null;
                ClanMember hunterMember = plugin.getClanManager().getMember(hunter.getUniqueId());
                if (hunterMember != null) hunterClanId = hunterMember.getClanId();

                final String finalHunterClanId = hunterClanId;

                // Pay hunter
                return plugin.getEconomyProvider()
                        .deposit(hunter, amount)
                        .thenCompose(paid -> {
                            if (!paid) return CompletableFuture.completedFuture(false);

                            // Update status in DB
                            return plugin.getBountyDAO()
                                    .updateStatus(bountyId, BountyEntry.Status.COMPLETED)
                                    .thenApply(ok -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            // Remove from cache
                                            activeByTarget.remove(entry.getTargetUuid());
                                            rebuildSnapshot();

                                            // Add reputation to hunter clan
                                            if (finalHunterClanId != null) {
                                                int rep = plugin.getConfigManager()
                                                        .getReputationBountyComplete();
                                                plugin.getClanManager()
                                                        .addReputation(finalHunterClanId, rep);
                                            }

                                            // Add contribution points
                                            int contribPts = plugin.getConfigManager()
                                                    .getContribBountyComplete();
                                            plugin.getClanManager().addContributionPoints(
                                                    hunter.getUniqueId(), contribPts);
                                            plugin.getContributionDAO().logContribution(
                                                    hunter.getUniqueId(),
                                                    finalHunterClanId != null ? finalHunterClanId : "",
                                                    contribPts, "bounty-complete");

                                            plugin.sendMessage(hunter, "bounty.submit-success",
                                                    "{value}", plugin.getEconomyProvider().format(amount));

                                            Bukkit.getPluginManager().callEvent(
                                                new BountyCompletedEvent(
                                                    hunter, 
                                                    entry.getTargetUuid(),
                                                    finalHunterClanId != null ? QuantumClanProvider.getAPI().getClanById(finalHunterClanId) : null,
                                                    amount
                                                )
                                            );
                                        });
                                        return true;
                                    });
                        });
            });
        });
    }

    // ── Expiry scheduler ──────────────────────────────────────

    public void startExpiryScheduler() {
        expiryTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getBountyDAO().expireOldBounties().thenAccept(expiredIds -> {
                if (expiredIds.isEmpty()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String id : expiredIds) {
                        activeByTarget.values().removeIf(e -> e.getId().equals(id));
                    }
                    rebuildSnapshot();
                });
            });
        }, 1200L, 1200L).getTaskId();
    }

    // ── Cache helpers ─────────────────────────────────────────

    private void rebuildSnapshot() {
        boardSnapshot.clear();
        boardSnapshot.addAll(activeByTarget.values());
        boardSnapshot.sort(Comparator.comparing(BountyEntry::getPostedAt).reversed());
    }

    /** Returns the cached ordered list for the board GUI. */
    public List<BountyEntry> getActiveBountiesSnapshot() {
        return Collections.unmodifiableList(boardSnapshot);
    }

    /** Returns the active bounty for a target player, or null. */
    public BountyEntry getActiveBountyForTarget(UUID targetUuid) {
        BountyEntry entry = activeByTarget.get(targetUuid);
        if (entry != null && entry.isActive()) return entry;
        if (entry != null && entry.isExpired()) {
            activeByTarget.remove(targetUuid);
            rebuildSnapshot();
        }
        return null;
    }

    public boolean hasBounty(UUID targetUuid) {
        return getActiveBountyForTarget(targetUuid) != null;
    }

    public void stopExpiryScheduler() {
        if (expiryTaskId != -1) Bukkit.getScheduler().cancelTask(expiryTaskId);
    }
}