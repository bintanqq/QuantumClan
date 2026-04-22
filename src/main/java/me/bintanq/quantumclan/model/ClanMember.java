package me.bintanq.quantumclan.model;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory model representing a single clan member.
 * Maps 1-to-1 with a row in clan_members table.
 */
public class ClanMember {

    private final UUID uuid;
    private String clanId;
    private String role;          // role name matching roles.yml key
    private int contributionPoints;
    private final Instant joinedAt;

    public ClanMember(UUID uuid, String clanId, String role,
                      int contributionPoints, Instant joinedAt) {
        this.uuid               = uuid;
        this.clanId             = clanId;
        this.role               = role;
        this.contributionPoints = contributionPoints;
        this.joinedAt           = joinedAt;
    }

    /** Factory method for newly joining members. */
    public static ClanMember create(UUID uuid, String clanId, String defaultRole) {
        return new ClanMember(uuid, clanId, defaultRole, 0, Instant.now());
    }

    // ── Contribution ──────────────────────────────────────────

    public void addContribution(int points) {
        this.contributionPoints += points;
    }

    public boolean spendContribution(int points) {
        if (contributionPoints < points) return false;
        contributionPoints -= points;
        return true;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public UUID    getUuid()                        { return uuid; }
    public String  getClanId()                      { return clanId; }
    public void    setClanId(String clanId)         { this.clanId = clanId; }
    public String  getRole()                        { return role; }
    public void    setRole(String role)             { this.role = role; }
    public int     getContributionPoints()          { return contributionPoints; }
    public void    setContributionPoints(int pts)   { this.contributionPoints = pts; }
    public Instant getJoinedAt()                    { return joinedAt; }

    @Override
    public String toString() {
        return "ClanMember{uuid=" + uuid + ", clan=" + clanId +
                ", role=" + role + ", points=" + contributionPoints + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClanMember other)) return false;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}