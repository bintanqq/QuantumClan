package me.bintanq.quantumclan.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main API interface for QuantumClan.
 *
 * <p>Obtain the instance after QuantumClan has been enabled:</p>
 * <pre>{@code
 * QuantumClanAPI api = QuantumClanProvider.getAPI();
 * }</pre>
 *
 * <p>All methods that touch the database return {@link CompletableFuture}.
 * Callbacks are dispatched on the main server thread unless otherwise noted.</p>
 */
public interface QuantumClanAPI {

    // ── Clan queries ──────────────────────────────────────────

    /**
     * Returns the clan the given player belongs to, or {@code null} if not in a clan.
     * This is an in-memory cache lookup — instant, no I/O.
     */
    @Nullable
    ClanAPI getClan(@NotNull UUID playerUuid);

    /**
     * Returns the clan the given player belongs to, or {@code null} if not in a clan.
     * Convenience overload for online players.
     */
    @Nullable
    default ClanAPI getClan(@NotNull Player player) {
        return getClan(player.getUniqueId());
    }

    /**
     * Returns the clan with the given ID, or {@code null} if not found.
     * Case-sensitive clan ID (UUID string).
     */
    @Nullable
    ClanAPI getClanById(@NotNull String clanId);

    /**
     * Returns the clan with the given name, or {@code null} if not found.
     * Case-insensitive.
     */
    @Nullable
    ClanAPI getClanByName(@NotNull String name);

    /**
     * Returns the clan with the given tag, or {@code null} if not found.
     * Case-insensitive.
     */
    @Nullable
    ClanAPI getClanByTag(@NotNull String tag);

    /**
     * Returns an unmodifiable view of all loaded clans.
     */
    @NotNull
    Collection<? extends ClanAPI> getAllClans();

    /**
     * Returns the total number of clans currently loaded.
     */
    int getClanCount();

    // ── Member queries ────────────────────────────────────────

    /**
     * Returns {@code true} if the given player is in any clan.
     */
    boolean isInClan(@NotNull UUID playerUuid);

    /**
     * Returns the clan member data for the given UUID, or {@code null} if not in a clan.
     */
    @Nullable
    ClanMemberAPI getMember(@NotNull UUID playerUuid);

    // ── Leaderboard ───────────────────────────────────────────

    /**
     * Returns the current leaderboard snapshot (ordered by reputation, descending).
     * Refreshed on a configurable interval — no direct DB query.
     *
     * @param limit Max number of clans to return (1–50). Clamped to config max.
     */
    @NotNull
    java.util.List<? extends ClanAPI> getLeaderboard(int limit);

    /**
     * Returns the 1-based leaderboard rank of the given clan, or {@code -1} if unranked.
     */
    int getClanRank(@NotNull String clanId);

    // ── Economy helpers ───────────────────────────────────────

    /**
     * Returns the built-in Coins balance for the given player.
     * Async — result delivered on the calling thread of the CompletableFuture.
     */
    @NotNull
    CompletableFuture<Long> getCoins(@NotNull UUID playerUuid);

    /**
     * Grants the given amount of built-in Coins to the player.
     * Returns {@code true} on success.
     */
    @NotNull
    CompletableFuture<Boolean> grantCoins(@NotNull UUID playerUuid, long amount, @NotNull String reason);

    /**
     * Deducts the given amount of built-in Coins from the player.
     * Returns {@code false} if the player does not have enough.
     */
    @NotNull
    CompletableFuture<Boolean> deductCoins(@NotNull UUID playerUuid, long amount, @NotNull String reason);

    // ── Reputation ────────────────────────────────────────────

    /**
     * Adds reputation to the given clan and updates the leaderboard cache.
     * Returns {@code true} on success.
     */
    @NotNull
    CompletableFuture<Boolean> addReputation(@NotNull String clanId, int amount);

    // ── Contribution points ───────────────────────────────────

    /**
     * Adds contribution points to the given player's clan member record.
     * No-op and returns {@code false} if the player is not in a clan.
     */
    @NotNull
    CompletableFuture<Boolean> addContributionPoints(@NotNull UUID playerUuid, int points);

    // ── War state ─────────────────────────────────────────────

    /**
     * Returns {@code true} if a war session is currently active.
     */
    boolean isWarActive();

    /**
     * Returns the current war session, or {@code null} if no war is running.
     */
    @Nullable
    WarSessionAPI getActiveWar();

    // ── Plugin version ────────────────────────────────────────

    /**
     * Returns the plugin version string (e.g. {@code "1.0.0"}).
     */
    @NotNull
    String getVersion();
}