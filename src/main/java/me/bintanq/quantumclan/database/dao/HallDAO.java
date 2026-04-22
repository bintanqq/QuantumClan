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

public class HallDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public HallDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert / Upsert ───────────────────────────────────────

    public CompletableFuture<Boolean> insertAccess(String clanId, Instant expiresAt) {
        return db.supplyAsync(() -> {
            String sql = db.getDialect().upsertHallAccess();
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

    public CompletableFuture<Boolean> hasAccess(String clanId) {
        return db.supplyAsync(() -> {
            String now = db.getDialect().nowExpression();
            String sql = """
                SELECT 1 FROM clan_hall_access
                WHERE clan_id=? AND active=1
                  AND (expires_at IS NULL OR expires_at > %s)
                LIMIT 1
                """.formatted(now);
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

    public CompletableFuture<List<HallAccess>> loadAllActive() {
        return db.supplyAsync(() -> {
            List<HallAccess> list = new ArrayList<>();
            String now = db.getDialect().nowExpression();
            String sql = """
                SELECT * FROM clan_hall_access
                WHERE active=1
                  AND (expires_at IS NULL OR expires_at > %s)
                """.formatted(now);
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

    public CompletableFuture<Void> cleanExpired() {
        return db.runAsync(() -> {
            String now = db.getDialect().nowExpression();
            String sql = """
                UPDATE clan_hall_access SET active=0
                WHERE active=1 AND expires_at IS NOT NULL
                  AND expires_at <= %s
                """.formatted(now);
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[HallDAO] cleanExpired failed", e);
            }
        });
    }

    // ── Row mapper ────────────────────────────────────────────

    private HallAccess mapRow(ResultSet rs) throws SQLException {
        String expiresStr = rs.getString("expires_at");
        Instant expiresAt = (expiresStr != null && !expiresStr.isEmpty())
                ? Instant.parse(expiresStr) : null;
        String purchasedStr = rs.getString("purchased_at");
        Instant purchasedAt = (purchasedStr != null && !purchasedStr.isEmpty())
                ? Instant.parse(purchasedStr) : Instant.now();
        return new HallAccess(
                rs.getString("clan_id"),
                purchasedAt,
                expiresAt,
                rs.getInt("active") == 1
        );
    }
}