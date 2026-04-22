package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.model.HallAccess;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO for the clan_hall_access table.
 *
 * Table DDL (added to DatabaseManager.createTables()):
 * CREATE TABLE IF NOT EXISTS clan_hall_access (
 *     clan_id      VARCHAR(36) PRIMARY KEY,
 *     purchased_at TIMESTAMP   NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 *     expires_at   TIMESTAMP   NULL,
 *     active       INTEGER     NOT NULL DEFAULT 1
 * )
 */
public class HallDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public HallDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    // ── Insert / Upsert ───────────────────────────────────────

    /**
     * Inserts or replaces an access record for the given clan.
     *
     * @param clanId    The clan's UUID string
     * @param expiresAt null = permanent, or a future Instant for DURATION mode
     */
    public CompletableFuture<Boolean> insertAccess(String clanId, Instant expiresAt) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO clan_hall_access (clan_id, purchased_at, expires_at, active)
                VALUES (?, strftime('%Y-%m-%dT%H:%M:%SZ','now'), ?, 1)
                ON CONFLICT(clan_id) DO UPDATE SET
                    purchased_at = strftime('%Y-%m-%dT%H:%M:%SZ','now'),
                    expires_at   = excluded.expires_at,
                    active       = 1
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                ps.setString(2, expiresAt != null ? expiresAt.toString() : null);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] insertAccess failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Revoke ────────────────────────────────────────────────

    /**
     * Marks a clan's hall access as inactive (revoked).
     */
    public CompletableFuture<Boolean> revokeAccess(String clanId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clan_hall_access SET active=0 WHERE clan_id=?")) {
                ps.setString(1, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] revokeAccess failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Query ─────────────────────────────────────────────────

    /**
     * Returns true if the clan currently has valid (active + not expired) access.
     */
    public CompletableFuture<Boolean> hasAccess(String clanId) {
        return db.supplyAsync(() -> {
            String sql = """
                SELECT 1 FROM clan_hall_access
                WHERE clan_id=? AND active=1
                  AND (expires_at IS NULL OR expires_at > strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                LIMIT 1
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] hasAccess failed for " + clanId, e);
                return false;
            }
        });
    }

    /**
     * Loads a single access record, or null if not found.
     */
    public CompletableFuture<HallAccess> findByClan(String clanId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clan_hall_access WHERE clan_id=?")) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] findByClan failed for " + clanId, e);
            }
            return null;
        });
    }

    /**
     * Loads all currently active (and not expired) access records.
     * Used during startup to populate the in-memory cache.
     */
    public CompletableFuture<List<HallAccess>> loadAllActive() {
        return db.supplyAsync(() -> {
            List<HallAccess> list = new ArrayList<>();
            String sql = """
                SELECT * FROM clan_hall_access
                WHERE active=1
                  AND (expires_at IS NULL OR expires_at > strftime('%Y-%m-%dT%H:%M:%SZ','now'))
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] loadAllActive failed", e);
            }
            return list;
        });
    }

    /**
     * Marks all expired records as inactive.
     * Run hourly via the ClanHallManager scheduler.
     */
    public CompletableFuture<Void> cleanExpired() {
        return db.runAsync(() -> {
            String sql = """
                UPDATE clan_hall_access SET active=0
                WHERE active=1 AND expires_at IS NOT NULL
                  AND expires_at <= strftime('%Y-%m-%dT%H:%M:%SZ','now')
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    // Logger is not directly accessible here in runAsync lambda,
                    // but we log from the manager. Just silently complete.
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] cleanExpired failed", e);
            }
        });
    }

    // ── Row mapper ────────────────────────────────────────────

    private HallAccess mapRow(ResultSet rs) throws SQLException {
        String expiresStr = rs.getString("expires_at");
        Instant expiresAt = (expiresStr != null) ? Instant.parse(expiresStr) : null;
        return new HallAccess(
                rs.getString("clan_id"),
                Instant.parse(rs.getString("purchased_at")),
                expiresAt,
                rs.getInt("active") == 1
        );
    }
}