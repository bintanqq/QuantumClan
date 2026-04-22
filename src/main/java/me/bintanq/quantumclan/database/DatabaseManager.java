package me.bintanq.quantumclan.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dialect.MysqlDialect;
import me.bintanq.quantumclan.database.dialect.SqlDialect;
import me.bintanq.quantumclan.database.dialect.SqliteDialect;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Manages the database connection pool via HikariCP.
 * Supports SQLite (default) and MySQL/MariaDB.
 *
 * All SQL dialect differences are encapsulated in {@link SqlDialect}
 * implementations; DAO classes never need to know which backend is active.
 */
public class DatabaseManager {

    private final QuantumClan plugin;
    private HikariDataSource dataSource;
    private SqlDialect dialect;
    private DatabaseType dbType;

    private final Executor dbExecutor;

    public DatabaseManager(QuantumClan plugin) {
        this.plugin = plugin;
        int poolSize = resolvePoolSize();
        this.dbExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "QuantumClan-DB-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Initialization ────────────────────────────────────────

    public boolean init() {
        String typeStr = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        try {
            dbType = DatabaseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Database] Unknown database type '" + typeStr + "', defaulting to SQLITE.");
            dbType = DatabaseType.SQLITE;
        }

        try {
            if (dbType == DatabaseType.MYSQL) {
                initMySQL();
            } else {
                initSQLite();
            }
            createTables();
            applyIndexes();
            plugin.getLogger().info("[Database] " + dbType + " initialized successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Failed to initialize database!", e);
            return false;
        }
    }

    // ── SQLite setup ──────────────────────────────────────────

    private void initSQLite() throws Exception {
        dialect = new SqliteDialect();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        String dbFile = plugin.getConfig().getString("database.sqlite.file", "quantumclan.db");
        File file = new File(dataFolder, dbFile);

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("QuantumClan-SQLite-Pool");
        config.setConnectionTestQuery("SELECT 1");
        config.setInitializationFailTimeout(10_000);

        dataSource = new HikariDataSource(config);
    }

    // ── MySQL setup ───────────────────────────────────────────

    private void initMySQL() throws Exception {
        dialect = new MysqlDialect();

        String host     = plugin.getConfig().getString("database.mysql.host", "localhost");
        int    port     = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "quantumclan");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        int    poolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);
        long   timeout  = plugin.getConfig().getLong("database.mysql.connection-timeout", 30_000L);

        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/').append(database)
                .append("?useSSL=false")
                .append("&autoReconnect=true")
                .append("&characterEncoding=utf8mb4")
                .append("&useUnicode=true")
                .append("&serverTimezone=UTC")
                .append("&allowPublicKeyRetrieval=true");

        // Merge extra properties from config
        var props = plugin.getConfig().getConfigurationSection("database.mysql.properties");
        if (props != null) {
            for (String key : props.getKeys(false)) {
                // Skip ones we already set
                if (key.equals("useSSL") || key.equals("autoReconnect")
                        || key.equals("characterEncoding")) continue;
                url.append('&').append(key).append('=').append(props.getString(key));
            }
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(url.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(1, poolSize / 4));
        config.setConnectionTimeout(timeout);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setKeepaliveTime(60_000);
        config.setPoolName("QuantumClan-MySQL-Pool");
        config.setConnectionTestQuery("SELECT 1");
        config.setInitializationFailTimeout(10_000);

        dataSource = new HikariDataSource(config);
    }

    // ── Table Creation ────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            dialect.applyConnectionProps(conn);

            String now  = dialect.currentTimestamp();
            String opts = dialect.tableOptions();
            String ai   = dialect.autoIncrement();

            // ── clans ──────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clans (
                    id            VARCHAR(36)  PRIMARY KEY,
                    name          VARCHAR(32)  NOT NULL UNIQUE,
                    tag           VARCHAR(8)   NOT NULL,
                    tag_color     VARCHAR(64)  NOT NULL DEFAULT '',
                    level         INTEGER      NOT NULL DEFAULT 1,
                    money         BIGINT       NOT NULL DEFAULT 0,
                    reputation    INTEGER      NOT NULL DEFAULT 0,
                    leader_uuid   VARCHAR(36)  NOT NULL,
                    shield_until  VARCHAR(32)  NULL,
                    hall_world    VARCHAR(64)  NULL,
                    hall_x        DOUBLE       NULL,
                    hall_y        DOUBLE       NULL,
                    hall_z        DOUBLE       NULL,
                    created_at    VARCHAR(32)  NOT NULL DEFAULT %s
                )%s
                """.formatted(now, opts));

            // ── clan_members ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    uuid                VARCHAR(36)  PRIMARY KEY,
                    clan_id             VARCHAR(36)  NOT NULL,
                    role                VARCHAR(32)  NOT NULL DEFAULT 'member',
                    contribution_points INTEGER      NOT NULL DEFAULT 0,
                    joined_at           VARCHAR(32)  NOT NULL DEFAULT %s,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )%s
                """.formatted(now, opts));

            // ── clan_homes ─────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    id        VARCHAR(36)  PRIMARY KEY,
                    clan_id   VARCHAR(36)  NOT NULL,
                    name      VARCHAR(32)  NOT NULL,
                    world     VARCHAR(64)  NOT NULL,
                    x         DOUBLE       NOT NULL,
                    y         DOUBLE       NOT NULL,
                    z         DOUBLE       NOT NULL,
                    yaw       DOUBLE       NOT NULL DEFAULT 0.0,
                    pitch     DOUBLE       NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    UNIQUE (clan_id, name)
                )%s
                """.formatted(opts));

            // ── clan_buffs_pending ─────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_buffs_pending (
                    id               VARCHAR(36)  PRIMARY KEY,
                    uuid             VARCHAR(36)  NOT NULL,
                    effect_type      VARCHAR(32)  NOT NULL,
                    amplifier        INTEGER      NOT NULL DEFAULT 0,
                    duration_seconds INTEGER      NOT NULL,
                    expires_at       VARCHAR(32)  NOT NULL,
                    FOREIGN KEY (uuid) REFERENCES clan_members(uuid) ON DELETE CASCADE
                )%s
                """.formatted(opts));

            // ── bounties ───────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bounties (
                    id                VARCHAR(36)  PRIMARY KEY,
                    clan_id_poster    VARCHAR(36)  NOT NULL,
                    clan_id_target    VARCHAR(36)  NOT NULL,
                    target_uuid       VARCHAR(36)  NOT NULL,
                    amount            BIGINT       NOT NULL,
                    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                    head_claimed      TINYINT      NOT NULL DEFAULT 0,
                    posted_at         VARCHAR(32)  NOT NULL DEFAULT %s,
                    expires_at        VARCHAR(32)  NOT NULL
                )%s
                """.formatted(now, opts));

            // ── bounty_heads ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bounty_heads (
                    head_id     VARCHAR(36)  PRIMARY KEY,
                    bounty_id   VARCHAR(36)  NOT NULL,
                    submitted   TINYINT      NOT NULL DEFAULT 0,
                    FOREIGN KEY (bounty_id) REFERENCES bounties(id) ON DELETE CASCADE
                )%s
                """.formatted(opts));

            // ── war_sessions ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS war_sessions (
                    id              VARCHAR(36)  PRIMARY KEY,
                    format          VARCHAR(16)  NOT NULL,
                    started_at      VARCHAR(32)  NOT NULL DEFAULT %s,
                    ended_at        VARCHAR(32)  NULL,
                    winner_clan_id  VARCHAR(36)  NULL
                )%s
                """.formatted(now, opts));

            // ── war_participants ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS war_participants (
                    session_id    VARCHAR(36)  NOT NULL,
                    clan_id       VARCHAR(36)  NOT NULL,
                    member_uuid   VARCHAR(36)  NOT NULL,
                    kills         INTEGER      NOT NULL DEFAULT 0,
                    eliminated    TINYINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (session_id, member_uuid),
                    FOREIGN KEY (session_id) REFERENCES war_sessions(id) ON DELETE CASCADE
                )%s
                """.formatted(opts));

            // ── coins_ledger ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins_ledger (
                    id        INTEGER PRIMARY KEY %s,
                    uuid      VARCHAR(36)  NOT NULL,
                    amount    BIGINT       NOT NULL,
                    reason    VARCHAR(128) NOT NULL DEFAULT '',
                    timestamp VARCHAR(32)  NOT NULL DEFAULT %s
                )%s
                """.formatted(ai, now, opts));

            // ── coins_balance ──────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins_balance (
                    uuid      VARCHAR(36)  PRIMARY KEY,
                    balance   BIGINT       NOT NULL DEFAULT 0
                )%s
                """.formatted(opts));

            // ── contribution_log ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contribution_log (
                    id        INTEGER PRIMARY KEY %s,
                    uuid      VARCHAR(36)  NOT NULL,
                    clan_id   VARCHAR(36)  NOT NULL,
                    points    INTEGER      NOT NULL,
                    reason    VARCHAR(128) NOT NULL DEFAULT '',
                    timestamp VARCHAR(32)  NOT NULL DEFAULT %s
                )%s
                """.formatted(ai, now, opts));

            // ── clan_hall_access ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_hall_access (
                    clan_id      VARCHAR(36) PRIMARY KEY,
                    purchased_at VARCHAR(32) NOT NULL DEFAULT %s,
                    expires_at   VARCHAR(32) NULL,
                    active       TINYINT     NOT NULL DEFAULT 1
                )%s
                """.formatted(now, opts));

            // ── clan_vault ─────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_vault (
                    clan_id    VARCHAR(36) PRIMARY KEY,
                    contents   MEDIUMTEXT  NOT NULL DEFAULT '',
                    updated_at VARCHAR(32) NOT NULL DEFAULT %s
                )%s
                """.formatted(now, opts));

            plugin.getLogger().info("[Database] All tables created/verified.");
        }
    }

    public void createExtraTables() {
        // Kita jalankan secara async agar tidak memberatkan startup server
        runAsync(() -> {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                String now  = dialect.currentTimestamp();
                String opts = dialect.tableOptions();
                String textType = (dbType == DatabaseType.MYSQL) ? "MEDIUMTEXT" : "TEXT";

                // Tabel Hall Access
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_hall_access (
                    clan_id      VARCHAR(36) PRIMARY KEY,
                    purchased_at VARCHAR(32) NOT NULL DEFAULT %s,
                    expires_at   VARCHAR(32) NULL,
                    active       INTEGER     NOT NULL DEFAULT 1
                )%s
                """.formatted(now, opts));

                // Tabel Vault
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_vault (
                    clan_id    VARCHAR(36) PRIMARY KEY,
                    contents   %s          NOT NULL DEFAULT '',
                    updated_at VARCHAR(32) NOT NULL DEFAULT %s
                )%s
                """.formatted(textType, now, opts));

                // Index khusus SQLite (MySQL biasanya otomatis handle primary key index)
                if (dbType == DatabaseType.SQLITE) {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_hall_access_active ON clan_hall_access(active)");
                }

                plugin.getLogger().info("[Database] Extra tables (Hall & Vault) verified.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Failed to create extra tables", e);
            }
        });
    }

    // ── Index Creation ────────────────────────────────────────

    private void applyIndexes() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            createIndex(stmt, "idx_clan_members_clan_id", "clan_members", "clan_id");
            createIndex(stmt, "idx_clan_homes_clan_id",   "clan_homes",   "clan_id");
            createIndex(stmt, "idx_buffs_uuid",            "clan_buffs_pending", "uuid");
            createIndex(stmt, "idx_buffs_expires",         "clan_buffs_pending", "expires_at");
            createIndex(stmt, "idx_bounties_target_uuid",  "bounties", "target_uuid");
            createIndex(stmt, "idx_bounties_status",       "bounties", "status");
            createIndex(stmt, "idx_bounties_expires",      "bounties", "expires_at");
            createIndex(stmt, "idx_bounties_clan_poster",  "bounties", "clan_id_poster");
            createIndex(stmt, "idx_bounties_clan_target",  "bounties", "clan_id_target");
            createIndex(stmt, "idx_bounty_heads_bounty_id","bounty_heads", "bounty_id");
            createIndex(stmt, "idx_war_participants_session","war_participants", "session_id");
            createIndex(stmt, "idx_war_participants_clan",  "war_participants", "clan_id");
            createIndex(stmt, "idx_war_participants_uuid",  "war_participants", "member_uuid");
            createIndex(stmt, "idx_coins_ledger_uuid",      "coins_ledger",    "uuid");
            createIndex(stmt, "idx_contribution_uuid",      "contribution_log", "uuid");
            createIndex(stmt, "idx_contribution_clan",      "contribution_log", "clan_id");
            createIndex(stmt, "idx_clans_reputation",       "clans", "reputation");
            createIndex(stmt, "idx_hall_access_active",     "clan_hall_access", "active");

            plugin.getLogger().info("[Database] Indexes applied.");
        }
    }

    /**
     * Creates an index only if it does not already exist.
     * Works for both SQLite and MySQL by catching duplicate-index errors.
     */
    private void createIndex(Statement stmt, String indexName, String table, String column) {
        try {
            if (dbType == DatabaseType.MYSQL) {
                // MySQL doesn't support IF NOT EXISTS on CREATE INDEX before 8.0.35+
                // Use information_schema check approach via exception handling
                stmt.execute("CREATE INDEX " + indexName + " ON " + table + "(" + column + ")");
            } else {
                stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + column + ")");
            }
        } catch (SQLException e) {
            // Duplicate index is expected and safe to ignore on both backends
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate") && !msg.contains("already exists")
                    && !msg.contains("duplicate key name")) {
                plugin.getLogger().warning("[Database] Index warning for " + indexName + ": " + e.getMessage());
            }
        }
    }

    // ── Schema Migration Helper ───────────────────────────────

    /**
     * Adds a column if it does not exist yet.
     * Safe to call on every startup.
     */
    public void addColumnIfNotExists(String table, String column, String definition) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate column") && !msg.contains("already exists")) {
                plugin.getLogger().log(Level.WARNING,
                        "[Database] Migration warning for column " + column + ": " + e.getMessage());
            }
        }
    }

    // ── Connection ────────────────────────────────────────────

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or has been closed.");
        }
        Connection conn = dataSource.getConnection();
        // Apply per-connection dialect settings (SQLite PRAGMAs, etc.)
        try {
            dialect.applyConnectionProps(conn);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Failed to apply connection props", e);
        }
        return conn;
    }

    // ── Dialect access ────────────────────────────────────────

    /**
     * Returns the active SQL dialect. DAOs may call this when they need
     * a dialect-specific expression beyond the common helpers.
     */
    public SqlDialect getDialect() {
        return dialect;
    }

    public DatabaseType getDbType() {
        return dbType;
    }

    // ── Async helpers ─────────────────────────────────────────

    public Executor getExecutor() { return dbExecutor; }

    public void async(Runnable task) { dbExecutor.execute(task); }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor);
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor);
    }

    // ── Batch Helper ──────────────────────────────────────────

    public CompletableFuture<Boolean> executeBatch(BatchTask task) {
        return supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    task.execute(conn);
                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().log(Level.WARNING,
                            "[Database] Batch transaction rolled back.", e);
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[Database] Failed to acquire connection for batch.", e);
                return false;
            }
        });
    }

    @FunctionalInterface
    public interface BatchTask {
        void execute(Connection conn) throws SQLException;
    }

    // ── Shutdown ──────────────────────────────────────────────

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[Database] Connection pool closed.");
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // ── Private helpers ───────────────────────────────────────

    private int resolvePoolSize() {
        String typeStr = "SQLITE";
        try {
            // Config may not be loaded yet during constructor — use raw access
            typeStr = System.getProperty("qc.dbtype", "SQLITE").toUpperCase();
        } catch (Exception ignored) {}
        return typeStr.equals("MYSQL") ? 10 : 5;
    }
}