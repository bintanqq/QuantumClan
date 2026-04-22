package me.bintanq.quantumclan.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory model for an active or completed war session.
 * Maps to war_sessions + war_participants tables.
 *
 * Runtime state (kills, eliminated, participant sets) is kept here
 * and flushed to DB on war end via WarDAO.
 */
public class WarSession {

    public enum Format {
        LAST_STANDING,
        KILL_COUNT
    }

    public enum State {
        REGISTRATION,   // accepting registrations
        COUNTDOWN,      // registration closed, countdown to start
        ACTIVE,         // war in progress
        ENDED           // war finished
    }

    // ── Identity ──────────────────────────────────────────────
    private final String id;
    private final Format format;
    private State state;
    private final Instant startedAt;
    private Instant endedAt;
    private String winnerClanId;

    // ── Participants ──────────────────────────────────────────
    /** clanId → set of member UUIDs registered */
    private final Map<String, Set<UUID>> registeredClans = new ConcurrentHashMap<>();

    /** clanId → set of member UUIDs currently alive in war */
    private final Map<String, Set<UUID>> aliveMembers = new ConcurrentHashMap<>();

    /** memberUuid → kill count */
    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();

    /** clanId → total kill count */
    private final Map<String, Integer> clanKillCounts = new ConcurrentHashMap<>();

    /** Members who are eliminated (disconnected or killed) */
    private final Set<UUID> eliminatedMembers = ConcurrentHashMap.newKeySet();

    // ── Constructor ───────────────────────────────────────────

    public WarSession(String id, Format format) {
        this.id        = id;
        this.format    = format;
        this.state     = State.REGISTRATION;
        this.startedAt = Instant.now();
    }

    public static WarSession create(Format format) {
        return new WarSession(UUID.randomUUID().toString(), format);
    }

    // ── Registration ──────────────────────────────────────────

    public void registerClan(String clanId, Set<UUID> members) {
        registeredClans.put(clanId, new HashSet<>(members));
        clanKillCounts.putIfAbsent(clanId, 0);
    }

    public void unregisterClan(String clanId) {
        registeredClans.remove(clanId);
        clanKillCounts.remove(clanId);
    }

    public boolean isClanRegistered(String clanId) {
        return registeredClans.containsKey(clanId);
    }

    public Set<String> getRegisteredClanIds() {
        return Collections.unmodifiableSet(registeredClans.keySet());
    }

    public Set<UUID> getRegisteredMembers(String clanId) {
        return Collections.unmodifiableSet(
                registeredClans.getOrDefault(clanId, Collections.emptySet()));
    }

    // ── Active war ────────────────────────────────────────────

    /**
     * Called when war transitions from COUNTDOWN to ACTIVE.
     * Seeds aliveMembers from registered set.
     */
    public void startWar() {
        this.state = State.ACTIVE;
        for (Map.Entry<String, Set<UUID>> entry : registeredClans.entrySet()) {
            aliveMembers.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }

    public void eliminateMember(UUID uuid, String clanId) {
        eliminatedMembers.add(uuid);
        Set<UUID> alive = aliveMembers.get(clanId);
        if (alive != null) alive.remove(uuid);
    }

    public boolean isMemberAlive(UUID uuid) {
        return !eliminatedMembers.contains(uuid);
    }

    public boolean isMemberParticipating(UUID uuid) {
        return registeredClans.values().stream()
                .anyMatch(set -> set.contains(uuid));
    }

    public String getClanIdForMember(UUID uuid) {
        for (Map.Entry<String, Set<UUID>> entry : registeredClans.entrySet()) {
            if (entry.getValue().contains(uuid)) return entry.getKey();
        }
        return null;
    }

    // ── Kill tracking ─────────────────────────────────────────

    public void recordKill(UUID killerUuid, String killerClanId) {
        killCounts.merge(killerUuid, 1, Integer::sum);
        clanKillCounts.merge(killerClanId, 1, Integer::sum);
    }

    public int getKillCount(UUID uuid) {
        return killCounts.getOrDefault(uuid, 0);
    }

    public int getClanKillCount(String clanId) {
        return clanKillCounts.getOrDefault(clanId, 0);
    }

    public Map<UUID, Integer> getAllKillCounts() {
        return Collections.unmodifiableMap(killCounts);
    }

    public Map<String, Integer> getAllClanKillCounts() {
        return Collections.unmodifiableMap(clanKillCounts);
    }

    // ── Alive tracking ────────────────────────────────────────

    public Set<UUID> getAliveMembers(String clanId) {
        return Collections.unmodifiableSet(
                aliveMembers.getOrDefault(clanId, Collections.emptySet()));
    }

    public int getAliveMemberCount(String clanId) {
        return aliveMembers.getOrDefault(clanId, Collections.emptySet()).size();
    }

    public boolean isClanEliminated(String clanId) {
        Set<UUID> alive = aliveMembers.get(clanId);
        return alive == null || alive.isEmpty();
    }

    public int getActiveClanCount() {
        return (int) aliveMembers.values().stream()
                .filter(s -> !s.isEmpty())
                .count();
    }

    // ── End war ───────────────────────────────────────────────

    public void endWar(String winnerClanId) {
        this.state        = State.ENDED;
        this.winnerClanId = winnerClanId;
        this.endedAt      = Instant.now();
    }

    // ── KILL_COUNT winner resolution ──────────────────────────

    /**
     * Resolves the winner for KILL_COUNT format.
     * Returns the clanId with the most kills, or null if tie or empty.
     */
    public String resolveKillCountWinner() {
        return clanKillCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> {
                    // Check for tie
                    int maxKills = e.getValue();
                    long tiedClans = clanKillCounts.values().stream()
                            .filter(v -> v == maxKills).count();
                    return tiedClans > 1 ? null : e.getKey();
                })
                .orElse(null);
    }

    /**
     * Resolves the winner for LAST_STANDING format.
     * Returns clanId of the last clan with alive members, or null if tie.
     */
    public String resolveLastStandingWinner() {
        long surviving = aliveMembers.values().stream()
                .filter(s -> !s.isEmpty()).count();
        if (surviving != 1) return null;
        return aliveMembers.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // ── Getters ───────────────────────────────────────────────

    public String  getId()            { return id; }
    public Format  getFormat()        { return format; }
    public State   getState()         { return state; }
    public void    setState(State s)  { this.state = s; }
    public Instant getStartedAt()     { return startedAt; }
    public Instant getEndedAt()       { return endedAt; }
    public String  getWinnerClanId()  { return winnerClanId; }

    public boolean isActive()        { return state == State.ACTIVE; }
    public boolean isRegistration()  { return state == State.REGISTRATION; }
    public boolean isEnded()         { return state == State.ENDED; }

    public Map<String, Set<UUID>> getRegisteredClans() {
        return Collections.unmodifiableMap(registeredClans);
    }

    public Set<UUID> getEliminatedMembers() {
        return Collections.unmodifiableSet(eliminatedMembers);
    }

    @Override
    public String toString() {
        return "WarSession{id='" + id + "', format=" + format +
                ", state=" + state + ", clans=" + registeredClans.size() + "}";
    }
}