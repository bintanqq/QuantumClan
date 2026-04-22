package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Reads all messages from messages.yml.
 * Supports placeholder replacement via key-value pairs.
 *
 * Usage:
 *   messagesManager.get("clan.create-success", "{clan}", "NWF", "{tag}", "NWF")
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
     * Retrieves a raw message string (MiniMessage format) by dot-notation key.
     * Returns null if not found.
     */
    public String getRaw(String key) {
        return cfg.getString(key);
    }

    /**
     * Retrieves a message string with placeholder substitution.
     *
     * @param key          Dot-notation key, e.g. "clan.create-success"
     * @param replacements Alternating placeholder-value pairs: "{clan}", "MyName", "{tag}", "TAG"
     * @return Formatted MiniMessage string, or empty string if key not found
     */
    // 1. Ganti method ini
    public String get(String key, String... replacements) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";

        // Jika tidak ada pengganti, langsung balikin
        if (replacements == null || replacements.length < 2) return msg;

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 >= replacements.length) break; // Safety check
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            if (placeholder != null && value != null) {
                msg = msg.replace(placeholder, value);
            }
        }
        return msg;
    }

    // 2. Ganti method ini (Baris 62) jadi lebih "To the Point"
    public String get(String key, String placeholder, Object value) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";
        if (placeholder == null || value == null) return msg;

        return msg.replace(placeholder, String.valueOf(value));
    }

    public FileConfiguration getConfig() { return cfg; }
}