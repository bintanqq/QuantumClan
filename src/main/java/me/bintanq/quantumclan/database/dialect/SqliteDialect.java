package me.bintanq.quantumclan.database.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQL dialect for SQLite (default backend).
 */
public class SqliteDialect implements SqlDialect {

    @Override
    public String autoIncrement() {
        return "AUTOINCREMENT";
    }

    @Override
    public String currentTimestamp() {
        return "(strftime('%Y-%m-%dT%H:%M:%SZ','now'))";
    }

    @Override
    public String tableOptions() {
        return "";
    }

    @Override
    public String upsertCoinsBalance() {
        return """
               INSERT INTO coins_balance (uuid, balance) VALUES (?,?)
               ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance
               """;
    }

    @Override
    public String insertIgnoreCoinsBalance() {
        return "INSERT OR IGNORE INTO coins_balance (uuid, balance) VALUES (?,0)";
    }

    @Override
    public String upsertHallAccess() {
        return """
               INSERT INTO clan_hall_access (clan_id, purchased_at, expires_at, active)
               VALUES (?, strftime('%Y-%m-%dT%H:%M:%SZ','now'), ?, 1)
               ON CONFLICT(clan_id) DO UPDATE SET
                   purchased_at = strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                   expires_at   = excluded.expires_at,
                   active       = 1
               """;
    }

    @Override
    public String upsertVault() {
        return """
               INSERT INTO clan_vault (clan_id, contents, updated_at)
               VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%SZ','now'))
               ON CONFLICT(clan_id) DO UPDATE SET
                   contents   = excluded.contents,
                   updated_at = strftime('%Y-%m-%dT%H:%M:%SZ','now')
               """;
    }

    @Override
    public String upsertVaultClear() {
        return """
               INSERT INTO clan_vault (clan_id, contents, updated_at)
               VALUES (?, '', strftime('%Y-%m-%dT%H:%M:%SZ','now'))
               ON CONFLICT(clan_id) DO UPDATE SET
                   contents   = '',
                   updated_at = strftime('%Y-%m-%dT%H:%M:%SZ','now')
               """;
    }

    @Override
    public void applyConnectionProps(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA busy_timeout = 10000");
        }
    }

    @Override
    public String nowExpression() {
        return "strftime('%Y-%m-%dT%H:%M:%SZ','now')";
    }
}