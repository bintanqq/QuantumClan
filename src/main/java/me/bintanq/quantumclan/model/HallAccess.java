package me.bintanq.quantumclan.model;

import java.time.Instant;

/**
 * Represents a clan's hall access record.
 * Maps to a row in clan_hall_access table.
 */
public class HallAccess {

    private final String clanId;
    private final Instant purchasedAt;
    /** null = permanent access */
    private final Instant expiresAt;
    private boolean active;

    public HallAccess(String clanId, Instant purchasedAt, Instant expiresAt, boolean active) {
        this.clanId = clanId;
        this.purchasedAt = purchasedAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    /** Returns true if access is active and not expired. */
    public boolean isValid() {
        if (!active) return false;
        if (expiresAt == null) return true; // permanent
        return Instant.now().isBefore(expiresAt);
    }

    public String getClanId()      { return clanId; }
    public Instant getPurchasedAt(){ return purchasedAt; }
    public Instant getExpiresAt()  { return expiresAt; }
    public boolean isActive()      { return active; }
    public void setActive(boolean v){ this.active = v; }

    @Override
    public String toString() {
        return "HallAccess{clanId='" + clanId + "', expiresAt=" + expiresAt + ", active=" + active + "}";
    }
}