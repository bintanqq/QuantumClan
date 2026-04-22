package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CoinsDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public CoinsDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Get balance ───────────────────────────────────────────

    public CompletableFuture<Long> getCoins(UUID uuid) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT balance FROM coins_balance WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong("balance");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[CoinsDAO] getCoins failed for " + uuid, e);
            }
            return 0L;
        });
    }

    // ── Set balance (upsert) + ledger entry ───────────────────

    public CompletableFuture<Boolean> setCoins(UUID uuid, long newBalance, String reason) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Upsert balance
                    String upsertSql = """
                        INSERT INTO coins_balance (uuid, balance) VALUES (?,?)
                        ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                        ps.setString(1, uuid.toString());
                        ps.setLong(2,   newBalance);
                        ps.executeUpdate();
                    }

                    // Append ledger entry
                    String ledgerSql = """
                        INSERT INTO coins_ledger (uuid, amount, reason)
                        VALUES (?,?,?)
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(ledgerSql)) {
                        ps.setString(1, uuid.toString());
                        ps.setLong(2,   newBalance);
                        ps.setString(3, reason != null ? reason : "");
                        ps.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    logger.log(Level.WARNING, "[CoinsDAO] setCoins transaction rolled back for " + uuid, e);
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[CoinsDAO] setCoins connection failed for " + uuid, e);
                return false;
            }
        });
    }

    // ── Ensure row exists ─────────────────────────────────────

    public CompletableFuture<Boolean> ensureExists(UUID uuid) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT OR IGNORE INTO coins_balance (uuid, balance) VALUES (?,0)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[CoinsDAO] ensureExists failed for " + uuid, e);
                return false;
            }
        });
    }
}