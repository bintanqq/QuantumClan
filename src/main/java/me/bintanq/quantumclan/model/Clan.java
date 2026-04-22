package me.bintanq.quantumclan.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory model representing a Clan.
 * Kept in ClanManager's cache — all fields are the authoritative in-memory state.
 * DAO reads/writes sync this with SQLite.
 */
public class Clan {

    // ── Identity ──────────────────────────────────────────────
    private final String id;          // UUID string, PK
    private String name;
    private String tag;               // max 8 chars
    private String tagColor;          // MiniMessage color tag e.g. "<gold>"
    private UUID leaderUuid;

    // ── Stats ─────────────────────────────────────────────────
    private int level;
    private long money;               // Clan Money (kas bersama)
    private int reputation;

    // ── Shield ────────────────────────────────────────────────
    private Instant shieldUntil;      // null = no active shield

    // ── Clan Hall ─────────────────────────────────────────────
    private String hallWorld;
    private double hallX, hallY, hallZ;

    // ── Timestamps ────────────────────────────────────────────
    private final Instant createdAt;

    // ── Runtime-only (not persisted directly) ─────────────────
    /** Member UUIDs — loaded from clan_members table into cache. */
    private final CopyOnWriteArrayList<UUID> memberUuids = new CopyOnWriteArrayList<>();

    /** Clan homes — loaded from clan_homes table into cache. */
    private final CopyOnWriteArrayList<ClanHome> homes = new CopyOnWriteArrayList<>();

    // ── Constructor ───────────────────────────────────────────

    public Clan(String id, String name, String tag, String tagColor,
                int level, long money, int reputation, UUID leaderUuid,
                Instant shieldUntil, String hallWorld,
                double hallX, double hallY, double hallZ,
                Instant createdAt) {
        this.id          = id;
        this.name        = name;
        this.tag         = tag;
        this.tagColor    = tagColor;
        this.level       = level;
        this.money       = money;
        this.reputation  = reputation;
        this.leaderUuid  = leaderUuid;
        this.shieldUntil = shieldUntil;
        this.hallWorld   = hallWorld;
        this.hallX       = hallX;
        this.hallY       = hallY;
        this.hallZ       = hallZ;
        this.createdAt   = createdAt;
    }

    /** Convenience constructor for new clan creation. */
    public static Clan create(String name, String tag, UUID leaderUuid) {
        return new Clan(
                UUID.randomUUID().toString(),
                name, tag, "",
                1, 0, 0,
                leaderUuid,
                null,
                null, 0, 0, 0,
                Instant.now()
        );
    }

    // ── Shield ────────────────────────────────────────────────

    public boolean hasActiveShield() {
        return shieldUntil != null && Instant.now().isBefore(shieldUntil);
    }

    public void applyShield(long durationSeconds) {
        this.shieldUntil = Instant.now().plusSeconds(durationSeconds);
    }

    // ── Members ───────────────────────────────────────────────

    public void addMemberUuid(UUID uuid) {
        if (!memberUuids.contains(uuid)) memberUuids.add(uuid);
    }

    public void removeMemberUuid(UUID uuid) {
        memberUuids.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return memberUuids.contains(uuid);
    }

    public List<UUID> getMemberUuids() {
        return Collections.unmodifiableList(memberUuids);
    }

    public int getMemberCount() {
        return memberUuids.size();
    }

    // ── Homes ─────────────────────────────────────────────────

    public void addHome(ClanHome home) {
        homes.removeIf(h -> h.getName().equalsIgnoreCase(home.getName()));
        homes.add(home);
    }

    public void removeHome(String name) {
        homes.removeIf(h -> h.getName().equalsIgnoreCase(name));
    }

    public ClanHome getHome(String name) {
        return homes.stream()
                .filter(h -> h.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<ClanHome> getHomes() {
        return Collections.unmodifiableList(homes);
    }

    public int getHomeCount() {
        return homes.size();
    }

    // ── Economy ───────────────────────────────────────────────

    public boolean hasMoney(long amount) {
        return money >= amount;
    }

    public void addMoney(long amount) {
        this.money += amount;
    }

    public boolean spendMoney(long amount) {
        if (money < amount) return false;
        this.money -= amount;
        return true;
    }

    // ── Tag display ───────────────────────────────────────────

    /**
     * Returns the colored tag string in MiniMessage format.
     * e.g. "<gold>[NWF]</gold>"
     */
    public String getColoredTag() {
        if (tagColor == null || tagColor.isBlank()) {
            return "[" + tag + "]";
        }
        return tagColor + "[" + tag + "]" + "<reset>";
    }

    // ── Nested: ClanHome ──────────────────────────────────────

    public static class ClanHome {
        private final String id;
        private final String clanId;
        private final String name;
        private final String world;
        private final double x, y, z;
        private final float yaw, pitch;

        public ClanHome(String id, String clanId, String name,
                        String world, double x, double y, double z,
                        float yaw, float pitch) {
            this.id     = id;
            this.clanId = clanId;
            this.name   = name;
            this.world  = world;
            this.x      = x;
            this.y      = y;
            this.z      = z;
            this.yaw    = yaw;
            this.pitch  = pitch;
        }

        public static ClanHome create(String clanId, String name, Location location) {
            return new ClanHome(
                    UUID.randomUUID().toString(),
                    clanId, name,
                    location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch()
            );
        }

        public Location toBukkitLocation() {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }

        public String getId()     { return id; }
        public String getClanId() { return clanId; }
        public String getName()   { return name; }
        public String getWorld()  { return world; }
        public double getX()      { return x; }
        public double getY()      { return y; }
        public double getZ()      { return z; }
        public float  getYaw()    { return yaw; }
        public float  getPitch()  { return pitch; }
    }

    // ── Getters / Setters ─────────────────────────────────────

    public String  getId()          { return id; }
    public String  getName()        { return name; }
    public void    setName(String n){ this.name = n; }

    public String  getTag()         { return tag; }
    public void    setTag(String t) { this.tag = t; }

    public String  getTagColor()             { return tagColor; }
    public void    setTagColor(String color) { this.tagColor = color; }

    public UUID    getLeaderUuid()           { return leaderUuid; }
    public void    setLeaderUuid(UUID uuid)  { this.leaderUuid = uuid; }

    public int     getLevel()               { return level; }
    public void    setLevel(int level)      { this.level = level; }

    public long    getMoney()               { return money; }
    public void    setMoney(long money)     { this.money = money; }

    public int     getReputation()          { return reputation; }
    public void    setReputation(int rep)   { this.reputation = rep; }
    public void    addReputation(int amount){ this.reputation += amount; }

    public Instant getShieldUntil()         { return shieldUntil; }
    public void    setShieldUntil(Instant i){ this.shieldUntil = i; }

    public String  getHallWorld()           { return hallWorld; }
    public void    setHallWorld(String w)   { this.hallWorld = w; }

    public double  getHallX()              { return hallX; }
    public void    setHallX(double x)      { this.hallX = x; }

    public double  getHallY()              { return hallY; }
    public void    setHallY(double y)      { this.hallY = y; }

    public double  getHallZ()              { return hallZ; }
    public void    setHallZ(double z)      { this.hallZ = z; }

    public Instant getCreatedAt()          { return createdAt; }

    @Override
    public String toString() {
        return "Clan{id='" + id + "', name='" + name + "', tag='" + tag +
                "', level=" + level + ", members=" + memberUuids.size() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Clan other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}