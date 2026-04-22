package me.bintanq.quantumclan.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only view of an active war session.
 */
public interface WarSessionAPI {

    /** War session ID (UUID string). */
    @NotNull
    String getId();

    /**
     * War format.
     * @see Format
     */
    @NotNull
    Format getFormat();

    /**
     * Current state of the war session.
     * @see State
     */
    @NotNull
    State getState();

    /** Returns {@code true} if the war is currently in ACTIVE state. */
    boolean isActive();

    /** Instant the war started. */
    @NotNull
    Instant getStartedAt();

    /** Instant the war ended, or {@code null} if still running. */
    @Nullable
    Instant getEndedAt();

    /** Winner clan ID, or {@code null} if the war has not ended or ended in a draw. */
    @Nullable
    String getWinnerClanId();

    // ── Participants ──────────────────────────────────────────

    /** IDs of clans registered for (or participating in) this war. */
    @NotNull
    Set<String> getRegisteredClanIds();

    /** Returns {@code true} if the given clan is registered. */
    boolean isClanRegistered(@NotNull String clanId);

    /**
     * Returns {@code true} if the given player UUID is participating in the war
     * (i.e. was online when their clan registered).
     */
    boolean isMemberParticipating(@NotNull UUID uuid);

    /** Returns {@code true} if the given player has not been eliminated yet. */
    boolean isMemberAlive(@NotNull UUID uuid);

    // ── Scores ────────────────────────────────────────────────

    /** Kill count for an individual player (0 if not participating). */
    int getKillCount(@NotNull UUID uuid);

    /** Total kill count for a clan (0 if not participating). */
    int getClanKillCount(@NotNull String clanId);

    /** Unmodifiable map of {@code clanId → kill count}. */
    @NotNull
    Map<String, Integer> getAllClanKillCounts();

    // ── Enums ─────────────────────────────────────────────────

    enum Format {
        LAST_STANDING,
        KILL_COUNT
    }

    enum State {
        REGISTRATION,
        COUNTDOWN,
        ACTIVE,
        ENDED
    }
}