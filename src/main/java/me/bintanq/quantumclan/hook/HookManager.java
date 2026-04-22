package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Manages all optional plugin hooks for QuantumClan.
 *
 * REQUIRED:
 *   - Vault (Economy)
 *
 * OPTIONAL:
 *   - PlayerPoints
 *   - PlaceholderAPI
 *   - LuckPerms
 *   - DecentHolograms
 *   - WorldGuard
 *   - FastAsyncWorldEdit (FAWE)
 *
 * Each hook is null-checked before use. If a hook fails to load,
 * the related feature is gracefully disabled with a console log.
 */
public class HookManager {

    private final QuantumClan plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // ── Required ──────────────────────────────────────────────
    private Economy vaultEconomy;
    private boolean vaultEnabled = false;

    // ── Optional ──────────────────────────────────────────────
    private boolean playerPointsEnabled   = false;
    private boolean placeholderApiEnabled = false;
    private boolean luckPermsEnabled      = false;
    private boolean decentHologramsEnabled = false;
    private boolean worldGuardEnabled     = false;
    private boolean faweEnabled           = false;

    // Raw instances (avoid hard compile-time dep on optional plugins)
    private Object playerPointsApi     = null;
    private Object luckPermsApi        = null;
    private Object decentHologramsApi  = null;
    private Object worldGuardPlugin    = null;
    private Object fawePlugin          = null;

    public HookManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialises all hooks. Call once from onEnable().
     * Logs the status of every hook to console.
     */
    public void init() {
        hookVault();
        hookPlayerPoints();
        hookPlaceholderAPI();
        hookLuckPerms();
        hookDecentHolograms();
        hookWorldGuard();
        hookFawe();
    }

    // ── Vault ─────────────────────────────────────────────────

    private void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
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

    // ── DecentHolograms ───────────────────────────────────────

    private void hookDecentHolograms() {
        if (!isPluginPresent("DecentHolograms")) {
            logHook("decentholograms-disabled");
            return;
        }
        try {
            Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            decentHologramsApi = Bukkit.getPluginManager().getPlugin("DecentHolograms");
            decentHologramsEnabled = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] DecentHolograms: Failed to load API — " + e.getMessage());
        }
        logHook(decentHologramsEnabled ? "decentholograms-enabled" : "decentholograms-disabled");
    }

    // ── WorldGuard ────────────────────────────────────────────

    private void hookWorldGuard() {
        if (!isPluginPresent("WorldGuard")) {
            logHook("worldguard-disabled");
            return;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
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
            fawePlugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
            faweEnabled = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hook] FAWE: Failed to load API — " + e.getMessage());
        }
        logHook(faweEnabled ? "fawe-enabled" : "fawe-disabled");
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
    public boolean isDecentHologramsEnabled(){ return decentHologramsEnabled; }
    public boolean isWorldGuardEnabled()     { return worldGuardEnabled; }
    public boolean isFaweEnabled()           { return faweEnabled; }

    /** Returns true if DecentHolograms is available (the only hologram plugin now). */
    public boolean isAnyHologramEnabled()    { return decentHologramsEnabled; }

    public Object  getDecentHologramsApi()   { return decentHologramsApi; }
    public Object  getWorldGuardPlugin()     { return worldGuardPlugin; }
    public Object  getFawePlugin()           { return fawePlugin; }

    /** Typed LuckPerms API. Null if not present. */
    public net.luckperms.api.LuckPerms getLuckPerms() {
        if (!luckPermsEnabled || luckPermsApi == null) return null;
        return (net.luckperms.api.LuckPerms) luckPermsApi;
    }

    /** Typed PlayerPoints API. Null if not present. */
    public org.black_ixx.playerpoints.PlayerPointsAPI getPlayerPoints() {
        if (!playerPointsEnabled || playerPointsApi == null) return null;
        return (org.black_ixx.playerpoints.PlayerPointsAPI) playerPointsApi;
    }
}