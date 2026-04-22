package me.bintanq.quantumclan.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a clan member's data.
 */
public interface ClanMemberAPI {

    /** The member's UUID. */
    @NotNull
    UUID getUuid();

    /** The ID of the clan this member belongs to. */
    @NotNull
    String getClanId();

    /**
     * The member's current role name (e.g. {@code "leader"}, {@code "officer"}, {@code "member"}).
     * Matches the {@code name} field in {@code roles.yml}.
     */
    @NotNull
    String getRole();

    /** Total contribution points accumulated by this member. */
    int getContributionPoints();

    /** Instant the member joined the clan. */
    @NotNull
    Instant getJoinedAt();
}