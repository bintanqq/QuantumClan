package me.bintanq.quantumclan.manager;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dao.SocialDAO;
import me.bintanq.quantumclan.model.Clan;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages alliance and rivalry relationships between clans.
 *
 * Alliance: bidirectional, stored once (clanA < clanB lexicographically).
 * Rivalry:  unidirectional (A rivals B ≠ B rivals A).
 *
 * All data is cached in-memory and persisted asynchronously.
 */
public class SocialManager {

    private final QuantumClan plugin;
    private final SocialDAO socialDAO;

    // ── Alliance cache ────────────────────────────────────────
    // Key = clanId, Value = set of allied clanIds
    private final Map<String, Set<String>> allianceCache = new ConcurrentHashMap<>();

    // ── Rivalry cache ─────────────────────────────────────────
    // Key = declarerClanId, Value = set of target clanIds that this clan has declared as rival
    private final Map<String, Set<String>> rivalryCache = new ConcurrentHashMap<>();

    // ── Pending alliance proposals ────────────────────────────
    // Key = targetClanId, Value = set of proposerClanIds
    private final Map<String, Set<String>> pendingProposals = new ConcurrentHashMap<>();

    public SocialManager(QuantumClan plugin, SocialDAO socialDAO) {
        this.plugin    = plugin;
        this.socialDAO = socialDAO;
    }

    // ── Startup load ──────────────────────────────────────────

    public CompletableFuture<Void> loadAll() {
        return socialDAO.loadAllAlliances().thenCompose(alliances -> {
            for (String[] pair : alliances) {
                addAllianceToCache(pair[0], pair[1]);
            }
            return socialDAO.loadAllRivalries();
        }).thenAccept(rivalries -> {
            for (String[] pair : rivalries) {
                addRivalryToCache(pair[0], pair[1]);
            }
            plugin.getLogger().info("[SocialManager] Loaded " + countAlliances() +
                    " alliances and " + countRivalries() + " rivalries.");
        });
    }

    // ══════════════════════════════════════════════════════════
    // ALLIANCES
    // ══════════════════════════════════════════════════════════

    // ── Proposals ─────────────────────────────────────────────

    public boolean hasPendingProposal(String proposerClanId, String targetClanId) {
        Set<String> proposals = pendingProposals.get(targetClanId);
        return proposals != null && proposals.contains(proposerClanId);
    }

    public void addProposal(String proposerClanId, String targetClanId) {
        pendingProposals.computeIfAbsent(targetClanId, k -> ConcurrentHashMap.newKeySet())
                .add(proposerClanId);
    }

    public void removeProposal(String proposerClanId, String targetClanId) {
        Set<String> proposals = pendingProposals.get(targetClanId);
        if (proposals != null) {
            proposals.remove(proposerClanId);
            if (proposals.isEmpty()) pendingProposals.remove(targetClanId);
        }
    }

    public Set<String> getIncomingProposals(String clanId) {
        Set<String> proposals = pendingProposals.get(clanId);
        return proposals != null ? Collections.unmodifiableSet(proposals) : Collections.emptySet();
    }

    // ── Alliance CRUD ─────────────────────────────────────────

    public boolean areAllied(String clanIdA, String clanIdB) {
        Set<String> allies = allianceCache.get(clanIdA);
        return allies != null && allies.contains(clanIdB);
    }

    public int getAllianceCount(String clanId) {
        Set<String> allies = allianceCache.get(clanId);
        return allies != null ? allies.size() : 0;
    }

    public int getMaxAlliances() {
        return plugin.getConfig().getInt("social.alliances.max-per-clan", 3);
    }

    public Set<String> getAllies(String clanId) {
        Set<String> allies = allianceCache.get(clanId);
        return allies != null ? Collections.unmodifiableSet(allies) : Collections.emptySet();
    }

    /**
     * Creates an alliance between two clans. Removes any pending proposal.
     */
    public CompletableFuture<Boolean> createAlliance(String clanIdA, String clanIdB) {
        String[] ordered = orderIds(clanIdA, clanIdB);
        addAllianceToCache(clanIdA, clanIdB);
        removeProposal(clanIdA, clanIdB);
        removeProposal(clanIdB, clanIdA);
        return socialDAO.insertAlliance(ordered[0], ordered[1]);
    }

    /**
     * Breaks an existing alliance.
     */
    public CompletableFuture<Boolean> breakAlliance(String clanIdA, String clanIdB) {
        String[] ordered = orderIds(clanIdA, clanIdB);
        removeAllianceFromCache(clanIdA, clanIdB);
        return socialDAO.deleteAlliance(ordered[0], ordered[1]);
    }

    // ══════════════════════════════════════════════════════════
    // RIVALRIES
    // ══════════════════════════════════════════════════════════

    public boolean isRival(String declarerClanId, String targetClanId) {
        Set<String> rivals = rivalryCache.get(declarerClanId);
        return rivals != null && rivals.contains(targetClanId);
    }

    public int getRivalryCount(String clanId) {
        Set<String> rivals = rivalryCache.get(clanId);
        return rivals != null ? rivals.size() : 0;
    }

    public int getMaxRivalries() {
        return plugin.getConfig().getInt("social.rivalries.max-per-clan", 5);
    }

    public double getRivalXpMultiplier() {
        return plugin.getConfig().getDouble("social.rivalries.xp-multiplier", 1.5);
    }

    public double getRivalReputationMultiplier() {
        return plugin.getConfig().getDouble("social.rivalries.reputation-multiplier", 1.5);
    }

    public Set<String> getRivals(String clanId) {
        Set<String> rivals = rivalryCache.get(clanId);
        return rivals != null ? Collections.unmodifiableSet(rivals) : Collections.emptySet();
    }

    public CompletableFuture<Boolean> declareRivalry(String declarerClanId, String targetClanId) {
        addRivalryToCache(declarerClanId, targetClanId);
        return socialDAO.insertRivalry(declarerClanId, targetClanId);
    }

    public CompletableFuture<Boolean> revokeRivalry(String declarerClanId, String targetClanId) {
        removeRivalryFromCache(declarerClanId, targetClanId);
        return socialDAO.deleteRivalry(declarerClanId, targetClanId);
    }

    // ══════════════════════════════════════════════════════════
    // FRIENDLY FIRE
    // ══════════════════════════════════════════════════════════

    public boolean isClanFriendlyFireDisabled() {
        return plugin.getConfig().getBoolean("social.friendly-fire.disabled", false);
    }

    public boolean isAllyFriendlyFireDisabled() {
        return plugin.getConfig().getBoolean("social.friendly-fire.ally-disabled", true);
    }

    // ══════════════════════════════════════════════════════════
    // CLEANUP
    // ══════════════════════════════════════════════════════════

    /**
     * Removes all social data (alliances + rivalries) for a disbanded clan.
     */
    public CompletableFuture<Void> cleanupClan(String clanId) {
        // Remove from alliance cache
        Set<String> allies = allianceCache.remove(clanId);
        if (allies != null) {
            for (String ally : allies) {
                Set<String> otherAllies = allianceCache.get(ally);
                if (otherAllies != null) otherAllies.remove(clanId);
            }
        }
        // Remove from rivalry cache (both directions)
        rivalryCache.remove(clanId);
        for (Set<String> targets : rivalryCache.values()) {
            targets.remove(clanId);
        }
        // Remove pending proposals
        pendingProposals.remove(clanId);
        for (Set<String> proposers : pendingProposals.values()) {
            proposers.remove(clanId);
        }
        return socialDAO.deleteAllForClan(clanId);
    }

    // ── Cache helpers ─────────────────────────────────────────

    private void addAllianceToCache(String clanIdA, String clanIdB) {
        allianceCache.computeIfAbsent(clanIdA, k -> ConcurrentHashMap.newKeySet()).add(clanIdB);
        allianceCache.computeIfAbsent(clanIdB, k -> ConcurrentHashMap.newKeySet()).add(clanIdA);
    }

    private void removeAllianceFromCache(String clanIdA, String clanIdB) {
        Set<String> setA = allianceCache.get(clanIdA);
        if (setA != null) { setA.remove(clanIdB); if (setA.isEmpty()) allianceCache.remove(clanIdA); }
        Set<String> setB = allianceCache.get(clanIdB);
        if (setB != null) { setB.remove(clanIdA); if (setB.isEmpty()) allianceCache.remove(clanIdB); }
    }

    private void addRivalryToCache(String declarer, String target) {
        rivalryCache.computeIfAbsent(declarer, k -> ConcurrentHashMap.newKeySet()).add(target);
    }

    private void removeRivalryFromCache(String declarer, String target) {
        Set<String> set = rivalryCache.get(declarer);
        if (set != null) { set.remove(target); if (set.isEmpty()) rivalryCache.remove(declarer); }
    }

    private String[] orderIds(String a, String b) {
        return a.compareTo(b) < 0 ? new String[]{a, b} : new String[]{b, a};
    }

    private int countAlliances() {
        int count = 0;
        for (Set<String> set : allianceCache.values()) count += set.size();
        return count / 2; // each alliance is stored twice in cache
    }

    private int countRivalries() {
        int count = 0;
        for (Set<String> set : rivalryCache.values()) count += set.size();
        return count;
    }
}
