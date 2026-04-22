package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads all messages from messages.yml.
 * Supports placeholder replacement via TagResolvers or manual pairs.
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

    public String getRaw(String key) {
        return cfg.getString(key);
    }

    /**
     * Returns a Component directly with replacements handled via TagResolvers.
     */
    public Component getComponent(String key, String... replacements) {
        String raw = cfg.getString(key);
        if (raw == null) return Component.text("Missing message: " + key);

        List<TagResolver> resolvers = new ArrayList<>();
        // Global {coins-name} resolver
        resolvers.add(Placeholder.parsed("coins-name", getCoinsName()));

        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) break;
                String p = replacements[i].replace("{", "").replace("}", "");
                resolvers.add(Placeholder.parsed(p, replacements[i+1]));
            }
        }

        return plugin.getMiniMessage().deserialize(raw, TagResolver.resolver(resolvers));
    }

    public String get(String key, String... replacements) {
        String msg = cfg.getString(key);
        if (msg == null) return "<red>[Missing message: " + key + "]";

        msg = msg.replace("{coins-name}", getCoinsName());

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
     */
    public String get(String key, String placeholder, Object value) {
        return get(key, placeholder, String.valueOf(value));
    }

    private String getCoinsName() {
        try {
            String name = plugin.getConfigManager().getCoinsName();
            return name != null ? name : "Coins";
        } catch (Exception e) {
            return "Coins";
        }
    }

    public FileConfiguration getConfig() { return cfg; }
}