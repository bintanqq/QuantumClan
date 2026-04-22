package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Reads and exposes all configuration from halls.yml.
 */
public class HallConfigManager {

    private final QuantumClan plugin;
    private FileConfiguration cfg;
    private File file;

    public HallConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "halls.yml");
        if (!file.exists()) {
            plugin.saveResource("halls.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    // ── Top-level ─────────────────────────────────────────────

    public boolean isEnabled() {
        return cfg.getBoolean("hall.enabled", true);
    }

    // ── Purchase ──────────────────────────────────────────────

    public long getPurchaseCost() {
        return cfg.getLong("hall.purchase.cost", 500_000L);
    }

    public void setPurchaseCost(long cost) {
        cfg.set("hall.purchase.cost", cost);
        save();
    }

    public boolean isPermanentMode() {
        return "PERMANENT".equalsIgnoreCase(cfg.getString("hall.purchase.mode", "DURATION"));
    }

    public int getDurationDays() {
        return cfg.getInt("hall.purchase.duration-days", 30);
    }

    // ── Region ────────────────────────────────────────────────

    public String getRegionWorld() {
        return cfg.getString("hall.region.world", "world");
    }

    public double getRegionMinX() { return cfg.getDouble("hall.region.min.x", 0); }
    public double getRegionMinY() { return cfg.getDouble("hall.region.min.y", 64); }
    public double getRegionMinZ() { return cfg.getDouble("hall.region.min.z", 0); }
    public double getRegionMaxX() { return cfg.getDouble("hall.region.max.x", 50); }
    public double getRegionMaxY() { return cfg.getDouble("hall.region.max.y", 100); }
    public double getRegionMaxZ() { return cfg.getDouble("hall.region.max.z", 50); }

    public String getWorldGuardRegionName() {
        return cfg.getString("hall.region.worldguard-region", "quantumclan_hall");
    }

    public void setRegionCorner1(Location loc) {
        cfg.set("hall.region.min.x", loc.getBlockX());
        cfg.set("hall.region.min.y", loc.getBlockY());
        cfg.set("hall.region.min.z", loc.getBlockZ());
        cfg.set("hall.region.world", loc.getWorld().getName());
        save();
    }

    public void setRegionCorner2(Location loc) {
        cfg.set("hall.region.max.x", loc.getBlockX());
        cfg.set("hall.region.max.y", loc.getBlockY());
        cfg.set("hall.region.max.z", loc.getBlockZ());
        save();
    }

    public boolean isInsideRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(getRegionWorld())) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= getRegionMinX() && x <= getRegionMaxX()
                && y >= getRegionMinY() && y <= getRegionMaxY()
                && z >= getRegionMinZ() && z <= getRegionMaxZ();
    }

    public Location getTeleportLocation() {
        World world = Bukkit.getWorld(getRegionWorld());
        if (world == null) return null;
        double x = (getRegionMinX() + getRegionMaxX()) / 2.0;
        double y = getRegionMinY() + 1;
        double z = (getRegionMinZ() + getRegionMaxZ()) / 2.0;
        return new Location(world, x, y, z);
    }

    // ── Vault Block ───────────────────────────────────────────

    /**
     * Returns the location of the vault interaction block,
     * or null if not configured.
     *
     * Players right-clicking this block open their clan vault.
     */
    public Location getVaultBlockLocation() {
        String world = cfg.getString("hall.vault-block.world");
        if (world == null || world.isBlank()) return null;
        double x = cfg.getDouble("hall.vault-block.x", 0);
        double y = cfg.getDouble("hall.vault-block.y", 0);
        double z = cfg.getDouble("hall.vault-block.z", 0);
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    /**
     * Saves the vault block location to halls.yml.
     * Called by /qclanadmin hall setvaultblock.
     */
    public void setVaultBlockLocation(Location loc) {
        cfg.set("hall.vault-block.world", loc.getWorld().getName());
        cfg.set("hall.vault-block.x", loc.getBlockX());
        cfg.set("hall.vault-block.y", loc.getBlockY());
        cfg.set("hall.vault-block.z", loc.getBlockZ());
        save();
    }

    /**
     * Clears the vault block location.
     */
    public void clearVaultBlockLocation() {
        cfg.set("hall.vault-block", null);
        save();
    }

    // ── Schematic ─────────────────────────────────────────────

    public String getSchematicFile() {
        return cfg.getString("hall.schematic.file", "hall_default.nbt");
    }

    public void setSchematicFile(String filename) {
        cfg.set("hall.schematic.file", filename);
        save();
    }

    public Location getSchematicPasteOrigin() {
        String world = cfg.getString("hall.schematic.paste-origin.world", "world");
        double x = cfg.getDouble("hall.schematic.paste-origin.x", 0);
        double y = cfg.getDouble("hall.schematic.paste-origin.y", 64);
        double z = cfg.getDouble("hall.schematic.paste-origin.z", 0);
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    // ── Passive Buffs ─────────────────────────────────────────

    public boolean isPassiveBuffsEnabled() {
        return cfg.getBoolean("hall.passive-buffs.enabled", true);
    }

    public int getBuffRefreshIntervalSeconds() {
        return cfg.getInt("hall.passive-buffs.refresh-interval-seconds", 10);
    }

    public boolean isBuffEnabled(String buffName) {
        return cfg.getBoolean("hall.passive-buffs.buffs." + buffName + ".enabled", false);
    }

    public int getBuffAmplifier(String buffName) {
        return cfg.getInt("hall.passive-buffs.buffs." + buffName + ".amplifier", 0);
    }

    public boolean isBuffAmbient(String buffName) {
        return cfg.getBoolean("hall.passive-buffs.buffs." + buffName + ".ambient", true);
    }

    // ── Discounts ─────────────────────────────────────────────

    public boolean isDiscountsEnabled() {
        return cfg.getBoolean("hall.discounts.enabled", true);
    }

    public double getDiscountMultiplier(String shopType) {
        int pct = cfg.getInt("hall.discounts." + shopType, 0);
        pct = Math.max(0, Math.min(100, pct));
        return 1.0 - (pct / 100.0);
    }

    public int getDiscountPercent(String shopType) {
        return cfg.getInt("hall.discounts." + shopType, 0);
    }

    // ── NPC Points ────────────────────────────────────────────

    public ConfigurationSection getNpcPointsSection() {
        ConfigurationSection sec = cfg.getConfigurationSection("hall.npc-points");
        if (sec == null) {
            cfg.createSection("hall.npc-points");
            sec = cfg.getConfigurationSection("hall.npc-points");
        }
        return sec;
    }

    public void saveNpcPoint(String key, String type, String name,
                             String world, double x, double y, double z,
                             float yaw, float pitch, String npcEntityId) {
        String base = "hall.npc-points." + key + ".";
        cfg.set(base + "type", type);
        cfg.set(base + "name", name);
        cfg.set(base + "world", world);
        cfg.set(base + "x", x);
        cfg.set(base + "y", y);
        cfg.set(base + "z", z);
        cfg.set(base + "yaw", yaw);
        cfg.set(base + "pitch", pitch);
        if (npcEntityId != null) cfg.set(base + "entity-id", npcEntityId);
        save();
    }

    public void removeNpcPoint(String key) {
        cfg.set("hall.npc-points." + key, null);
        save();
    }

    public List<NpcPointConfig> loadNpcPoints() {
        List<NpcPointConfig> list = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("hall.npc-points");
        if (sec == null) return list;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) continue;
            list.add(new NpcPointConfig(
                    key,
                    entry.getString("type", "HALL_INFO"),
                    entry.getString("name", "Hall NPC"),
                    entry.getString("world", "world"),
                    entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                    (float) entry.getDouble("yaw"), (float) entry.getDouble("pitch"),
                    entry.getString("entity-id")
            ));
        }
        return list;
    }

    // ── Persistence ───────────────────────────────────────────

    public FileConfiguration getRawConfig() { return cfg; }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[HallConfigManager] Failed to save halls.yml", e);
        }
    }

    // ── Nested: NpcPointConfig ────────────────────────────────

    public static class NpcPointConfig {
        public final String key;
        public final String type;
        public final String name;
        public final String world;
        public final double x, y, z;
        public final float yaw, pitch;
        public final String entityId;

        public NpcPointConfig(String key, String type, String name,
                              String world, double x, double y, double z,
                              float yaw, float pitch, String entityId) {
            this.key = key; this.type = type; this.name = name;
            this.world = world; this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch; this.entityId = entityId;
        }

        public Location toBukkitLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }
}