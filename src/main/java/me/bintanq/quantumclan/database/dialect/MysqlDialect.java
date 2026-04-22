package me.bintanq.quantumclan.database.dialect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQL dialect for MySQL / MariaDB.
 */
public class MysqlDialect implements SqlDialect {

    @Override
    public String autoIncrement() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String currentTimestamp() {
        return "UTC_TIMESTAMP()";
    }

    @Override
    public String tableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    @Override
    public String upsertCoinsBalance() {
        return """
               INSERT INTO coins_balance (uuid, balance) VALUES (?,?)
               ON DUPLICATE KEY UPDATE balance=VALUES(balance)
               """;
    }

    @Override
    public String insertIgnoreCoinsBalance() {
        return "INSERT IGNORE INTO coins_balance (uuid, balance) VALUES (?,0)";
    }

    @Override
    public String upsertHallAccess() {
        return """
               INSERT INTO clan_hall_access (clan_id, purchased_at, expires_at, active)
               VALUES (?, UTC_TIMESTAMP(), ?, 1)
               ON DUPLICATE KEY UPDATE
                   purchased_at = UTC_TIMESTAMP(),
                   expires_at   = VALUES(expires_at),
                   active       = 1
               """;
    }

    @Override
    public String upsertVault() {
        return """
               INSERT INTO clan_vault (clan_id, contents, updated_at)
               VALUES (?, ?, UTC_TIMESTAMP())
               ON DUPLICATE KEY UPDATE
                   contents   = VALUES(contents),
                   updated_at = UTC_TIMESTAMP()
               """;
    }

    @Override
    public String upsertVaultClear() {
        return """
               INSERT INTO clan_vault (clan_id, contents, updated_at)
               VALUES (?, '', UTC_TIMESTAMP())
               ON DUPLICATE KEY UPDATE
                   contents   = '',
                   updated_at = UTC_TIMESTAMP()
               """;
    }

    @Override
    public void applyConnectionProps(Connection conn) throws SQLException {
        // MySQL handles foreign keys and WAL-equivalent through InnoDB;
        // nothing to set per-connection here.
        // Character set is configured at the connection URL level.
    }

    @Override
    public String nowExpression() {
        return "UTC_TIMESTAMP()";
    }
}