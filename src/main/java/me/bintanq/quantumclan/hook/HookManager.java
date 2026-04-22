package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Manages all optional plugin hooks for QuantumClan.
 *
 * REQUIRED: Vault (Economy)
 *
 * OPTIONAL:
 *   - PlayerPoints
 *   - PlaceholderAPI
 *   - LuckPerms
 *   - WorldGuard
 *   - FastAsyncWorldEdit (FAWE)
 *   - Citizens (NPC backend for Clan Hall)
 */
public class HookManager {

    private final QuantumClan plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private Economy vaultEconomy;
    private boolean vaultEnabled          = false;
    private boolean playerPointsEnabled   = false;
    private boolean placeholderApiEnabled = false;
    private boolean luckPermsEnabled      = false;
    private boolean worldGuardEnabled     = false;
    private boolean faweEnabled           = false;
    private boolean citizensEnabled       = false;

    private Object playerPointsApi = null;
    private Object luckPermsApi    = null;

    public HookManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void init() {
        hookVault();
        hookPlayerPoints();
        hookPlaceholderAPI();
        hookLuckPerms();
        hookWorldGuard();
        hookFawe();
        hookCitizens();
    }

    // ── Vault ─────────────────────────────────────────────────

    private void hookVault() {
        if (!isPluginPresent("Vault")) {
            logHook("vault-disabled");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logHook("vault-disabled");
            return;
        }
        vaultEconomy = rsp.getProvider();
        vaultEnabled = (vaultEconomy != null);
        logHook(vaultEnabled ? "vault-enabled" : "vault-disabled");
    }

    // ── PlayerPoints ──────────────────────────────────────────

    private void hookPlayerPoints() {
        if (!isPluginPresent("PlayerPoints")) {
            logHook("playerpoints-disabled");
            return;
        }
        try {
            org.bukkit.plugin.Plugin pp =
                    Bukkit.getPluginManager().getPlugin("PlayerPoints");
            playerPointsApi = pp.getClass().getMethod("getAPI").invoke(pp);
            playerPointsEnabled = (playerPointsApi != null);
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] PlayerPoints: Failed to load API — " + e.getMessage());
        }
        logHook(playerPointsEnabled ? "playerpoints-enabled" : "playerpoints-disabled");
    }

    // ── PlaceholderAPI ────────────────────────────────────────

    private void hookPlaceholderAPI() {
        if (!isPluginPresent("PlaceholderAPI")) {
            logHook("placeholderapi-disabled");
            return;
        }
        placeholderApiEnabled = true;
        logHook("placeholderapi-enabled");
    }

    // ── LuckPerms ─────────────────────────────────────────────

    private void hookLuckPerms() {
        if (!isPluginPresent("LuckPerms")) {
            logHook("luckperms-disabled");
            return;
        }
        try {
            RegisteredServiceProvider<?> rsp =
                    Bukkit.getServicesManager().getRegistration(
                            Class.forName("net.luckperms.api.LuckPerms"));
            if (rsp != null) {
                luckPermsApi = rsp.getProvider();
                luckPermsEnabled = true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] LuckPerms: Failed to load API — " + e.getMessage());
        }
        logHook(luckPermsEnabled ? "luckperms-enabled" : "luckperms-disabled");
    }

    // ── WorldGuard ────────────────────────────────────────────

    private void hookWorldGuard() {
        if (!isPluginPresent("WorldGuard")) {
            logHook("worldguard-disabled");
            return;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardEnabled = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] WorldGuard: Failed to load API — " + e.getMessage());
        }
        logHook(worldGuardEnabled ? "worldguard-enabled" : "worldguard-disabled");
    }

    // ── FAWE ──────────────────────────────────────────────────

    private void hookFawe() {
        if (!isPluginPresent("FastAsyncWorldEdit")) {
            logHook("fawe-disabled");
            return;
        }
        try {
            Class.forName("com.fastasyncworldedit.core.FaweAPI");
            faweEnabled = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] FAWE: Failed to load API — " + e.getMessage());
        }
        logHook(faweEnabled ? "fawe-enabled" : "fawe-disabled");
    }

    // ── Citizens ──────────────────────────────────────────────

    private void hookCitizens() {
        if (!isPluginPresent("Citizens")) {
            plugin.getLogger().info("[Hook] Citizens: Not found — armor stand NPC fallback will be used.");
            return;
        }
        try {
            Class.forName("net.citizensnpcs.api.CitizensAPI");
            citizensEnabled = true;
            plugin.getLogger().info("[Hook] Citizens: Connected ✓");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Hook] Citizens: Class not found — " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private boolean isPluginPresent(String name) {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    private void logHook(String messageKey) {
        String raw = plugin.getMessagesManager().getRaw("hook." + messageKey);
        if (raw == null) return;
        plugin.getLogger().info(mm.stripTags(raw));
    }

    // ── Public API ────────────────────────────────────────────

    public Economy getVaultEconomy()         { return vaultEconomy; }
    public boolean isVaultEnabled()          { return vaultEnabled; }
    public boolean isPlayerPointsEnabled()   { return playerPointsEnabled; }
    public boolean isPlaceholderApiEnabled() { return placeholderApiEnabled; }
    public boolean isLuckPermsEnabled()      { return luckPermsEnabled; }
    public boolean isWorldGuardEnabled()     { return worldGuardEnabled; }
    public boolean isFaweEnabled()           { return faweEnabled; }
    public boolean isCitizensEnabled()       { return citizensEnabled; }
    public boolean isAnyHologramEnabled()    { return false; }

    public net.luckperms.api.LuckPerms getLuckPerms() {
        if (!luckPermsEnabled || luckPermsApi == null) return null;
        return (net.luckperms.api.LuckPerms) luckPermsApi;
    }

    public org.black_ixx.playerpoints.PlayerPointsAPI getPlayerPoints() {
        if (!playerPointsEnabled || playerPointsApi == null) return null;
        return (org.black_ixx.playerpoints.PlayerPointsAPI) playerPointsApi;
    }
}