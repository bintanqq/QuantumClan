package me.bintanq.quantumclan.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a configurable role loaded from roles.yml.
 *
 * Hierarchy is determined by the order roles appear in roles.yml:
 * index 0 = highest (Leader), higher index = lower rank.
 *
 * The "leader" role always has every permission set to true
 * regardless of what is written in roles.yml.
 */
public class ClanRole {

    // Known permission node keys
    public static final String PERM_KICK           = "can-kick";
    public static final String PERM_INVITE         = "can-invite";
    public static final String PERM_UPGRADE        = "can-upgrade";
    public static final String PERM_ACCESS_SHOP    = "can-access-shop";
    public static final String PERM_SET_HOME       = "can-set-home";
    public static final String PERM_DELETE_HOME    = "can-delete-home";
    public static final String PERM_DEPOSIT        = "can-deposit";
    public static final String PERM_DECLARE_BOUNTY = "can-declare-bounty";
    public static final String PERM_MANAGE_WAR     = "can-manage-war";
    public static final String PERM_ANNOUNCE       = "can-announce";
    public static final String PERM_SET_ROLE       = "can-set-role";
    public static final String PERM_TRANSFER       = "can-transfer";
    public static final String PERM_DISBAND        = "can-disband";

    private final String name;           // internal key, e.g. "leader"
    private final String displayName;    // MiniMessage, e.g. "<gold><bold>Leader"
    private final String color;          // hex or MiniMessage color
    private final int hierarchyIndex;    // lower = higher rank
    private final Map<String, Boolean> permissions;

    public ClanRole(String name, String displayName, String color,
                    int hierarchyIndex, Map<String, Boolean> permissions) {
        this.name            = name;
        this.displayName     = displayName;
        this.color           = color;
        this.hierarchyIndex  = hierarchyIndex;
        this.permissions     = Collections.unmodifiableMap(new HashMap<>(permissions));
    }

    // ── Permission checks ─────────────────────────────────────

    /**
     * Returns true if this role has the given permission node.
     * The "leader" role always returns true for any node.
     */
    public boolean hasPermission(String node) {
        if ("leader".equalsIgnoreCase(name)) return true;
        return permissions.getOrDefault(node, false);
    }

    public boolean canKick()          { return hasPermission(PERM_KICK); }
    public boolean canInvite()        { return hasPermission(PERM_INVITE); }
    public boolean canUpgrade()       { return hasPermission(PERM_UPGRADE); }
    public boolean canAccessShop()    { return hasPermission(PERM_ACCESS_SHOP); }
    public boolean canSetHome()       { return hasPermission(PERM_SET_HOME); }
    public boolean canDeleteHome()    { return hasPermission(PERM_DELETE_HOME); }
    public boolean canDeposit()       { return hasPermission(PERM_DEPOSIT); }
    public boolean canDeclareBounty() { return hasPermission(PERM_DECLARE_BOUNTY); }
    public boolean canManageWar()     { return hasPermission(PERM_MANAGE_WAR); }
    public boolean canAnnounce()      { return hasPermission(PERM_ANNOUNCE); }
    public boolean canSetRole()       { return hasPermission(PERM_SET_ROLE); }
    public boolean canTransfer()      { return hasPermission(PERM_TRANSFER); }
    public boolean canDisband()       { return hasPermission(PERM_DISBAND); }

    // ── Hierarchy ─────────────────────────────────────────────

    /**
     * Returns true if this role outranks (is higher than) the other role.
     * Lower hierarchyIndex = higher rank.
     */
    public boolean outranks(ClanRole other) {
        return this.hierarchyIndex < other.hierarchyIndex;
    }

    /**
     * Returns true if this role is the same rank as another.
     */
    public boolean sameRankAs(ClanRole other) {
        return this.hierarchyIndex == other.hierarchyIndex;
    }

    // ── Getters ───────────────────────────────────────────────

    public String getName()               { return name; }
    public String getDisplayName()        { return displayName; }
    public String getColor()              { return color; }
    public int    getHierarchyIndex()     { return hierarchyIndex; }
    public Map<String, Boolean> getPermissions() { return permissions; }

    public boolean isLeader() {
        return "leader".equalsIgnoreCase(name);
    }

    @Override
    public String toString() {
        return "ClanRole{name='" + name + "', index=" + hierarchyIndex + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClanRole other)) return false;
        return name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }
}