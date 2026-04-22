package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and caches all war.yml configuration values.
 */
public class WarConfigManager {

    private final QuantumClan plugin;
    private FileConfiguration cfg;

    public WarConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "war.yml");
        if (!file.exists()) plugin.saveResource("war.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    // ── Schedule ──────────────────────────────────────────────

    public String getScheduleType()   { return cfg.getString("schedule.type", "WEEKLY").toUpperCase(); }
    public String getScheduleTime()   { return cfg.getString("schedule.time", "20:00"); }
    public String getScheduleDay()    { return cfg.getString("schedule.day", "SATURDAY").toUpperCase(); }
    public int    getDayOfMonth()     { return cfg.getInt("schedule.day-of-month", 1); }

    // ── War Settings ──────────────────────────────────────────

    public WarSession.Format getWarFormat() {
        String fmt = cfg.getString("format", "KILL_COUNT").toUpperCase();
        try { return WarSession.Format.valueOf(fmt); }
        catch (Exception e) { return WarSession.Format.KILL_COUNT; }
    }

    public int getWarDurationMinutes()      { return cfg.getInt("duration-minutes", 20); }
    public int getMinMembersOnline()        { return cfg.getInt("min-members-online", 3); }
    public int getMaxClans()               { return cfg.getInt("max-clans", 0); }
    public int getRegistrationOpenMinutes() { return cfg.getInt("registration-open-minutes", 60); }
    public int getCountdownSeconds()        { return cfg.getInt("countdown-seconds", 30); }

    // ── Arena ─────────────────────────────────────────────────

    public Location getArenaLocation() {
        String world = cfg.getString("arena.world", "world");
        double x = cfg.getDouble("arena.x", 0.0);
        double y = cfg.getDouble("arena.y", 64.0);
        double z = cfg.getDouble("arena.z", 0.0);
        float  yaw   = (float) cfg.getDouble("arena.yaw", 0.0);
        float  pitch = (float) cfg.getDouble("arena.pitch", 0.0);

        var w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public double getArenaRadius() { return cfg.getDouble("arena.radius", 100.0); }

    /** Saves arena location back to war.yml (called by /qclanadmin war setarena). */
    public void saveArenaLocation(Location loc) {
        cfg.set("arena.world", loc.getWorld().getName());
        cfg.set("arena.x",     loc.getX());
        cfg.set("arena.y",     loc.getY());
        cfg.set("arena.z",     loc.getZ());
        cfg.set("arena.yaw",   loc.getYaw());
        cfg.set("arena.pitch", loc.getPitch());
        File file = new File(plugin.getDataFolder(), "war.yml");
        try { cfg.save(file); } catch (Exception e) {
            plugin.getLogger().warning("[WarConfig] Failed to save arena location: " + e.getMessage());
        }
    }

    // ── Rewards ───────────────────────────────────────────────

    public int getReputationReward() { return cfg.getInt("reputation-reward", 20); }
    public double getRewardBalance() { return cfg.getDouble("rewards.balance", 1000.0); }
    public int    getRewardCoins()   { return cfg.getInt("rewards.coins", 500); }

    /** Returns a list of reward items as {material, amount} maps. */
    public List<RewardItem> getRewardItems() {
        List<RewardItem> items = new ArrayList<>();
        List<?> list = cfg.getList("rewards.items");
        if (list == null) return items;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> m)) continue;
            String matName = String.valueOf(m.getOrDefault("material", "DIAMOND")).toUpperCase();
            int amount     = parseIntObj(m.get("amount"), 1);
            try {
                Material mat = Material.valueOf(matName);
                items.add(new RewardItem(mat, amount));
            } catch (Exception ignored) {}
        }
        return items;
    }

    // ── Broadcast ─────────────────────────────────────────────

    public long   getKillBroadcastInterval() { return cfg.getLong("kill-broadcast-interval", 200L); }
    public String getBroadcastWarStart()      { return cfg.getString("broadcast.war-start", "<gold>War dimulai!"); }
    public String getBroadcastWarEnd()        { return cfg.getString("broadcast.war-end", "<gold>War selesai! Pemenang: {winner}"); }
    public String getBroadcastWarNoWinner()   { return cfg.getString("broadcast.war-no-winner", "<gold>War selesai! Tidak ada pemenang!"); }
    public String getBroadcastKillCount()     { return cfg.getString("broadcast.kill-count", "<gray>Skor: {scores}"); }
    public String getBroadcastRegistrationOpen()   { return cfg.getString("broadcast.registration-open", "<gold>Registrasi war dibuka!"); }
    public String getBroadcastRegistrationClosed() { return cfg.getString("broadcast.registration-closed", "<gold>Registrasi ditutup. War dalam {seconds} detik!"); }

    // ── Nested: RewardItem ────────────────────────────────────

    public static class RewardItem {
        public final Material material;
        public final int amount;
        public RewardItem(Material material, int amount) {
            this.material = material;
            this.amount   = amount;
        }
    }

    // ── Helper ────────────────────────────────────────────────

    private int parseIntObj(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
        return def;
    }
}