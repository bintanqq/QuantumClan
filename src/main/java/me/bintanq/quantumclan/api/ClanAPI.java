package me.bintanq.quantumclan.api;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view of a clan, exposed via the QuantumClan API.
 *
 * <p>All data is pulled from the in-memory cache — no database I/O.
 * Do not hold long-lived references to this object; always fetch fresh via
 * {@link QuantumClanAPI#getClanById(String)}.</p>
 */
public interface ClanAPI {

    // ── Identity ──────────────────────────────────────────────

    /** Unique clan ID (UUID string). */
    @NotNull
    String getId();

    /** Display name of the clan. */
    @NotNull
    String getName();

    /** Short tag, e.g. {@code "NWF"}. */
    @NotNull
    String getTag();

    /**
     * MiniMessage color prefix for the tag, e.g. {@code "<gold>"}.
     * Empty string if no color has been purchased.
     */
    @NotNull
    String getTagColor();

    /**
     * Returns the tag with its color applied, without brackets.
     * e.g. {@code "<gold>NWF<reset>"}
     */
    @NotNull
    String getColoredTag();

    /**
     * Returns the tag with brackets and color.
     * e.g. {@code "<gold>[NWF]<reset>"}
     */
    @NotNull
    String getFormattedTag();

    // ── Leader ────────────────────────────────────────────────

    /** UUID of the current clan leader. */
    @NotNull
    UUID getLeaderUuid();

    // ── Stats ─────────────────────────────────────────────────

    /** Current level (1 – max). */
    int getLevel();

    /** Shared clan treasury balance (Clan Money). */
    long getMoney();

    /** Total reputation score. */
    int getReputation();

    // ── Members ───────────────────────────────────────────────

    /** Unmodifiable list of all member UUIDs (including leader). */
    @NotNull
    List<UUID> getMemberUuids();

    /** Total member count. */
    int getMemberCount();

    /** Returns {@code true} if the given UUID is a member of this clan. */
    boolean hasMember(@NotNull UUID uuid);

    // ── Homes ─────────────────────────────────────────────────

    /** Unmodifiable list of set clan homes. */
    @NotNull
    List<ClanHomeAPI> getHomes();

    /** Total number of set homes. */
    int getHomeCount();

    /**
     * Returns the home with the given name (case-insensitive), or {@code null}.
     */
    @Nullable
    ClanHomeAPI getHome(@NotNull String name);

    // ── Shield ────────────────────────────────────────────────

    /** Returns {@code true} if the clan shield is currently active. */
    boolean hasActiveShield();

    /** Returns the instant the shield expires, or {@code null} if no shield. */
    @Nullable
    Instant getShieldUntil();

    // ── Clan Hall ─────────────────────────────────────────────

    /** Returns the world name of the clan hall, or {@code null} if not set. */
    @Nullable
    String getHallWorld();

    /** Returns the clan hall origin X, or {@code 0} if not set. */
    double getHallX();

    /** Returns the clan hall origin Y, or {@code 0} if not set. */
    double getHallY();

    /** Returns the clan hall origin Z, or {@code 0} if not set. */
    double getHallZ();

    // ── Timestamps ────────────────────────────────────────────

    /** Instant the clan was created. */
    @NotNull
    Instant getCreatedAt();

    // ── Nested: ClanHomeAPI ───────────────────────────────────

    /**
     * Read-only view of a single clan home.
     */
    interface ClanHomeAPI {

        /** Unique home ID (UUID string). */
        @NotNull String getId();

        /** The name given to this home (e.g. {@code "base"}, {@code "farm"}). */
        @NotNull String getName();

        /** World name where the home is located. */
        @NotNull String getWorld();

        double getX();
        double getY();
        double getZ();
        float  getYaw();
        float  getPitch();

        /**
         * Resolves a live Bukkit {@link Location}, or {@code null} if the world
         * is not loaded on this server.
         */
        @Nullable Location toBukkitLocation();
    }
}