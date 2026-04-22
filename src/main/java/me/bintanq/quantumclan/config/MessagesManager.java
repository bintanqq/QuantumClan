package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Reads all messages from messages.yml.
 * Supports placeholder replacement via key-value pairs.
 *
 * BUG FIX #3: {coins-name} is now always resolved globally before returning,
 * so callers don't need to pass it manually. It's read from ConfigManager.
 *
 * Usage:
 *   messagesManager.get("coins.balance", "{value}", "500")
 *   → auto-replaces {coins-name} with whatever is set in config.yml
 */
public class MessagesManager {

    private final QuantumClan plugin;
    private FileConfiguration cfg;

    public MessagesManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Returns the raw MiniMessage string for the given key, or null if not found.
     * Does NOT perform any placeholder replacement.
     */
    public String getRaw(String key) {
        return cfg.getString(key);
    }

    /**
     * Retrieves a message string with placeholder substitution.
     * {coins-name} is ALWAYS replaced automatically from config.
     *
     * @param key          Dot-notation key, e.g. "clan.create-success"
     * @param replacements Alternating placeholder-value pairs
     * @return Formatted MiniMessage string
     */
    public String get(String key, String... replacements) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";

        // BUG FIX #3: Always resolve {coins-name} first, globally
        msg = resolveCoinsName(msg);

        if (replacements == null || replacements.length < 2) return msg;

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break;
            String placeholder = replacements[i];
            String value       = replacements[i + 1];
            if (placeholder != null && value != null) {
                msg = msg.replace(placeholder, value);
            }
        }
        return msg;
    }

    /**
     * Single-placeholder convenience overload.
     * {coins-name} is ALWAYS replaced automatically from config.
     */
    public String get(String key, String placeholder, Object value) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";

        msg = resolveCoinsName(msg);

        if (placeholder != null && value != null) {
            msg = msg.replace(placeholder, String.valueOf(value));
        }
        return msg;
    }

    /**
     * BUG FIX #3: Replaces {coins-name} with the configured name from config.yml.
     * Safe to call even if {coins-name} is not present in the string.
     */
    private String resolveCoinsName(String msg) {
        if (msg == null || !msg.contains("{coins-name}")) return msg;
        // ConfigManager may not be ready during very early init — guard with null check
        try {
            String coinsName = plugin.getConfigManager().getCoinsName();
            return msg.replace("{coins-name}", coinsName != null ? coinsName : "Coins");
        } catch (Exception e) {
            return msg.replace("{coins-name}", "Coins");
        }
    }

    public FileConfiguration getConfig() { return cfg; }
}