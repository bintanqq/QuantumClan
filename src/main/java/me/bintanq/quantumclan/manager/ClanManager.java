package me.bintanq.quantumclan.manager;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.ConfigManager;
import me.bintanq.quantumclan.config.RolesConfigManager;
import me.bintanq.quantumclan.database.dao.ClanDAO;
import me.bintanq.quantumclan.database.dao.MemberDAO;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.ClanRole;
import me.bintanq.quantumclan.api.QuantumClanProvider;
import me.bintanq.quantumclan.api.event.ClanCreateEvent;
import me.bintanq.quantumclan.api.event.ClanDisbandEvent;
import me.bintanq.quantumclan.api.event.ClanJoinEvent;
import me.bintanq.quantumclan.api.event.ClanLeaveEvent;
import me.bintanq.quantumclan.api.event.ClanLevelUpEvent;
import me.bintanq.quantumclan.api.event.ClanReputationChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Central cache and CRUD layer for clan data.
 *
 * Cache structure:
 *   clanById         : clanId  → Clan
 *   clanByName       : lowercaseName → Clan
 *   clanByTag        : lowercaseTag  → Clan
 *   memberByClanId   : clanId  → Clan  (reverse lookup)
 *   memberByUuid     : UUID    → ClanMember
 *
 * Leaderboard cache:
 *   leaderboardCache : ordered List<Clan> top N by reputation
 *   Refreshed every leaderboard-cache-interval ticks.
 *
 * All write operations:
 *   1. Update in-memory cache
 *   2. Persist to DB async
 *   3. Invalidate/update leaderboard cache if reputation changed
 */
public class ClanManager {

    private final QuantumClan plugin;
    private final ClanDAO clanDAO;
    private final MemberDAO memberDAO;
    private final ConfigManager config;
    private final RolesConfigManager rolesConfig;

    // ── Primary caches ────────────────────────────────────────
    private final Map<String, Clan>       clanById       = new ConcurrentHashMap<>();
    private final Map<String, Clan>       clanByName     = new ConcurrentHashMap<>();
    private final Map<String, Clan>       clanByTag      = new ConcurrentHashMap<>();
    private final Map<UUID, ClanMember>   memberByUuid   = new ConcurrentHashMap<>();
    // uuid → clanId for O(1) "which clan is this player in?"
    private final Map<UUID, String>       playerClanId   = new ConcurrentHashMap<>();

    // ── Leaderboard cache ─────────────────────────────────────
    private final CopyOnWriteArrayList<Clan> leaderboardCache = new CopyOnWriteArrayList<>();
    private int leaderboardTaskId = -1;

    // ── Pending invites ───────────────────────────────────────
    // targetUuid → clanId
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    public ClanManager(QuantumClan plugin, ClanDAO clanDAO, MemberDAO memberDAO,
                       ConfigManager config, RolesConfigManager rolesConfig) {
        this.plugin      = plugin;
        this.clanDAO     = clanDAO;
        this.memberDAO   = memberDAO;
        this.config      = config;
        this.rolesConfig = rolesConfig;
    }

    // ── Startup load ──────────────────────────────────────────

    /**
     * Loads all clans and members from DB into cache.
     * Blocks the calling thread (call from onEnable before server opens).
     */
    public CompletableFuture<Void> loadAll() {
        return clanDAO.loadAll().thenCompose(clans -> {
            for (Clan clan : clans) {
                clanById.put(clan.getId(), clan);
                clanByName.put(clan.getName().toLowerCase(), clan);
                clanByTag.put(clan.getTag().toLowerCase(), clan);
            }

            return memberDAO.loadAll().thenCompose(members -> {
                for (ClanMember member : members) {
                    memberByUuid.put(member.getUuid(), member);
                    playerClanId.put(member.getUuid(), member.getClanId());

                    Clan clan = clanById.get(member.getClanId());
                    if (clan != null) clan.addMemberUuid(member.getUuid());
                }

                // Load homes for each clan
                List<CompletableFuture<Void>> homeFutures = clans.stream()
                        .map(clan -> clanDAO.loadHomes(clan.getId()).thenAccept(homes ->
                                homes.forEach(clan::addHome)))
                        .collect(Collectors.toList());

                return CompletableFuture.allOf(homeFutures.toArray(new CompletableFuture[0]));
            });
        }).thenRun(() -> {
            plugin.getLogger().info("[ClanManager] Loaded " + clanById.size() +
                    " clans and " + memberByUuid.size() + " members into cache.");
            refreshLeaderboard();
            startLeaderboardScheduler();
        });
    }

    // ── Leaderboard ───────────────────────────────────────────

    private void startLeaderboardScheduler() {
        long interval = config.getLeaderboardCacheInterval();
        leaderboardTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshLeaderboard, interval, interval).getTaskId();
    }

    public void refreshLeaderboard() {
        int size = config.getLeaderboardSize();
        List<Clan> sorted = clanById.values().stream()
                .sorted(Comparator.comparingInt(Clan::getReputation).reversed())
                .limit(size)
                .collect(Collectors.toList());
        leaderboardCache.clear();
        leaderboardCache.addAll(sorted);
    }

    /** Returns an unmodifiable snapshot of the leaderboard cache. */
    public List<Clan> getLeaderboard() {
        return Collections.unmodifiableList(leaderboardCache);
    }

    /** Returns the 1-based rank of the given clan in the leaderboard, or -1 if not found. */
    public int getClanRank(String clanId) {
        List<Clan> board = leaderboardCache;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).getId().equals(clanId)) return i + 1;
        }
        return -1;
    }

    // ── Clan CRUD ─────────────────────────────────────────────

    /**
     * Creates a new clan, persists to DB, updates cache.
     * Does NOT charge the player — caller must handle economy.
     */
    public CompletableFuture<Clan> createClan(String name, String tag, UUID leaderUuid) {
        Clan clan = Clan.create(name, tag, leaderUuid);
        String defaultRole = rolesConfig.getLeaderRole().getName();
        ClanMember leader  = ClanMember.create(leaderUuid, clan.getId(), defaultRole);

        // Add to cache first
        putClanInCache(clan);
        clan.addMemberUuid(leaderUuid);
        memberByUuid.put(leaderUuid, leader);
        playerClanId.put(leaderUuid, clan.getId());

        // Persist async
        return clanDAO.insert(clan)
                .thenCompose(ok -> memberDAO.insert(leader))
                .thenApply(ok -> {
                    if (!ok) {
                        // Rollback cache on DB failure
                        removeClanFromCache(clan);
                        memberByUuid.remove(leaderUuid);
                        playerClanId.remove(leaderUuid);
                        return null;
                    }
                    refreshLeaderboard();
                    Player p = Bukkit.getPlayer(leaderUuid);
                    if (p != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                            new ClanCreateEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()), p)));
                    }
                    return clan;
                });
    }

    /**
     * Disbands a clan: removes all members, homes, and the clan itself.
     */
    public CompletableFuture<Boolean> disbandClan(String clanId) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        // Remove all members from cache
        new ArrayList<>(clan.getMemberUuids()).forEach(uuid -> {
            memberByUuid.remove(uuid);
            playerClanId.remove(uuid);
        });
        removeClanFromCache(clan);

        // DB delete (cascade handles members and homes)
        return clanDAO.delete(clanId).thenApply(ok -> {
            if (ok) {
                // Clean up social data (alliances + rivalries)
                plugin.getSocialManager().cleanupClan(clanId);
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                    new ClanDisbandEvent(QuantumClanProvider.getAPI().getClanById(clanId), null))); // we don't have the actor
                refreshLeaderboard();
            }
            return ok;
        });
    }

    // ── Member management ─────────────────────────────────────

    /**
     * Adds a player to a clan as a member with the default member role.
     */
    public CompletableFuture<Boolean> addMember(String clanId, UUID uuid) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        String defaultRole = rolesConfig.getDefaultMemberRole().getName();
        ClanMember member  = ClanMember.create(uuid, clanId, defaultRole);

        // Update cache
        clan.addMemberUuid(uuid);
        memberByUuid.put(uuid, member);
        playerClanId.put(uuid, clanId);

        return memberDAO.insert(member).thenApply(ok -> {
            if (!ok) {
                clan.removeMemberUuid(uuid);
                memberByUuid.remove(uuid);
                playerClanId.remove(uuid);
            } else {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                        new ClanJoinEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()), p)));
                }
            }
            return ok;
        });
    }

    /**
     * Removes a player from their clan.
     */
    public CompletableFuture<Boolean> removeMember(UUID uuid) {
        ClanMember member = memberByUuid.get(uuid);
        if (member == null) return CompletableFuture.completedFuture(false);

        Clan clan = clanById.get(member.getClanId());
        if (clan != null) clan.removeMemberUuid(uuid);

        memberByUuid.remove(uuid);
        playerClanId.remove(uuid);

        return memberDAO.delete(uuid).thenApply(ok -> {
            if (ok && clan != null) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                        new ClanLeaveEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()), p, ClanLeaveEvent.Reason.LEAVE)));
                }
            }
            return ok;
        });
    }

    /**
     * Changes a member's role. Validates hierarchy — actor must outrank target.
     * Returns false if actor doesn't outrank target or role not found.
     */
    public CompletableFuture<Boolean> setMemberRole(UUID actorUuid, UUID targetUuid,
                                                    String newRoleName) {
        ClanMember actor  = memberByUuid.get(actorUuid);
        ClanMember target = memberByUuid.get(targetUuid);
        if (actor == null || target == null) return CompletableFuture.completedFuture(false);
        if (!actor.getClanId().equals(target.getClanId()))
            return CompletableFuture.completedFuture(false);

        ClanRole actorRole  = rolesConfig.getRole(actor.getRole());
        ClanRole targetRole = rolesConfig.getRole(target.getRole());
        ClanRole newRole    = rolesConfig.getRole(newRoleName);

        if (actorRole == null || targetRole == null || newRole == null)
            return CompletableFuture.completedFuture(false);

        // Actor must outrank target, and new role must be lower than actor
        if (!actorRole.outranks(targetRole)) return CompletableFuture.completedFuture(false);
        if (!actorRole.outranks(newRole) && !actorRole.sameRankAs(newRole))
            return CompletableFuture.completedFuture(false);
        if (newRole.isLeader()) return CompletableFuture.completedFuture(false);

        target.setRole(newRoleName);
        return memberDAO.updateRole(targetUuid, newRoleName);
    }

    /**
     * Transfers clan leadership to another member.
     */
    public CompletableFuture<Boolean> transferLeadership(String clanId,
                                                         UUID currentLeader,
                                                         UUID newLeader) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);
        if (!clan.hasMember(newLeader)) return CompletableFuture.completedFuture(false);

        ClanMember leaderMember    = memberByUuid.get(currentLeader);
        ClanMember newLeaderMember = memberByUuid.get(newLeader);
        if (leaderMember == null || newLeaderMember == null)
            return CompletableFuture.completedFuture(false);

        String leaderRoleName = rolesConfig.getLeaderRole().getName();
        String officerRoleName = rolesConfig.getDefaultOfficerRole() != null
                ? rolesConfig.getDefaultOfficerRole().getName()
                : rolesConfig.getDefaultMemberRole().getName();

        // Update in memory
        clan.setLeaderUuid(newLeader);
        leaderMember.setRole(officerRoleName);
        newLeaderMember.setRole(leaderRoleName);

        // Persist
        return memberDAO.updateRole(currentLeader, officerRoleName)
                .thenCompose(ok -> memberDAO.updateRole(newLeader, leaderRoleName))
                .thenCompose(ok -> clanDAO.update(clan));
    }

    // ── Economy ───────────────────────────────────────────────

    /**
     * Deposits money into clan kas. Adds contribution points to member.
     */
    public CompletableFuture<Boolean> depositMoney(UUID depositorUuid, long amount) {
        ClanMember member = memberByUuid.get(depositorUuid);
        if (member == null) return CompletableFuture.completedFuture(false);

        Clan clan = clanById.get(member.getClanId());
        if (clan == null) return CompletableFuture.completedFuture(false);

        clan.addMoney(amount);
        return clanDAO.updateMoney(clan.getId(), clan.getMoney());
    }

    /**
     * Spends clan money (e.g. shop purchase). Atomic check-and-spend.
     * Returns false if insufficient funds.
     */
    public CompletableFuture<Boolean> spendClanMoney(String clanId, long amount) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        if (!clan.spendMoney(amount)) return CompletableFuture.completedFuture(false);

        return clanDAO.updateMoney(clanId, clan.getMoney()).thenApply(ok -> {
            if (!ok) {
                // Rollback in-memory
                clan.addMoney(amount);
            }
            return ok;
        });
    }

    // ── Level / Upgrade ───────────────────────────────────────

    /**
     * Upgrades a clan's level by spending clan money.
     * Returns false if max level reached or insufficient funds.
     */
    public CompletableFuture<Boolean> upgradeClan(String clanId) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        int currentLevel = clan.getLevel();
        int maxLevel     = config.getMaxLevel();
        if (currentLevel >= maxLevel) return CompletableFuture.completedFuture(false);

        int nextLevel = currentLevel + 1;
        long cost     = config.getLevelCost(nextLevel);

        if (!clan.hasMoney(cost)) return CompletableFuture.completedFuture(false);

        clan.spendMoney(cost);
        clan.setLevel(nextLevel);

        return clanDAO.update(clan).thenApply(ok -> {
            if (!ok) {
                // Rollback
                clan.addMoney(cost);
                clan.setLevel(currentLevel);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                    new ClanLevelUpEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()), currentLevel, nextLevel)));
            }
            return ok;
        });
    }

    // ── Reputation ────────────────────────────────────────────

    public CompletableFuture<Boolean> addReputation(String clanId, int amount) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        clan.addReputation(amount);
        return clanDAO.updateReputation(clanId, clan.getReputation())
                .thenApply(ok -> {
                    if (ok) {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                            new ClanReputationChangeEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()),
                                    clan.getReputation() - amount, clan.getReputation(), ClanReputationChangeEvent.Source.ADMIN)));
                    }
                    refreshLeaderboard();
                    return ok;
                });
    }

    public CompletableFuture<Boolean> setReputation(String clanId, int amount) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        int old = clan.getReputation();
        clan.setReputation(amount);
        return clanDAO.updateReputation(clanId, amount).thenApply(ok -> {
            if (!ok) {
                clan.setReputation(old);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(
                    new ClanReputationChangeEvent(QuantumClanProvider.getAPI().getClanById(clan.getId()),
                            old, amount, ClanReputationChangeEvent.Source.ADMIN)));
            }
            refreshLeaderboard();
            return ok;
        });
    }

    // ── Clan Home management ──────────────────────────────────

    public CompletableFuture<Boolean> setHome(String clanId, Clan.ClanHome home) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        int maxHomes = config.getMaxHomes(clan.getLevel());
        if (clan.getHomeCount() >= maxHomes && clan.getHome(home.getName()) == null)
            return CompletableFuture.completedFuture(false);

        // Remove old home with same name from DB if exists
        Clan.ClanHome existing = clan.getHome(home.getName());
        CompletableFuture<Boolean> deleteFuture = existing != null
                ? clanDAO.deleteHome(existing.getId())
                : CompletableFuture.completedFuture(true);

        clan.addHome(home);

        return deleteFuture.thenCompose(ok -> clanDAO.insertHome(home)).thenApply(ok -> {
            if (!ok && existing != null) clan.addHome(existing); // rollback
            return ok;
        });
    }

    public CompletableFuture<Boolean> deleteHome(String clanId, String homeName) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);

        Clan.ClanHome home = clan.getHome(homeName);
        if (home == null) return CompletableFuture.completedFuture(false);

        clan.removeHome(homeName);
        return clanDAO.deleteHome(home.getId()).thenApply(ok -> {
            if (!ok) clan.addHome(home); // rollback
            return ok;
        });
    }

    // ── Contribution Points ───────────────────────────────────

    public CompletableFuture<Boolean> addContributionPoints(UUID uuid, int points) {
        ClanMember member = memberByUuid.get(uuid);
        if (member == null) return CompletableFuture.completedFuture(false);

        member.addContribution(points);
        return memberDAO.updateContribution(uuid, member.getContributionPoints());
    }

    public CompletableFuture<Boolean> spendContributionPoints(UUID uuid, int points) {
        ClanMember member = memberByUuid.get(uuid);
        if (member == null) return CompletableFuture.completedFuture(false);

        if (!member.spendContribution(points)) return CompletableFuture.completedFuture(false);

        return memberDAO.updateContribution(uuid, member.getContributionPoints())
                .thenApply(ok -> {
                    if (!ok) member.addContribution(points); // rollback
                    return ok;
                });
    }

    // ── Invite management ─────────────────────────────────────

    public void addInvite(UUID targetUuid, String clanId) {
        pendingInvites.put(targetUuid, clanId);
    }

    public String getInvite(UUID targetUuid) {
        return pendingInvites.get(targetUuid);
    }

    public void removeInvite(UUID targetUuid) {
        pendingInvites.remove(targetUuid);
    }

    public boolean hasInvite(UUID targetUuid) {
        return pendingInvites.containsKey(targetUuid);
    }

    // ── Online member helpers ─────────────────────────────────

    public int getOnlineCount(String clanId) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return 0;
        return (int) clan.getMemberUuids().stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .count();
    }

    public List<Player> getOnlineMembers(String clanId) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return Collections.emptyList();
        return clan.getMemberUuids().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Permission checks ─────────────────────────────────────

    /**
     * Returns true if the given player has the named role permission
     * in their clan. Returns false if not in a clan or role not found.
     */
    public boolean hasRolePermission(UUID uuid, String permNode) {
        ClanMember member = memberByUuid.get(uuid);
        if (member == null) return false;
        ClanRole role = rolesConfig.getRole(member.getRole());
        if (role == null) return false;
        return role.hasPermission(permNode);
    }

    // ── Cache lookups ─────────────────────────────────────────

    public Clan getClanById(String id) {
        return clanById.get(id);
    }

    public Clan getClanByName(String name) {
        return clanByName.get(name.toLowerCase());
    }

    public Clan getClanByTag(String tag) {
        return clanByTag.get(tag.toLowerCase());
    }

    public Clan getClanByPlayer(UUID uuid) {
        String clanId = playerClanId.get(uuid);
        if (clanId == null) return null;
        return clanById.get(clanId);
    }

    public ClanMember getMember(UUID uuid) {
        return memberByUuid.get(uuid);
    }

    public boolean isInClan(UUID uuid) {
        return playerClanId.containsKey(uuid);
    }

    public boolean isClanNameTaken(String name) {
        return clanByName.containsKey(name.toLowerCase());
    }

    public boolean isClanTagTaken(String tag) {
        return clanByTag.containsKey(tag.toLowerCase());
    }

    public Collection<Clan> getAllClans() {
        return Collections.unmodifiableCollection(clanById.values());
    }

    public int getTotalClanCount() {
        return clanById.size();
    }

    // ── Admin overrides ───────────────────────────────────────

    public CompletableFuture<Boolean> adminSetLevel(String clanId, int level) {
        Clan clan = clanById.get(clanId);
        if (clan == null) return CompletableFuture.completedFuture(false);
        int old = clan.getLevel();
        clan.setLevel(level);
        return clanDAO.update(clan).thenApply(ok -> {
            if (!ok) clan.setLevel(old);
            return ok;
        });
    }

    // ── Cache helpers ─────────────────────────────────────────

    private void putClanInCache(Clan clan) {
        clanById.put(clan.getId(), clan);
        clanByName.put(clan.getName().toLowerCase(), clan);
        clanByTag.put(clan.getTag().toLowerCase(), clan);
    }

    private void removeClanFromCache(Clan clan) {
        clanById.remove(clan.getId());
        clanByName.remove(clan.getName().toLowerCase());
        clanByTag.remove(clan.getTag().toLowerCase());
    }

    // ── Shutdown ──────────────────────────────────────────────

    public void shutdown() {
        if (leaderboardTaskId != -1) {
            Bukkit.getScheduler().cancelTask(leaderboardTaskId);
        }
    }
}