package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and caches all values from config.yml.
 * Re-created via QuantumClan#reload().
 */
public class ConfigManager {

    private final QuantumClan plugin;
    private final FileConfiguration cfg;

    private final Map<Integer, LevelData> levelDataMap = new HashMap<>();
    /** level → vault-rows (1-6) */
    private final Map<Integer, Integer> vaultRowsMap = new HashMap<>();
    private int maxLevel = 10;

    public ConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
        loadLevelRequirements();
    }

    // ── Level requirements ────────────────────────────────────

    private void loadLevelRequirements() {
        List<?> list = cfg.getList("level-requirements");
        if (list == null) return;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            int level      = toInt(map.get("level"), 1);
            long cost      = toLong(map.get("cost"), 0L);
            int maxMembers = toInt(map.get("max-members"), 10);
            int maxHomes   = toInt(map.get("max-homes"), 1);
            int vaultRows  = toInt(map.get("vault-rows"), 1);
            levelDataMap.put(level, new LevelData(level, cost, maxMembers, maxHomes));
            vaultRowsMap.put(level, Math.max(1, Math.min(6, vaultRows)));
        }
        maxLevel = cfg.getInt("max-level", levelDataMap.size());
    }

    public long getLevelCost(int level)    { LevelData d = levelDataMap.get(level); return d != null ? d.cost : Long.MAX_VALUE; }
    public int  getMaxMembers(int level)   { LevelData d = levelDataMap.get(level); return d != null ? d.maxMembers : 10; }
    public int  getMaxHomes(int level)     { LevelData d = levelDataMap.get(level); return d != null ? d.maxHomes : 1; }
    public int  getMaxLevel()              { return maxLevel; }

    /**
     * Returns the number of vault rows (1-6) for the given clan level.
     * Falls back to the highest configured level <= requested level,
     * or 1 if nothing is configured.
     */
    public int getVaultRows(int level) {
        Integer rows = vaultRowsMap.get(level);
        if (rows != null) return rows;
        int best = 1;
        for (Map.Entry<Integer, Integer> e : vaultRowsMap.entrySet()) {
            if (e.getKey() <= level) best = Math.max(best, e.getValue());
        }
        return Math.max(1, Math.min(6, best));
    }

    // ── Database ──────────────────────────────────────────────

    public String getDatabaseType()         { return cfg.getString("database.type", "SQLITE").toUpperCase(); }
    public String getSqliteFile()           { return cfg.getString("database.sqlite.file", "quantumclan.db"); }
    public String getMysqlHost()            { return cfg.getString("database.mysql.host", "localhost"); }
    public int    getMysqlPort()            { return cfg.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase()        { return cfg.getString("database.mysql.database", "quantumclan"); }
    public String getMysqlUsername()        { return cfg.getString("database.mysql.username", "root"); }
    public String getMysqlPassword()        { return cfg.getString("database.mysql.password", ""); }
    public int    getMysqlPoolSize()        { return cfg.getInt("database.mysql.pool-size", 10); }
    public long   getMysqlConnectionTimeout(){ return cfg.getLong("database.mysql.connection-timeout", 30_000L); }

    // ── Feature Toggles ───────────────────────────────────────

    public boolean isFeatureEnabled(String feature) {
        return cfg.getBoolean("features." + feature, true);
    }

    public boolean isAnnouncementsEnabled()  { return isFeatureEnabled("announcements"); }
    public boolean isBountiesEnabled()       { return isFeatureEnabled("bounties"); }
    public boolean isWarsEnabled()           { return isFeatureEnabled("wars"); }
    public boolean isLevelingEnabled()       { return isFeatureEnabled("leveling"); }
    public boolean isHomesEnabled()          { return isFeatureEnabled("homes"); }
    public boolean isClanShopEnabled()       { return isFeatureEnabled("clan-shop"); }
    public boolean isContributionsEnabled()  { return isFeatureEnabled("contributions"); }
    public boolean isCoinsShopEnabled()      { return isFeatureEnabled("coins-shop"); }
    public boolean isReputationEnabled()     { return isFeatureEnabled("reputation"); }
    public boolean isXpBoostEnabled()        { return isFeatureEnabled("xp-boost"); }
    public boolean isSpyScrollEnabled()      { return isFeatureEnabled("spy-scroll"); }
    public boolean isDeathProtectionEnabled(){ return isFeatureEnabled("death-protection"); }
    public boolean isClanShieldEnabled()     { return isFeatureEnabled("clan-shield"); }
    public boolean isVaultEnabled()          { return isFeatureEnabled("vault"); }

    // ── Clan creation ─────────────────────────────────────────

    public double getClanCreationCost() { return cfg.getDouble("clan-creation-cost", 5000.0); }
    public int    getMaxNameLength()    { return cfg.getInt("max-name-length", 24); }
    public int    getMaxTagLength()     { return cfg.getInt("max-tag-length", 8); }
    public int    getChatInputTimeout() { return cfg.getInt("chat-input-timeout", 60); }

    // ── Economy ───────────────────────────────────────────────

    public String getEconomyProvider() { return cfg.getString("economy-provider", "VAULT"); }

    // ── Coins ─────────────────────────────────────────────────

    public String getCoinsName() { return cfg.getString("coins-name", "Coins"); }

    // ── Chat tag ──────────────────────────────────────────────

    public boolean isClanChatTagEnabled() { return cfg.getBoolean("chat.tag-enabled", true); }
    public String  getChatTagPosition()   { return cfg.getString("chat.tag-position", "PREFIX").toUpperCase(); }
    public String  getChatTagFormat()     { return cfg.getString("chat.tag-format", "<gray>[{tag}<gray>] "); }

    // ── Cooldowns ─────────────────────────────────────────────

    public int getTeleportCooldown()        { return cfg.getInt("teleport-cooldown", 3); }
    public int getHomeTeleportCooldown()    { return cfg.getInt("home-teleport-cooldown", 30); }
    public int getAnnounceCooldown()        { return cfg.getInt("announce-cooldown", 3600); }
    public int getDeathProtectionCooldown() { return cfg.getInt("death-protection-cooldown", 3600); }

    // ── Bounty ────────────────────────────────────────────────

    public int    getBountyExpireHours() { return cfg.getInt("bounty-expire-hours", 48); }
    public double getBountyMinAmount()   { return cfg.getDouble("bounty-min-amount", 100.0); }

    // ── Clan Hall ─────────────────────────────────────────────

    public boolean isClanHallEnabled() { return cfg.getBoolean("features.clan-hall", false) || cfg.getBoolean("clan-hall.enabled", false); }
    public String  getClanHallEngine() { return cfg.getString("clan-hall.engine", "AUTO"); }

    // ── Disband ───────────────────────────────────────────────

    public boolean isDisbandRefundTreasury()    { return cfg.getBoolean("disband.refund-treasury", true); }
    public boolean isDisbandRefundCreationCost() { return cfg.getBoolean("disband.refund-creation-cost", false); }

    // ── Leaderboard ───────────────────────────────────────────

    public long getLeaderboardCacheInterval() { return cfg.getLong("leaderboard-cache-interval", 6000L); }
    public int  getLeaderboardSize()          { return cfg.getInt("leaderboard-size", 50); }

    // ── Reputation weights ────────────────────────────────────

    public int getReputationBountyComplete() { return cfg.getInt("reputation-weights.bounty-complete", 5); }
    public int getReputationWarWin()         { return cfg.getInt("reputation-weights.war-win", 20); }

    // ── Contribution weights ──────────────────────────────────

    public int getContribDepositPerUnit()    { return cfg.getInt("contribution-weights.deposit-per-unit", 1); }
    public int getContribDepositAmountUnit() { return cfg.getInt("contribution-weights.deposit-amount-unit", 1000); }
    public int getContribBountyComplete()    { return cfg.getInt("contribution-weights.bounty-complete", 10); }
    public int getContribWarWin()            { return cfg.getInt("contribution-weights.war-win", 25); }

    // ── XP Boost ──────────────────────────────────────────────

    public double getXpBoostMultiplier() { return cfg.getDouble("xp-boost-multiplier", 2.0); }

    // ── Spy Scroll ────────────────────────────────────────────

    public int getSpyScrollDuration()       { return cfg.getInt("spy-scroll-duration", 300); }
    public int getSpyScrollUpdateInterval() { return cfg.getInt("spy-scroll-update-interval", 60); }

    // ── Nested: LevelData ─────────────────────────────────────

    public static class LevelData {
        public final int  level;
        public final long cost;
        public final int  maxMembers;
        public final int  maxHomes;

        public LevelData(int level, long cost, int maxMembers, int maxHomes) {
            this.level      = level;
            this.cost       = cost;
            this.maxMembers = maxMembers;
            this.maxHomes   = maxHomes;
        }
    }

    // ── Conversion helpers ────────────────────────────────────

    private int toInt(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
        return def;
    }

    private long toLong(Object obj, long def) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) { try { return Long.parseLong(s); } catch (Exception e) { return def; } }
        return def;
    }
}