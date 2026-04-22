package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.ClanRole;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads roles from roles.yml.
 * Roles are ordered by their list index (0 = highest / Leader).
 */
public class RolesConfigManager {

    private final QuantumClan plugin;
    /** Ordered list — index 0 = highest rank */
    private final List<ClanRole> roles = new ArrayList<>();
    /** name (lowercase) → ClanRole for O(1) lookup */
    private final Map<String, ClanRole> roleMap = new HashMap<>();

    public RolesConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        roles.clear();
        roleMap.clear();

        File file = new File(plugin.getDataFolder(), "roles.yml");
        if (!file.exists()) plugin.saveResource("roles.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        List<?> list = cfg.getList("roles");
        if (list == null) {
            plugin.getLogger().warning("[RolesConfig] No roles found in roles.yml!");
            return;
        }

        int index = 0;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            String name        = String.valueOf(map.getOrDefault("name", "member"));
            String displayName = String.valueOf(map.getOrDefault("display-name", name));
            String color       = String.valueOf(map.getOrDefault("color", "#AAAAAA"));

            // Parse permissions map
            Map<String, Boolean> perms = new HashMap<>();
            Object permsObj = map.get("permissions");
            if (permsObj instanceof Map<?, ?> permsMap) {
                for (Map.Entry<?, ?> entry : permsMap.entrySet()) {
                    String node  = String.valueOf(entry.getKey());
                    boolean val  = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    perms.put(node, val);
                }
            }

            ClanRole role = new ClanRole(name, displayName, color, index, perms);
            roles.add(role);
            roleMap.put(name.toLowerCase(), role);
            index++;
        }

        plugin.getLogger().info("[RolesConfig] Loaded " + roles.size() + " roles.");
    }

    // ── Lookups ───────────────────────────────────────────────

    /** Returns ClanRole by name (case-insensitive), or null. */
    public ClanRole getRole(String name) {
        if (name == null) return null;
        return roleMap.get(name.toLowerCase());
    }

    /** Returns the highest-ranked role (index 0 — always the Leader). */
    public ClanRole getLeaderRole() {
        return roles.isEmpty() ? null : roles.get(0);
    }

    /** Returns the second-ranked role (Officer), or member if only 2 roles. */
    public ClanRole getDefaultOfficerRole() {
        if (roles.size() < 2) return getLeaderRole();
        return roles.get(1);
    }

    /** Returns the lowest-ranked role (default new member role). */
    public ClanRole getDefaultMemberRole() {
        return roles.isEmpty() ? null : roles.get(roles.size() - 1);
    }

    /** Returns an unmodifiable ordered list of all roles (index 0 = highest). */
    public List<ClanRole> getRoles() {
        return List.copyOf(roles);
    }

    /** Returns all role names for tab-completion. */
    public List<String> getRoleNames() {
        return roles.stream().map(ClanRole::getName).toList();
    }
}