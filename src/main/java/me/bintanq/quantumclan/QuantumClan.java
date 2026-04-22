package me.bintanq.quantumclan;

import me.bintanq.quantumclan.config.*;
import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.database.dao.*;
import me.bintanq.quantumclan.economy.EconomyProvider;
import me.bintanq.quantumclan.economy.impl.*;
import me.bintanq.quantumclan.hook.HookManager;
import me.bintanq.quantumclan.listener.*;
import me.bintanq.quantumclan.manager.ClanManager;
import me.bintanq.quantumclan.module.*;
import me.bintanq.quantumclan.placeholder.QuantumClanPlaceholder;
import me.bintanq.quantumclan.schematic.SchematicProvider;
import me.bintanq.quantumclan.schematic.impl.FAWEStructureProvider;
import me.bintanq.quantumclan.schematic.impl.NBTStructureProvider;
import me.bintanq.quantumclan.util.ChatInputManager;
import me.bintanq.quantumclan.command.QClanCommand;
import me.bintanq.quantumclan.command.QClanAdminCommand;
import me.bintanq.quantumclan.api.QuantumClanProvider;
import me.bintanq.quantumclan.api.QuantumClanAPIImpl;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public class QuantumClan extends JavaPlugin {

    private static QuantumClan instance;

    // ── Config managers ───────────────────────────────────────
    private ConfigManager       configManager;
    private MessagesManager     messagesManager;
    private ShopConfigManager   shopConfigManager;
    private WarConfigManager    warConfigManager;
    private RolesConfigManager  rolesConfigManager;
    private GuiConfigManager    guiConfigManager;
    private HallConfigManager   hallConfigManager;   // NEW
    private ConfigMigrator migrator;

    // ── Database ──────────────────────────────────────────────
    private DatabaseManager  databaseManager;
    private ClanDAO          clanDAO;
    private MemberDAO        memberDAO;
    private BountyDAO        bountyDAO;
    private WarDAO           warDAO;
    private CoinsDAO         coinsDAO;
    private ContributionDAO  contributionDAO;
    private HallDAO          hallDAO;               // NEW

    // ── Hooks ─────────────────────────────────────────────────
    private HookManager hookManager;

    // ── Economy ───────────────────────────────────────────────
    private EconomyProvider economyProvider;
    private CoinsProvider   coinsProvider;

    // ── Schematic ─────────────────────────────────────────────
    private SchematicProvider schematicProvider;

    // ── Core manager ──────────────────────────────────────────
    private ClanManager clanManager;

    // ── Modules ───────────────────────────────────────────────
    private ChatInputManager    chatInputManager;
    private BountyManager       bountyManager;
    private WarManager          warManager;
    private WarScheduler        warScheduler;
    private ClanShopManager     clanShopManager;
    private BuffTracker         buffTracker;
    private ContributionManager contributionManager;
    private SpyScrollManager    spyScrollManager;
    private CoinsShopManager    coinsShopManager;
    private ClanHallManager     clanHallManager;    // NEW
    private HallNPCManager      hallNPCManager;     // NEW

    // ── MiniMessage ───────────────────────────────────────────
    private final MiniMessage mm = MiniMessage.miniMessage();

    // ─────────────────────────────────────────────────────────
    // onEnable
    // ─────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║     QuantumClan v" + getDescription().getVersion() + " Loading...     ║");
        getLogger().info("╚══════════════════════════════════════╝");

        // 1. Save default configs
        saveDefaultConfigs();

        // 2. Load config managers
        if (!loadConfigs()) {
            getLogger().severe("[QuantumClan] Failed to load configs! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize database
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("[QuantumClan] Failed to initialize database! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3a. Add clan_hall_access table
        addHallTable();

        // 4. Initialize DAOs
        initDAOs();

        // 5. Initialize hooks
        hookManager = new HookManager(this);
        hookManager.init();

        // 6. Select economy provider
        if (!initEconomyProvider()) {
            getLogger().severe("[QuantumClan] Vault is required but not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 7. Init coins provider (built-in)
        coinsProvider = new CoinsProvider(this, coinsDAO);

        // 8. Init schematic provider
        initSchematicProvider();

        // 9. Init ClanManager and load cache
        clanManager = new ClanManager(this, clanDAO, memberDAO, configManager, rolesConfigManager);
        try {
            clanManager.loadAll().get();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[QuantumClan] Failed to load clan cache!", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 10. Init utility
        chatInputManager = new ChatInputManager(this);

        // 11. Init feature modules
        initModules();

        // 12. Register listeners
        registerListeners();

        // 13. Register commands
        registerCommands();

        // 14. Register Core API
        QuantumClanAPIImpl apiImpl = new QuantumClanAPIImpl(this);
        QuantumClanProvider.register(apiImpl);
        getLogger().info("[QuantumClan] API successfully registered.");

        // 15. Register PlaceholderAPI expansion
        if (hookManager.isPlaceholderApiEnabled()) {
            new QuantumClanPlaceholder(this).register();
            getLogger().info("[QuantumClan] PlaceholderAPI expansion registered.");
        }

        // 16. Init hall system AFTER everything else is ready
        if (hallConfigManager.isEnabled()) {
            clanHallManager.init();
            hallNPCManager.spawnAll();
            getLogger().info("[ClanHall] Hall system initialised.");
        }

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║   QuantumClan enabled in " + elapsed + "ms       ║");
        getLogger().info("╚══════════════════════════════════════╝");
    }

    // ─────────────────────────────────────────────────────────
    // onDisable
    // ─────────────────────────────────────────────────────────

    @Override
    public void onDisable() {
        getLogger().info("[QuantumClan] Shutting down...");

        if (hallNPCManager != null) {
            hallNPCManager.despawnAll();
        }
        if (clanHallManager != null) {
            clanHallManager.shutdown();
        }
        if (warManager != null) {
            warManager.endWarGracefully();
        }

        Bukkit.getScheduler().cancelTasks(this);

        if (clanManager != null) {
            clanManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        QuantumClanProvider.unregister();

        getLogger().info("[QuantumClan] Disabled successfully.");
    }

    // ─────────────────────────────────────────────────────────
    // Private init helpers
    // ─────────────────────────────────────────────────────────

    private void saveDefaultConfigs() {
        saveDefaultConfig();
        saveResourceIfAbsent("messages.yml");
        saveResourceIfAbsent("shop.yml");
        saveResourceIfAbsent("war.yml");
        saveResourceIfAbsent("roles.yml");
        saveResourceIfAbsent("gui.yml");
        saveResourceIfAbsent("halls.yml");          // NEW

        // Ensure schematics directory exists
        new File(getDataFolder(), "halls/schematics").mkdirs();

        migrator = new ConfigMigrator(this);
        migrator.migrate("config.yml");
        migrator.migrate("messages.yml");
        migrator.migrate("shop.yml");
        migrator.migrate("war.yml");
        migrator.migrate("roles.yml");
        migrator.migrate("gui.yml");
    }

    private void saveResourceIfAbsent(String resourceName) {
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) {
            try {
                saveResource(resourceName, false);
            } catch (Exception e) {
                getLogger().warning("Could not save default resource: " + resourceName);
            }
        }
    }

    private boolean loadConfigs() {
        try {
            configManager      = new ConfigManager(this);
            messagesManager    = new MessagesManager(this);
            shopConfigManager  = new ShopConfigManager(this);
            warConfigManager   = new WarConfigManager(this);
            rolesConfigManager = new RolesConfigManager(this);
            guiConfigManager   = new GuiConfigManager(this);
            hallConfigManager  = new HallConfigManager(this);  // NEW
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[QuantumClan] Config load error", e);
            return false;
        }
    }

    /** Adds clan_hall_access table using addColumnIfNotExists pattern. */
    private void addHallTable() {
        databaseManager.runAsync(() -> {
            try (var conn = databaseManager.getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS clan_hall_access (
                        clan_id      VARCHAR(36) PRIMARY KEY,
                        purchased_at TIMESTAMP   NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
                        expires_at   TIMESTAMP   NULL,
                        active       INTEGER     NOT NULL DEFAULT 1
                    )
                    """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_hall_access_active " +
                        "ON clan_hall_access(active)");
                getLogger().info("[Database] clan_hall_access table verified.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[Database] Failed to create clan_hall_access table", e);
            }
        });
    }

    private void initDAOs() {
        clanDAO         = new ClanDAO(databaseManager, getLogger());
        memberDAO       = new MemberDAO(databaseManager, getLogger());
        bountyDAO       = new BountyDAO(databaseManager, getLogger());
        warDAO          = new WarDAO(databaseManager, getLogger());
        coinsDAO        = new CoinsDAO(databaseManager, getLogger());
        contributionDAO = new ContributionDAO(databaseManager, getLogger());
        hallDAO         = new HallDAO(databaseManager, getLogger());    // NEW
    }

    private boolean initEconomyProvider() {
        String providerName = configManager.getEconomyProvider().toUpperCase();

        switch (providerName) {
            case "PLAYERPOINTS" -> {
                if (hookManager.isPlayerPointsEnabled()) {
                    economyProvider = new PlayerPointsProvider(hookManager.getPlayerPoints());
                    getLogger().info("[Economy] Provider: PlayerPoints");
                    return true;
                }
                getLogger().warning("[Economy] PlayerPoints not available, falling back to Vault.");
            }
            case "COINS" -> {
                getLogger().info("[Economy] Provider: Built-in Coins (using Vault as fallback)");
            }
        }

        if (!hookManager.isVaultEnabled()) {
            return false;
        }
        economyProvider = new VaultProvider(hookManager.getVaultEconomy());
        getLogger().info("[Economy] Provider: Vault (" +
                hookManager.getVaultEconomy().getName() + ")");
        return true;
    }

    private void initSchematicProvider() {
        if (!configManager.isClanHallEnabled()) {
            getLogger().info("[ClanHall] Disabled in config.");
            return;
        }

        String engine = configManager.getClanHallEngine().toUpperCase();

        FAWEStructureProvider fawe = new FAWEStructureProvider(this);
        NBTStructureProvider  nbt  = new NBTStructureProvider(this);

        switch (engine) {
            case "FAWE" -> {
                if (fawe.isAvailable()) {
                    schematicProvider = fawe;
                } else {
                    getLogger().warning("[ClanHall] FAWE requested but not available! Falling back to NBT.");
                    schematicProvider = nbt;
                }
            }
            case "NBT" -> schematicProvider = nbt;
            default -> {
                // AUTO
                schematicProvider = (hookManager.isFaweEnabled() && fawe.isAvailable()) ? fawe : nbt;
            }
        }

        getLogger().info("[ClanHall] Schematic engine: " + schematicProvider.getName());
    }

    private void initModules() {
        buffTracker         = new BuffTracker(this);
        spyScrollManager    = new SpyScrollManager(this);
        bountyManager       = new BountyManager(this);
        contributionManager = new ContributionManager(this);
        clanShopManager     = new ClanShopManager(this);
        coinsShopManager    = new CoinsShopManager(this);
        warManager          = new WarManager(this);
        warScheduler        = new WarScheduler(this);
        clanHallManager     = new ClanHallManager(this);   // NEW
        hallNPCManager      = new HallNPCManager(this);    // NEW

        bountyManager.startExpiryScheduler();
        warScheduler.schedule();
    }

    private void registerListeners() {
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerLoginListener(this),    this);
        pm.registerEvents(new PlayerQuitListener(this),     this);
        pm.registerEvents(new ChatListener(this),           this);
        pm.registerEvents(new PlayerDeathListener(this),    this);
        pm.registerEvents(new InventoryClickListener(this), this);
        pm.registerEvents(new WarListener(this),            this);
        pm.registerEvents(new PlayerMoveListener(this),     this);
        pm.registerEvents(new HallListener(this),           this);  // NEW
    }

    private void registerCommands() {
        QClanCommand      qclan      = new QClanCommand(this);
        QClanAdminCommand qclanAdmin = new QClanAdminCommand(this);

        var qclanCmd = getCommand("qclan");
        if (qclanCmd != null) {
            qclanCmd.setExecutor(qclan);
            qclanCmd.setTabCompleter(qclan);
        }

        var adminCmd = getCommand("qclanadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(qclanAdmin);
            adminCmd.setTabCompleter(qclanAdmin);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Public reload
    // ─────────────────────────────────────────────────────────

    public void reload() {
        reloadConfig();
        saveDefaultConfigs();
        loadConfigs();
        getLogger().info("[QuantumClan] Configuration reloaded.");
    }

    // ─────────────────────────────────────────────────────────
    // Message helpers
    // ─────────────────────────────────────────────────────────

    public void sendMessage(org.bukkit.command.CommandSender sender, String messageKey,
                            String... replacements) {
        String raw = messagesManager.get(messageKey, replacements);
        if (raw == null || raw.isBlank()) return;
        String prefix = messagesManager.getRaw("prefix");
        String full   = (prefix != null ? prefix : "") + raw;
        sender.sendMessage(mm.deserialize(full));
    }

    public void sendRaw(org.bukkit.command.CommandSender sender, String miniMessage) {
        sender.sendMessage(mm.deserialize(miniMessage));
    }

    public void broadcast(String miniMessage) {
        Component component = mm.deserialize(miniMessage);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }

    // ─────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────

    public static QuantumClan getInstance()              { return instance; }
    public ConfigManager      getConfigManager()         { return configManager; }
    public MessagesManager    getMessagesManager()       { return messagesManager; }
    public ShopConfigManager  getShopConfigManager()     { return shopConfigManager; }
    public WarConfigManager   getWarConfigManager()      { return warConfigManager; }
    public RolesConfigManager getRolesConfigManager()    { return rolesConfigManager; }
    public GuiConfigManager   getGuiConfigManager()      { return guiConfigManager; }
    public HallConfigManager  getHallConfigManager()     { return hallConfigManager; }  // NEW
    public DatabaseManager    getDatabaseManager()       { return databaseManager; }
    public ClanDAO            getClanDAO()               { return clanDAO; }
    public MemberDAO          getMemberDAO()             { return memberDAO; }
    public BountyDAO          getBountyDAO()             { return bountyDAO; }
    public WarDAO             getWarDAO()                { return warDAO; }
    public CoinsDAO           getCoinsDAO()              { return coinsDAO; }
    public ContributionDAO    getContributionDAO()       { return contributionDAO; }
    public HallDAO            getHallDAO()               { return hallDAO; }             // NEW
    public HookManager        getHookManager()           { return hookManager; }
    public EconomyProvider    getEconomyProvider()       { return economyProvider; }
    public CoinsProvider      getCoinsProvider()         { return coinsProvider; }
    public SchematicProvider  getSchematicProvider()     { return schematicProvider; }
    public ClanManager        getClanManager()           { return clanManager; }
    public ChatInputManager   getChatInputManager()      { return chatInputManager; }
    public BountyManager      getBountyManager()         { return bountyManager; }
    public WarManager         getWarManager()            { return warManager; }
    public WarScheduler       getWarScheduler()          { return warScheduler; }
    public ClanShopManager    getClanShopManager()       { return clanShopManager; }
    public CoinsShopManager   getCoinsShopManager()      { return coinsShopManager; }
    public BuffTracker        getBuffTracker()           { return buffTracker; }
    public ContributionManager getContributionManager() { return contributionManager; }
    public SpyScrollManager   getSpyScrollManager()     { return spyScrollManager; }
    public ClanHallManager    getClanHallManager()       { return clanHallManager; }    // NEW
    public HallNPCManager     getHallNPCManager()        { return hallNPCManager; }      // NEW
    public MiniMessage        getMiniMessage()           { return mm; }
}