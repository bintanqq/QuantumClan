package me.bintanq.quantumclan.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantumclan.QuantumClan;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Manages the SQLite database connection pool via HikariCP.
 *
 * Responsibilities:
 *  - Initialize and configure HikariDataSource
 *  - Create all tables and indexes on startup
 *  - Provide Connection acquisition for DAO classes
 *  - Provide a shared async Executor for database operations
 *  - Graceful shutdown on plugin disable
 */
public class DatabaseManager {

    private final QuantumClan plugin;
    private HikariDataSource dataSource;

    /**
     * Dedicated thread pool for all async database operations.
     * Sized to match HikariCP pool size to avoid thread contention.
     */
    private final Executor dbExecutor;
    private static final int POOL_SIZE = 5;

    public DatabaseManager(QuantumClan plugin) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newFixedThreadPool(POOL_SIZE, r -> {
            Thread t = new Thread(r, "QuantumClan-DB-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Initialization ────────────────────────────────────────

    /**
     * Initializes the HikariCP connection pool and creates all tables.
     * Call once from onEnable().
     *
     * @return true if initialization succeeded, false on failure
     */
    public boolean init() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "quantumclan.db");

        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(POOL_SIZE);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30_000);
            config.setIdleTimeout(600_000);
            config.setMaxLifetime(1_800_000);
            config.setPoolName("QuantumClan-Pool");

            // SQLite-specific pragmas for performance and safety
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous",  "NORMAL");
            config.addDataSourceProperty("foreign_keys", "true");
            config.addDataSourceProperty("busy_timeout", "10000");

            // Disable HikariCP's internal metrics (not needed)
            config.setInitializationFailTimeout(10_000);

            dataSource = new HikariDataSource(config);

            createTables();
            applyIndexes();

            plugin.getLogger().info("[Database] SQLite initialized successfully.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[Database] Failed to initialize database!", e);
            return false;
        }
    }

    // ── Table Creation ────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable WAL and foreign keys per connection
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA foreign_keys = ON");

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
                    shield_until  TIMESTAMP    NULL,
                    hall_world    VARCHAR(64)  NULL,
                    hall_x        REAL         NULL,
                    hall_y        REAL         NULL,
                    hall_z        REAL         NULL,
                    created_at    TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                )
                """);

            // ── clan_members ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    uuid                VARCHAR(36)  PRIMARY KEY,
                    clan_id             VARCHAR(36)  NOT NULL,
                    role                VARCHAR(32)  NOT NULL DEFAULT 'member',
                    contribution_points INTEGER      NOT NULL DEFAULT 0,
                    joined_at           TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """);

            // ── clan_homes ─────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    id        VARCHAR(36)  PRIMARY KEY,
                    clan_id   VARCHAR(36)  NOT NULL,
                    name      VARCHAR(32)  NOT NULL,
                    world     VARCHAR(64)  NOT NULL,
                    x         REAL         NOT NULL,
                    y         REAL         NOT NULL,
                    z         REAL         NOT NULL,
                    yaw       REAL         NOT NULL DEFAULT 0.0,
                    pitch     REAL         NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    UNIQUE (clan_id, name)
                )
                """);

            // ── clan_buffs_pending ─────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_buffs_pending (
                    id               VARCHAR(36)  PRIMARY KEY,
                    uuid             VARCHAR(36)  NOT NULL,
                    effect_type      VARCHAR(32)  NOT NULL,
                    amplifier        INTEGER      NOT NULL DEFAULT 0,
                    duration_seconds INTEGER      NOT NULL,
                    expires_at       TIMESTAMP    NOT NULL,
                    FOREIGN KEY (uuid) REFERENCES clan_members(uuid) ON DELETE CASCADE
                )
                """);

            // ── bounties ───────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bounties (
                    id                VARCHAR(36)  PRIMARY KEY,
                    clan_id_poster    VARCHAR(36)  NOT NULL,
                    clan_id_target    VARCHAR(36)  NOT NULL,
                    target_uuid       VARCHAR(36)  NOT NULL,
                    amount            BIGINT       NOT NULL,
                    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                    head_claimed      INTEGER      NOT NULL DEFAULT 0,
                    posted_at         TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
                    expires_at        TIMESTAMP    NOT NULL
                )
                """);

            // ── bounty_heads ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bounty_heads (
                    head_id     VARCHAR(36)  PRIMARY KEY,
                    bounty_id   VARCHAR(36)  NOT NULL,
                    submitted   INTEGER      NOT NULL DEFAULT 0,
                    FOREIGN KEY (bounty_id) REFERENCES bounties(id) ON DELETE CASCADE
                )
                """);

            // ── war_sessions ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS war_sessions (
                    id              VARCHAR(36)  PRIMARY KEY,
                    format          VARCHAR(16)  NOT NULL,
                    started_at      TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
                    ended_at        TIMESTAMP    NULL,
                    winner_clan_id  VARCHAR(36)  NULL
                )
                """);

            // ── war_participants ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS war_participants (
                    session_id    VARCHAR(36)  NOT NULL,
                    clan_id       VARCHAR(36)  NOT NULL,
                    member_uuid   VARCHAR(36)  NOT NULL,
                    kills         INTEGER      NOT NULL DEFAULT 0,
                    eliminated    INTEGER      NOT NULL DEFAULT 0,
                    PRIMARY KEY (session_id, member_uuid),
                    FOREIGN KEY (session_id) REFERENCES war_sessions(id) ON DELETE CASCADE
                )
                """);

            // ── coins_ledger ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins_ledger (
                    id        INTEGER      PRIMARY KEY AUTOINCREMENT,
                    uuid      VARCHAR(36)  NOT NULL,
                    amount    BIGINT       NOT NULL,
                    reason    VARCHAR(128) NOT NULL DEFAULT '',
                    timestamp TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                )
                """);

            // ── coins_balance ──────────────────────────────────
            // Separate balance table for O(1) reads (ledger is append-only audit log)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins_balance (
                    uuid      VARCHAR(36)  PRIMARY KEY,
                    balance   BIGINT       NOT NULL DEFAULT 0
                )
                """);

            // ── contribution_log ───────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contribution_log (
                    id        INTEGER      PRIMARY KEY AUTOINCREMENT,
                    uuid      VARCHAR(36)  NOT NULL,
                    clan_id   VARCHAR(36)  NOT NULL,
                    points    INTEGER      NOT NULL,
                    reason    VARCHAR(128) NOT NULL DEFAULT '',
                    timestamp TIMESTAMP    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                )
                """);

            plugin.getLogger().info("[Database] All tables created/verified.");
        }
    }

    // ── Index Creation ────────────────────────────────────────

    private void applyIndexes() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // clan_members
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_members_clan_id " +
                    "ON clan_members(clan_id)");

            // clan_homes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_clan_homes_clan_id " +
                    "ON clan_homes(clan_id)");

            // clan_buffs_pending
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_buffs_uuid " +
                    "ON clan_buffs_pending(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_buffs_expires " +
                    "ON clan_buffs_pending(expires_at)");

            // bounties
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounties_target_uuid " +
                    "ON bounties(target_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounties_status " +
                    "ON bounties(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounties_expires " +
                    "ON bounties(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounties_clan_poster " +
                    "ON bounties(clan_id_poster)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounties_clan_target " +
                    "ON bounties(clan_id_target)");

            // bounty_heads
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bounty_heads_bounty_id " +
                    "ON bounty_heads(bounty_id)");

            // war_participants
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_war_participants_session " +
                    "ON war_participants(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_war_participants_clan " +
                    "ON war_participants(clan_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_war_participants_uuid " +
                    "ON war_participants(member_uuid)");

            // coins_ledger
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_coins_ledger_uuid " +
                    "ON coins_ledger(uuid)");

            // contribution_log
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_contribution_uuid " +
                    "ON contribution_log(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_contribution_clan " +
                    "ON contribution_log(clan_id)");

            // clans — for reputation leaderboard ordering
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_clans_reputation " +
                    "ON clans(reputation DESC)");

            plugin.getLogger().info("[Database] Indexes applied.");
        }
    }

    // ── Schema Migration ──────────────────────────────────────

    /**
     * Lightweight schema migration helper.
     * Adds a column if it does not exist yet (SQLite ALTER TABLE ADD COLUMN).
     * Safe to call on every startup.
     */
    public void addColumnIfNotExists(String table, String column, String definition) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // SQLite doesn't support IF NOT EXISTS on ALTER TABLE — catch exception instead
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            // "duplicate column name" error is expected and safe to ignore
            if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                plugin.getLogger().log(Level.WARNING,
                        "[Database] Migration warning for column " + column + ": " + e.getMessage());
            }
        }
    }

    // ── Connection ────────────────────────────────────────────

    /**
     * Acquires a Connection from the pool.
     * Always use in try-with-resources to return it to the pool.
     *
     * @return Active Connection
     * @throws SQLException if a connection could not be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    // ── Async Executor ────────────────────────────────────────

    /**
     * Returns the shared Executor for async database tasks.
     * Use with CompletableFuture.supplyAsync(() -> ..., db.getExecutor()).
     */
    public Executor getExecutor() {
        return dbExecutor;
    }

    /**
     * Convenience: run a Runnable on the DB executor.
     */
    public void async(Runnable task) {
        dbExecutor.execute(task);
    }

    /**
     * Convenience: supply a value async on the DB executor.
     */
    public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor);
    }

    /**
     * Convenience: run async on DB executor, return CompletableFuture<Void>.
     */
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor);
    }

    // ── Shutdown ──────────────────────────────────────────────

    /**
     * Closes the HikariCP connection pool.
     * Call from onDisable().
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[Database] Connection pool closed.");
        }
    }

    /**
     * Returns true if the datasource is active and not closed.
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // ── Batch Helper ──────────────────────────────────────────

    /**
     * Executes a batch of SQL statements within a single transaction.
     * Rolls back automatically on failure.
     *
     * @param task A consumer that receives the Connection and should add batch entries.
     * @return CompletableFuture<Boolean> — true if batch succeeded
     */
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

    /**
     * Functional interface for batch DB tasks that may throw SQLException.
     */
    @FunctionalInterface
    public interface BatchTask {
        void execute(Connection conn) throws SQLException;
    }
}