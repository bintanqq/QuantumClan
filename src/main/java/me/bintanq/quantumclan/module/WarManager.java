package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.WarConfigManager.RewardItem;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of a war session:
 *  REGISTRATION → COUNTDOWN → ACTIVE → ENDED
 *
 * Exposes:
 *  - registerClan / unregisterClan
 *  - startWar (teleport all participants)
 *  - checkWarEnd (called on each kill/elimination)
 *  - endWar (pay rewards, update reputation, broadcast)
 *  - endWarGracefully (called on plugin disable)
 *
 * Kill broadcast runs every kill-broadcast-interval ticks.
 * Hologram updater is started if a hologram plugin is present.
 */
public class WarManager {

    private final QuantumClan plugin;

    /** The single active war session, or null if none. */
    private volatile WarSession activeSession = null;

    private int killBroadcastTaskId = -1;
    private int hologramTaskId      = -1;
    private int warTimerTaskId      = -1;

    public WarManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Register / Unregister ─────────────────────────────────

    public void registerClan(Player player, Clan clan) {
        if (activeSession == null || activeSession.getState() != WarSession.State.REGISTRATION) {
            plugin.sendMessage(player, "war.register-no-war");
            return;
        }

        if (activeSession.isClanRegistered(clan.getId())) {
            plugin.sendMessage(player, "war.register-already");
            return;
        }

        // Check role permission
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-manage-war")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        // Check online member count
        int minOnline = plugin.getWarConfigManager().getMinMembersOnline();
        int online    = plugin.getClanManager().getOnlineCount(clan.getId());
        boolean bypass = player.hasPermission("quantumclan.bypass.war.minclan");
        if (!bypass && online < minOnline) {
            plugin.sendMessage(player, "war.register-insufficient-members", "{value}", minOnline + "");
            return;
        }

        // Get online member UUIDs to register
        Set<UUID> memberUuids = new HashSet<>();
        for (Player m : plugin.getClanManager().getOnlineMembers(clan.getId())) {
            memberUuids.add(m.getUniqueId());
        }

        activeSession.registerClan(clan.getId(), memberUuids);
        plugin.sendMessage(player, "war.register-success");

        // Notify all online clan members
        plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m -> {
            if (!m.equals(player)) plugin.sendMessage(m, "war.register-success");
        });
    }

    public void unregisterClan(Player player, Clan clan) {
        if (activeSession == null || activeSession.getState() != WarSession.State.REGISTRATION) {
            plugin.sendMessage(player, "war.leave-not-registered");
            return;
        }
        if (!activeSession.isClanRegistered(clan.getId())) {
            plugin.sendMessage(player, "war.leave-not-registered");
            return;
        }
        activeSession.unregisterClan(clan.getId());
        plugin.sendMessage(player, "war.leave-success");
    }

    // ── Start war ─────────────────────────────────────────────

    /**
     * Transitions from REGISTRATION/COUNTDOWN → ACTIVE.
     * Teleports all registered members to arena and starts the war timer.
     */
    public void startWar() {
        if (activeSession == null) return;

        Location arena = plugin.getWarConfigManager().getArenaLocation();
        if (arena == null) {
            plugin.getLogger().warning("[WarManager] Arena not set! Cannot start war.");
            activeSession = null;
            return;
        }

        activeSession.startWar();

        // Persist session to DB
        plugin.getWarDAO().insertSession(activeSession);

        // Teleport all registered participants
        for (Map.Entry<String, Set<UUID>> entry : activeSession.getRegisteredClans().entrySet()) {
            for (UUID uuid : entry.getValue()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.teleport(arena);
                    plugin.sendMessage(p, "war.teleport", "{value}", "0");
                }
            }
        }

        // Build participants map for DB
        plugin.getWarDAO().insertParticipantsBatch(
                activeSession.getId(), activeSession.getRegisteredClans());

        // Start kill broadcast
        long broadcastInterval = plugin.getWarConfigManager().getKillBroadcastInterval();
        killBroadcastTaskId = Bukkit.getScheduler().runTaskTimer(plugin,
                this::broadcastKillCount, broadcastInterval, broadcastInterval).getTaskId();

        // Start war timer
        int durationMinutes = plugin.getWarConfigManager().getWarDurationMinutes();
        if (activeSession.getFormat() == WarSession.Format.KILL_COUNT) {
            warTimerTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeSession != null && activeSession.isActive()) {
                    endWar(activeSession.resolveKillCountWinner());
                }
            }, durationMinutes * 60 * 20L).getTaskId();
        }

        // Broadcast war start
        String msg = plugin.getWarConfigManager().getBroadcastWarStart()
                .replace("{format}", activeSession.getFormat().name())
                .replace("{duration}", String.valueOf(durationMinutes));
        plugin.broadcast(msg);
    }

    // ── Check war end (LAST_STANDING) ─────────────────────────

    public void checkWarEnd(WarSession war) {
        if (war == null || !war.isActive()) return;

        if (war.getFormat() == WarSession.Format.LAST_STANDING) {
            int activeClans = war.getActiveClanCount();
            if (activeClans <= 1) {
                String winner = war.resolveLastStandingWinner();
                Bukkit.getScheduler().runTask(plugin, () -> endWar(winner));
            }
        }
    }

    // ── End war ───────────────────────────────────────────────

    public void endWar(String winnerClanId) {
        if (activeSession == null || !activeSession.isActive()) return;

        activeSession.endWar(winnerClanId);

        // Cancel schedulers
        if (killBroadcastTaskId != -1) Bukkit.getScheduler().cancelTask(killBroadcastTaskId);
        if (warTimerTaskId != -1) Bukkit.getScheduler().cancelTask(warTimerTaskId);

        // Persist to DB
        plugin.getWarDAO().updateSessionEnd(
                activeSession.getId(), Instant.now(), winnerClanId);
        plugin.getWarDAO().updateParticipantsBatch(
                activeSession.getId(),
                activeSession.getAllKillCounts(),
                activeSession.getEliminatedMembers());

        Clan winnerClan = winnerClanId != null
                ? plugin.getClanManager().getClanById(winnerClanId) : null;

        // Pay rewards to winner clan members
        if (winnerClan != null) {
            distributeRewards(winnerClan, activeSession.getRegisteredMembers(winnerClanId));

            // Add reputation
            int rep = plugin.getWarConfigManager().getReputationReward();
            plugin.getClanManager().addReputation(winnerClanId, rep);

            // Add contribution points to winning members
            int contribPts = plugin.getConfigManager().getContribWarWin();
            for (UUID uuid : activeSession.getRegisteredMembers(winnerClanId)) {
                plugin.getClanManager().addContributionPoints(uuid, contribPts);
                plugin.getContributionDAO().logContribution(uuid, winnerClanId, contribPts, "war-win");
            }
        }

        // Broadcast result
        String broadcast;
        if (winnerClan != null) {
            String score = activeSession.getFormat() == WarSession.Format.KILL_COUNT
                    ? String.valueOf(activeSession.getClanKillCount(winnerClanId))
                    : "Last Standing";
            broadcast = plugin.getWarConfigManager().getBroadcastWarEnd()
                    .replace("{winner}", winnerClan.getName())
                    .replace("{score}", score);
        } else {
            broadcast = plugin.getWarConfigManager().getBroadcastWarNoWinner();
        }
        plugin.broadcast(broadcast);

        // Notify participants
        for (Map.Entry<String, Set<UUID>> entry : activeSession.getRegisteredClans().entrySet()) {
            boolean isWinner = entry.getKey().equals(winnerClanId);
            for (UUID uuid : entry.getValue()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    plugin.sendMessage(p, isWinner ? "war.win" : "war.lose");
                }
            }
        }

        activeSession = null;
    }

    // ── Graceful end (plugin disable) ─────────────────────────

    public void endWarGracefully() {
        if (activeSession == null) return;
        plugin.getLogger().info("[WarManager] Ending active war gracefully (plugin disable).");
        if (killBroadcastTaskId != -1) Bukkit.getScheduler().cancelTask(killBroadcastTaskId);
        if (warTimerTaskId != -1)      Bukkit.getScheduler().cancelTask(warTimerTaskId);
        if (hologramTaskId != -1)      Bukkit.getScheduler().cancelTask(hologramTaskId);

        // Save incomplete session to DB
        if (activeSession.isActive()) {
            plugin.getWarDAO().updateSessionEnd(activeSession.getId(), Instant.now(), null);
        }
        activeSession = null;
    }

    // ── Session creation ──────────────────────────────────────

    /**
     * Creates a new war session in REGISTRATION state.
     * Called by WarScheduler when the scheduled time arrives.
     */
    public WarSession createSession() {
        WarSession.Format format = plugin.getWarConfigManager().getWarFormat();
        activeSession = WarSession.create(format);
        return activeSession;
    }

    // ── Rewards ───────────────────────────────────────────────

    private void distributeRewards(Clan winnerClan, Set<UUID> memberUuids) {
        double balanceReward = plugin.getWarConfigManager().getRewardBalance();
        long   coinsReward   = plugin.getWarConfigManager().getRewardCoins();
        List<RewardItem> itemRewards = plugin.getWarConfigManager().getRewardItems();

        for (UUID uuid : memberUuids) {
            Player p = Bukkit.getPlayer(uuid);

            // Vault balance
            if (balanceReward > 0) {
                plugin.getEconomyProvider().deposit(Bukkit.getOfflinePlayer(uuid), balanceReward);
                if (p != null) plugin.sendMessage(p, "war.reward-balance",
                        "{value}", plugin.getEconomyProvider().format(balanceReward));
            }

            // Built-in Coins
            if (coinsReward > 0) {
                plugin.getCoinsProvider().grant(uuid, coinsReward, "war-win");
                if (p != null) plugin.sendMessage(p, "war.reward-coins",
                        "{value}", coinsReward + " Coins");
            }

            // Item rewards
            if (p != null) {
                for (RewardItem ri : itemRewards) {
                    ItemStack stack = new ItemStack(ri.material, ri.amount);
                    if (p.getInventory().firstEmpty() == -1) {
                        p.getWorld().dropItemNaturally(p.getLocation(), stack);
                    } else {
                        p.getInventory().addItem(stack);
                    }
                }
            }
        }
    }

    // ── Kill count broadcast ──────────────────────────────────

    private void broadcastKillCount() {
        if (activeSession == null || !activeSession.isActive()) return;

        StringBuilder scores = new StringBuilder();
        activeSession.getAllClanKillCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Clan clan = plugin.getClanManager().getClanById(e.getKey());
                    String name = clan != null ? clan.getColoredTag() : e.getKey();
                    scores.append(name).append(": ").append(e.getValue()).append("  ");
                });

        String broadcast = plugin.getWarConfigManager().getBroadcastKillCount()
                .replace("{scores}", scores.toString().trim());

        // Only send to war participants
        for (Set<UUID> memberSet : activeSession.getRegisteredClans().values()) {
            for (UUID uuid : memberSet) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) plugin.sendRaw(p, broadcast);
            }
        }
    }

    // ── Hologram updater ──────────────────────────────────────

    public void startHologramUpdater() {
        long interval = plugin.getConfigManager().getHologramUpdateInterval();
        hologramTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Hologram update is handled by leaderboard cache refresh
            // DecentHolograms / HolographicDisplays update delegated here if needed
        }, interval, interval).getTaskId();
    }

    // ── Getters ───────────────────────────────────────────────

    public WarSession getActiveSession() { return activeSession; }
    public void setActiveSession(WarSession session) { this.activeSession = session; }
}