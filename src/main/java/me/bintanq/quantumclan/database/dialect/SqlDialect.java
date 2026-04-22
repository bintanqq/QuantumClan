package me.bintanq.quantumclan.database.dialect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstracts SQL syntax differences between SQLite and MySQL/MariaDB.
 * Every dialect-specific expression is centralised here so all DAOs
 * remain 100 % database-agnostic.
 */
public interface SqlDialect {

    // ── DDL helpers ───────────────────────────────────────────

    /**
     * Returns the correct AUTO INCREMENT keyword for a PRIMARY KEY integer.
     * SQLite: {@code AUTOINCREMENT}
     * MySQL : {@code AUTO_INCREMENT}
     */
    String autoIncrement();

    /**
     * Returns the current UTC timestamp expression suitable for a DEFAULT clause.
     * SQLite: {@code (strftime('%Y-%m-%dT%H:%M:%SZ','now'))}
     * MySQL : {@code UTC_TIMESTAMP()}
     */
    String currentTimestamp();

    /**
     * Returns an optional table-level ENGINE clause appended after the closing
     * parenthesis of a CREATE TABLE statement.
     * SQLite: {@code ""}  (empty — no engine clause)
     * MySQL : {@code " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"}
     */
    String tableOptions();

    // ── DML helpers ───────────────────────────────────────────

    /**
     * Returns a complete UPSERT statement for {@code coins_balance}.
     * <p>
     * SQLite: {@code INSERT INTO coins_balance (uuid, balance) VALUES (?,?)
     *               ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance}
     * <p>
     * MySQL : {@code INSERT INTO coins_balance (uuid, balance) VALUES (?,?)
     *               ON DUPLICATE KEY UPDATE balance=VALUES(balance)}
     */
    String upsertCoinsBalance();

    /**
     * Returns an INSERT-OR-IGNORE statement for {@code coins_balance}.
     * <p>
     * SQLite: {@code INSERT OR IGNORE INTO coins_balance (uuid, balance) VALUES (?,0)}
     * MySQL : {@code INSERT IGNORE INTO coins_balance (uuid, balance) VALUES (?,0)}
     */
    String insertIgnoreCoinsBalance();

    /**
     * Returns a complete UPSERT for {@code clan_hall_access}.
     * <p>
     * SQLite uses ON CONFLICT … DO UPDATE; MySQL uses ON DUPLICATE KEY UPDATE.
     */
    String upsertHallAccess();

    /**
     * Returns a complete UPSERT for {@code clan_vault}.
     */
    String upsertVault();

    /**
     * Returns a complete UPSERT (clear) for {@code clan_vault} (sets contents to '').
     */
    String upsertVaultClear();

    // ── Connection initialisation ─────────────────────────────

    /**
     * Called once per new connection to apply any dialect-specific settings.
     * <p>
     * SQLite: executes {@code PRAGMA journal_mode = WAL} and
     *         {@code PRAGMA foreign_keys = ON}.
     * MySQL : no-op (settings are handled at the driver/server level).
     *
     * @param conn a freshly acquired connection
     * @throws SQLException if a PRAGMA statement fails
     */
    void applyConnectionProps(Connection conn) throws SQLException;

    // ── Timestamp comparison ──────────────────────────────────

    /**
     * Returns the expression used to compare a TIMESTAMP column against "now"
     * in WHERE clauses.
     * <p>
     * SQLite: {@code strftime('%Y-%m-%dT%H:%M:%SZ','now')}
     * MySQL : {@code UTC_TIMESTAMP()}
     */
    String nowExpression();
}