package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and caches all values from config.yml.
 * Reloaded via QuantumClan#reload() → new instance created.
 */
public class ConfigManager {

    private final QuantumClan plugin;
    private final FileConfiguration cfg;

    // ── Level cache ───────────────────────────────────────────
    // level (1-based) → LevelData
    private final Map<Integer, LevelData> levelDataMap = new HashMap<>();
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
            levelDataMap.put(level, new LevelData(level, cost, maxMembers, maxHomes));
        }
        maxLevel = cfg.getInt("max-level", levelDataMap.size());
    }

    public long getLevelCost(int level) {
        LevelData data = levelDataMap.get(level);
        return data != null ? data.cost : Long.MAX_VALUE;
    }

    public int getMaxMembers(int level) {
        LevelData data = levelDataMap.get(level);
        return data != null ? data.maxMembers : 10;
    }

    public int getMaxHomes(int level) {
        LevelData data = levelDataMap.get(level);
        return data != null ? data.maxHomes : 1;
    }

    public int getMaxLevel() { return maxLevel; }

    // ── Clan creation ─────────────────────────────────────────

    public double getClanCreationCost()  { return cfg.getDouble("clan-creation-cost", 5000.0); }
    public int    getMaxNameLength()     { return cfg.getInt("max-name-length", 24); }
    public int    getMaxTagLength()      { return cfg.getInt("max-tag-length", 8); }
    public int    getChatInputTimeout()  { return cfg.getInt("chat-input-timeout", 60); }

    // ── Economy ───────────────────────────────────────────────

    public String getEconomyProvider()        { return cfg.getString("economy-provider", "VAULT"); }
    public String getGemsEconomyCurrency()    { return cfg.getString("gemseconomy-currency", "dollars"); }

    // ── Chat ──────────────────────────────────────────────────

    public boolean isClanChatEnabled()  { return cfg.getBoolean("chat.enabled", true); }
    public String  getChatTagPosition() { return cfg.getString("chat.position", "PREFIX"); }
    public String  getChatFormat()      { return cfg.getString("chat.format",
            "<gray>[{tag}<gray>] <white>{player}<dark_gray>: <gray>{message}"); }

    // ── Cooldowns ─────────────────────────────────────────────

    public int getTeleportCooldown()     { return cfg.getInt("teleport-cooldown", 3); }
    public int getHomeTeleportCooldown() { return cfg.getInt("home-teleport-cooldown", 30); }
    public int getAnnounceCooldown()     { return cfg.getInt("announce-cooldown", 3600); }
    public int getDeathProtectionCooldown() { return cfg.getInt("death-protection-cooldown", 3600); }

    // ── Bounty ────────────────────────────────────────────────

    public int    getBountyExpireHours() { return cfg.getInt("bounty-expire-hours", 48); }
    public double getBountyMinAmount()   { return cfg.getDouble("bounty-min-amount", 100.0); }

    // ── Clan Hall ─────────────────────────────────────────────

    public boolean isClanHallEnabled()  { return cfg.getBoolean("clan-hall.enabled", false); }
    public String  getClanHallEngine()  { return cfg.getString("clan-hall.engine", "AUTO"); }

    // ── Leaderboard ───────────────────────────────────────────

    public long getLeaderboardCacheInterval() { return cfg.getLong("leaderboard-cache-interval", 6000L); }
    public int  getLeaderboardSize()          { return cfg.getInt("leaderboard-size", 50); }

    // ── Reputation weights ────────────────────────────────────

    public int getReputationBountyComplete() {
        return cfg.getInt("reputation-weights.bounty-complete", 5);
    }
    public int getReputationWarWin() {
        return cfg.getInt("reputation-weights.war-win", 20);
    }

    // ── Contribution weights ──────────────────────────────────

    public int getContribDepositPerThousand() {
        return cfg.getInt("contribution-weights.deposit-per-thousand", 1);
    }
    public int getContribBountyComplete() {
        return cfg.getInt("contribution-weights.bounty-complete", 10);
    }
    public int getContribWarWin() {
        return cfg.getInt("contribution-weights.war-win", 25);
    }

    // ── Hologram ──────────────────────────────────────────────

    public long   getHologramUpdateInterval() { return cfg.getLong("hologram.update-interval", 6000L); }
    public String getHologramWorld()          { return cfg.getString("hologram.location.world", "world"); }
    public double getHologramX()              { return cfg.getDouble("hologram.location.x", 0.0); }
    public double getHologramY()              { return cfg.getDouble("hologram.location.y", 64.0); }
    public double getHologramZ()              { return cfg.getDouble("hologram.location.z", 0.0); }

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