package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

// Optional hook imports — wrapped in try/catch at class level via isPresent checks
// so the plugin compiles even when these are not on the classpath at runtime.

public class HookManager {

    private final QuantumClan plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // ── Required ──────────────────────────────────────────────
    private Economy vaultEconomy;
    private boolean vaultEnabled = false;

    // ── Optional ──────────────────────────────────────────────
    private boolean playerPointsEnabled = false;
    private boolean gemsEconomyEnabled  = false;
    private boolean placeholderApiEnabled = false;
    private boolean luckPermsEnabled    = false;
    private boolean decentHologramsEnabled = false;
    private boolean holographicDisplaysEnabled = false;
    private boolean worldGuardEnabled   = false;
    private boolean faweEnabled         = false;

    // Cached hook instances (raw Object to avoid hard compile-time dependency)
    private Object playerPointsApi      = null;
    private Object gemsEconomyApi       = null;
    private Object luckPermsApi         = null;
    private Object decentHologramsApi   = null;
    private Object holographicDisplaysApi = null;
    private Object worldGuardPlugin     = null;
    private Object fawePlugin           = null;

    public HookManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialise all hooks. Call once from onEnable().
     * Logs the status of every hook to console.
     */
    public void init() {
        hookVault();
        hookPlayerPoints();
        hookGemsEconomy();
        hookPlaceholderAPI();
        hookLuckPerms();
        hookDecentHolograms();
        hookHolographicDisplays();
        hookWorldGuard();
        hookFawe();
    }

    // ── Vault ─────────────────────────────────────────────────

    private void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logHook("vault-disabled");
            vaultEnabled = false;
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logHook("vault-disabled");
            vaultEnabled = false;
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
            Class<?> apiClass = Class.forName(
                    "org.black_ixx.playerpoints.PlayerPointsAPI");
            org.bukkit.plugin.Plugin pp =
                    Bukkit.getPluginManager().getPlugin("PlayerPoints");
            // Retrieve API via PlayerPoints#getAPI()
            playerPointsApi = pp.getClass().getMethod("getAPI").invoke(pp);
            playerPointsEnabled = (playerPointsApi != null);
        } catch (Exception e) {
            playerPointsEnabled = false;
            plugin.getLogger().warning("[Hook] PlayerPoints: Failed to load API — " + e.getMessage());
        }
        logHook(playerPointsEnabled ? "playerpoints-enabled" : "playerpoints-disabled");
    }

    // ── GemsEconomy ───────────────────────────────────────────

    private void hookGemsEconomy() {
        if (!isPluginPresent("GemsEconomy")) {
            logHook("gemseconomy-disabled");
            return;
        }
        try {
            org.bukkit.plugin.Plugin ge =
                    Bukkit.getPluginManager().getPlugin("GemsEconomy");
            // GemsEconomy exposes getAPI() on its main class
            gemsEconomyApi = ge.getClass().getMethod("getAPI").invoke(ge);
            gemsEconomyEnabled = (gemsEconomyApi != null);
        } catch (Exception e) {
            gemsEconomyEnabled = false;
            plugin.getLogger().warning("[Hook] GemsEconomy: Failed to load API — " + e.getMessage());
        }
        logHook(gemsEconomyEnabled ? "gemseconomy-enabled" : "gemseconomy-disabled");
    }

    // ── PlaceholderAPI ────────────────────────────────────────

    private void hookPlaceholderAPI() {
        if (!isPluginPresent("PlaceholderAPI")) {
            logHook("placeholderapi-disabled");
            return;
        }
        // No API instance needed — PAPI expansion is registered separately
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
            luckPermsEnabled = false;
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
            org.bukkit.plugin.Plugin dh =
                    Bukkit.getPluginManager().getPlugin("DecentHolograms");
            // DecentHolograms exposes DHAPI static utility — no instance needed
            Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            decentHologramsApi = dh;
            decentHologramsEnabled = true;
        } catch (Exception e) {
            decentHologramsEnabled = false;
            plugin.getLogger().warning("[Hook] DecentHolograms: Failed to load API — " + e.getMessage());
        }
        logHook(decentHologramsEnabled ? "decentholograms-enabled" : "decentholograms-disabled");
    }

    // ── HolographicDisplays ───────────────────────────────────

    private void hookHolographicDisplays() {
        if (!isPluginPresent("HolographicDisplays")) {
            logHook("holographicdisplays-disabled");
            return;
        }
        try {
            Class.forName("com.gmail.filoghost.holographicdisplays.api.HologramsAPI");
            holographicDisplaysApi =
                    Bukkit.getPluginManager().getPlugin("HolographicDisplays");
            holographicDisplaysEnabled = true;
        } catch (Exception e) {
            holographicDisplaysEnabled = false;
            plugin.getLogger().warning("[Hook] HolographicDisplays: Failed to load API — " + e.getMessage());
        }
        logHook(holographicDisplaysEnabled
                ? "holographicdisplays-enabled" : "holographicdisplays-disabled");
    }

    // ── WorldGuard ────────────────────────────────────────────

    private void hookWorldGuard() {
        if (!isPluginPresent("WorldGuard")) {
            logHook("worldguard-disabled");
            return;
        }
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardPlugin =
                    Bukkit.getPluginManager().getPlugin("WorldGuard");
            worldGuardEnabled = true;
        } catch (Exception e) {
            worldGuardEnabled = false;
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
            fawePlugin =
                    Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
            faweEnabled = true;
        } catch (Exception e) {
            faweEnabled = false;
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
        // Strip MiniMessage tags for console output (console doesn't render colour)
        String plain = mm.stripTags(raw);
        plugin.getLogger().info(plain);
    }

    // ── Public API ────────────────────────────────────────────

    /** Vault Economy instance. Null if Vault is not present. */
    public Economy getVaultEconomy() { return vaultEconomy; }
    public boolean isVaultEnabled()  { return vaultEnabled; }

    /** Raw PlayerPoints API instance (cast to PlayerPointsAPI when needed). */
    public Object getPlayerPointsApi()      { return playerPointsApi; }
    public boolean isPlayerPointsEnabled()  { return playerPointsEnabled; }

    /** Raw GemsEconomy API instance. */
    public Object getGemsEconomyApi()      { return gemsEconomyApi; }
    public boolean isGemsEconomyEnabled()  { return gemsEconomyEnabled; }

    public boolean isPlaceholderApiEnabled()       { return placeholderApiEnabled; }

    /** Raw LuckPerms API instance (cast to net.luckperms.api.LuckPerms). */
    public Object getLuckPermsApi()        { return luckPermsApi; }
    public boolean isLuckPermsEnabled()    { return luckPermsEnabled; }

    /** DecentHolograms plugin instance (use DHAPI static methods). */
    public Object getDecentHologramsApi()       { return decentHologramsApi; }
    public boolean isDecentHologramsEnabled()   { return decentHologramsEnabled; }

    /** HolographicDisplays plugin instance (use HologramsAPI static methods). */
    public Object getHolographicDisplaysApi()       { return holographicDisplaysApi; }
    public boolean isHolographicDisplaysEnabled()   { return holographicDisplaysEnabled; }

    /**
     * Returns true if ANY hologram plugin is available.
     * DecentHolograms takes priority over HolographicDisplays.
     */
    public boolean isAnyHologramEnabled() {
        return decentHologramsEnabled || holographicDisplaysEnabled;
    }

    /** WorldGuard plugin instance. */
    public Object getWorldGuardPlugin()    { return worldGuardPlugin; }
    public boolean isWorldGuardEnabled()   { return worldGuardEnabled; }

    /** FAWE plugin instance. */
    public Object getFawePlugin()          { return fawePlugin; }
    public boolean isFaweEnabled()         { return faweEnabled; }

    /**
     * Convenience: typed LuckPerms API.
     * Returns null if LuckPerms is not present.
     */
    public net.luckperms.api.LuckPerms getLuckPerms() {
        if (!luckPermsEnabled || luckPermsApi == null) return null;
        return (net.luckperms.api.LuckPerms) luckPermsApi;
    }

    /**
     * Convenience: typed PlayerPoints API.
     * Returns null if PlayerPoints is not present.
     */
    public org.black_ixx.playerpoints.PlayerPointsAPI getPlayerPoints() {
        if (!playerPointsEnabled || playerPointsApi == null) return null;
        return (org.black_ixx.playerpoints.PlayerPointsAPI) playerPointsApi;
    }

    /**
     * Convenience: typed GemsEconomy API.
     * Returns null if GemsEconomy is not present.
     */
    public me.glaremasters.gemseconomy.api.GemsEconomyAPI getGemsEconomy() {
        if (!gemsEconomyEnabled || gemsEconomyApi == null) return null;
        return (me.glaremasters.gemseconomy.api.GemsEconomyAPI) gemsEconomyApi;
    }
}