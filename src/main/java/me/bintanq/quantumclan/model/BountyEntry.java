package me.bintanq.quantumclan.model;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory model representing an active or historical bounty.
 * Maps 1-to-1 with a row in the bounties table.
 */
public class BountyEntry {

    public enum Status {
        ACTIVE,
        COMPLETED,
        EXPIRED,
        CANCELLED
    }

    private final String id;
    private final String clanIdPoster;    // clan yang memasang bounty
    private final String clanIdTarget;    // clan target (untuk Clan Shield check)
    private final UUID   targetUuid;      // player yang di-bounty
    private final long   amount;          // jumlah bounty (Vault balance)
    private Status status;
    private boolean headClaimed;          // apakah head sudah di-drop
    private final Instant postedAt;
    private final Instant expiresAt;

    public BountyEntry(String id, String clanIdPoster, String clanIdTarget,
                       UUID targetUuid, long amount, Status status,
                       boolean headClaimed, Instant postedAt, Instant expiresAt) {
        this.id            = id;
        this.clanIdPoster  = clanIdPoster;
        this.clanIdTarget  = clanIdTarget;
        this.targetUuid    = targetUuid;
        this.amount        = amount;
        this.status        = status;
        this.headClaimed   = headClaimed;
        this.postedAt      = postedAt;
        this.expiresAt     = expiresAt;
    }

    /** Factory method for new bounty placement. */
    public static BountyEntry create(String clanIdPoster, String clanIdTarget,
                                     UUID targetUuid, long amount, long expireHours) {
        Instant now = Instant.now();
        return new BountyEntry(
                UUID.randomUUID().toString(),
                clanIdPoster, clanIdTarget,
                targetUuid, amount,
                Status.ACTIVE, false,
                now,
                now.plusSeconds(expireHours * 3600L)
        );
    }

    // ── State checks ──────────────────────────────────────────

    public boolean isActive() {
        return status == Status.ACTIVE && Instant.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return status == Status.ACTIVE && Instant.now().isAfter(expiresAt);
    }

    // ── Getters / Setters ─────────────────────────────────────

    public String  getId()             { return id; }
    public String  getClanIdPoster()   { return clanIdPoster; }
    public String  getClanIdTarget()   { return clanIdTarget; }
    public UUID    getTargetUuid()     { return targetUuid; }
    public long    getAmount()         { return amount; }
    public Status  getStatus()         { return status; }
    public void    setStatus(Status s) { this.status = s; }
    public boolean isHeadClaimed()     { return headClaimed; }
    public void    setHeadClaimed(boolean b) { this.headClaimed = b; }
    public Instant getPostedAt()       { return postedAt; }
    public Instant getExpiresAt()      { return expiresAt; }

    @Override
    public String toString() {
        return "BountyEntry{id='" + id + "', target=" + targetUuid +
                ", amount=" + amount + ", status=" + status + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BountyEntry other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}