package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.logging.Level;

/**
 * Handles automatic config migration on plugin update.
 *
 * Two-phase approach:
 *  1. syncMissingKeys() — inject keys that exist in default but not in user file
 *  2. migrateVersion()  — handle renamed/removed/restructured keys per version bump
 *
 * How to add a new migration:
 *  1. Increment CURRENT_VERSION
 *  2. Add a new case in migrateVersion() for the new version
 *  3. Inside that case, use helpers: renameKey(), removeKey(), setKey()
 */
public class ConfigMigrator {

    // ── Bump this every time you make a breaking config change ──
    private static final int CURRENT_VERSION = 1;

    private static final String VERSION_KEY = "config-version";

    private final QuantumClan plugin;

    public ConfigMigrator(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────
    // Public entry point — call from onEnable after saveResource
    // ─────────────────────────────────────────────────────────

    public void migrate(String resourceName) {
        File userFile = new File(plugin.getDataFolder(), resourceName);
        if (!userFile.exists()) return; // saveResource handles first-time creation

        // Phase 1: inject missing keys from default
        syncMissingKeys(resourceName, userFile);

        // Phase 2: version-based migration (only for config.yml)
        // Other YAMLs use the version stored in config.yml
        if (resourceName.equals("config.yml")) {
            runVersionMigrations(userFile);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Phase 1 — Sync missing keys
    // ─────────────────────────────────────────────────────────

    /**
     * Reads the bundled default resource from the jar,
     * compares with the user's file, and injects any keys
     * that are present in the default but absent in the user file.
     *
     * Existing user values are NEVER overwritten.
     */
    private void syncMissingKeys(String resourceName, File userFile) {
        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream == null) {
            plugin.getLogger().warning("[ConfigMigrator] No default resource found for: " + resourceName);
            return;
        }

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream));
        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            // getKeys(true) includes nested keys but also parent keys
            // Only set if the key is a leaf value (not a section header)
            if (!userConfig.contains(key) && defaults.get(key) != null
                    && !(defaults.get(key) instanceof org.bukkit.configuration.ConfigurationSection)) {
                userConfig.set(key, defaults.get(key));
                plugin.getLogger().info("[ConfigMigrator] Added missing key '" + key + "' to " + resourceName);
                changed = true;
            }
        }

        if (changed) {
            save(userConfig, userFile, resourceName);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Phase 2 — Version-based migration
    // ─────────────────────────────────────────────────────────

    private void runVersionMigrations(File configFile) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        int savedVersion = cfg.getInt(VERSION_KEY, 0);

        if (savedVersion >= CURRENT_VERSION) return; // already up to date

        plugin.getLogger().info("[ConfigMigrator] Config version: " + savedVersion
                + " → " + CURRENT_VERSION + ". Running migrations...");

        // Run each migration step in order
        for (int v = savedVersion + 1; v <= CURRENT_VERSION; v++) {
            migrateVersion(v, configFile);
        }

        // Update stored version
        FileConfiguration updated = YamlConfiguration.loadConfiguration(configFile);
        updated.set(VERSION_KEY, CURRENT_VERSION);
        save(updated, configFile, "config.yml");

        plugin.getLogger().info("[ConfigMigrator] Migration complete. Now at version " + CURRENT_VERSION + ".");
    }

    /**
     * Add a new case here every time you bump CURRENT_VERSION.
     *
     * @param targetVersion the version being migrated TO
     * @param configFile    the config.yml file (use helpers for other files too)
     */
    private void migrateVersion(int targetVersion, File configFile) {
        switch (targetVersion) {

            case 1 -> {
                // Example: nothing to migrate for initial version
                plugin.getLogger().info("[ConfigMigrator] v1: baseline, no changes needed.");
            }

            // ── TEMPLATE for future migrations ───────────────
            // case 2 -> {
            //     // Example: rename a key in config.yml
            //     renameKey(configFile, "old-key-name", "new-key-name");
            //
            //     // Example: remove a key that no longer exists
            //     removeKey(configFile, "deprecated-key");
            //
            //     // Example: set a new key with a default value
            //     //          (syncMissingKeys already handles this,
            //     //           but useful if you need a specific value
            //     //           based on existing data)
            //     setKeyIfAbsent(configFile, "new-feature.enabled", true);
            //
            //     // Example: rename a key in messages.yml
            //     File msgFile = new File(plugin.getDataFolder(), "messages.yml");
            //     renameKey(msgFile, "contribution.old-key", "contribution.new-key");
            // }
            //
            // case 3 -> {
            //     // Restructure: move key from one section to another
            //     File shopFile = new File(plugin.getDataFolder(), "shop.yml");
            //     copyKeyValue(shopFile, "old.section.key", "new.section.key");
            //     removeKey(shopFile, "old.section.key");
            // }

            default -> plugin.getLogger().warning(
                    "[ConfigMigrator] No migration defined for version " + targetVersion);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Migration helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Renames oldKey to newKey in the given file.
     * The old key's value is copied to the new key, then the old key is removed.
     * No-op if oldKey doesn't exist or newKey already exists.
     */
    public void renameKey(File file, String oldKey, String newKey) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.contains(oldKey)) return;
        if (cfg.contains(newKey)) {
            // New key already exists (maybe user added it manually) — just remove old
            cfg.set(oldKey, null);
            save(cfg, file, file.getName());
            return;
        }
        Object value = cfg.get(oldKey);
        cfg.set(newKey, value);
        cfg.set(oldKey, null);
        save(cfg, file, file.getName());
        plugin.getLogger().info("[ConfigMigrator] Renamed key '" + oldKey + "' → '" + newKey + "' in " + file.getName());
    }

    /**
     * Removes a key from the given file.
     * No-op if the key doesn't exist.
     */
    public void removeKey(File file, String key) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.contains(key)) return;
        cfg.set(key, null);
        save(cfg, file, file.getName());
        plugin.getLogger().info("[ConfigMigrator] Removed deprecated key '" + key + "' from " + file.getName());
    }

    /**
     * Sets a key only if it doesn't already exist.
     * Useful inside version migrations for computed defaults.
     */
    public void setKeyIfAbsent(File file, String key, Object value) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.contains(key)) return;
        cfg.set(key, value);
        save(cfg, file, file.getName());
    }

    /**
     * Copies the value of one key to another within the same file.
     * Useful before calling removeKey() when restructuring.
     */
    public void copyKeyValue(File file, String fromKey, String toKey) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.contains(fromKey)) return;
        cfg.set(toKey, cfg.get(fromKey));
        save(cfg, file, file.getName());
    }

    // ─────────────────────────────────────────────────────────
    // Save helper
    // ─────────────────────────────────────────────────────────

    private void save(FileConfiguration cfg, File file, String label) {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ConfigMigrator] Failed to save " + label, e);
        }
    }
}