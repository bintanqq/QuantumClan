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
    public String get(String key, String... replacements) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";
        if (replacements.length % 2 != 0) return msg;
        for (int i = 0; i < replacements.length; i += 2) {
            if (replacements[i] == null || replacements[i + 1] == null) continue;
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    /**
     * Retrieves a message with a single Object value substituted for {value}.
     */
    public String get(String key, String placeholder, Object value) {
        return get(key, placeholder, String.valueOf(value));
    }

    public FileConfiguration getConfig() { return cfg; }
}