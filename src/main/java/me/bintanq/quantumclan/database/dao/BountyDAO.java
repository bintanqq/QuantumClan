package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.model.BountyEntry;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BountyDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public BountyDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert ────────────────────────────────────────────────

    public CompletableFuture<Boolean> insert(BountyEntry bounty) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO bounties
                  (id, clan_id_poster, clan_id_target, target_uuid,
                   amount, status, head_claimed, posted_at, expires_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bounty.getId());
                ps.setString(2, bounty.getClanIdPoster());
                ps.setString(3, bounty.getClanIdTarget());
                ps.setString(4, bounty.getTargetUuid().toString());
                ps.setLong(5,   bounty.getAmount());
                ps.setString(6, bounty.getStatus().name());
                ps.setInt(7,    bounty.isHeadClaimed() ? 1 : 0);
                ps.setString(8, bounty.getPostedAt().toString());
                ps.setString(9, bounty.getExpiresAt().toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] insert failed for " + bounty.getId(), e);
                return false;
            }
        });
    }

    // ── Update status ─────────────────────────────────────────

    public CompletableFuture<Boolean> updateStatus(String bountyId, BountyEntry.Status status) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE bounties SET status=? WHERE id=?")) {
                ps.setString(1, status.name());
                ps.setString(2, bountyId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] updateStatus failed for " + bountyId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> markHeadClaimed(String bountyId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE bounties SET head_claimed=1 WHERE id=?")) {
                ps.setString(1, bountyId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] markHeadClaimed failed for " + bountyId, e);
                return false;
            }
        });
    }

    // ── Select active bounty by target ────────────────────────

    public CompletableFuture<BountyEntry> findActiveByTarget(UUID targetUuid) {
        return db.supplyAsync(() -> {
            String sql = """
                SELECT * FROM bounties
                WHERE target_uuid=? AND status='ACTIVE'
                  AND expires_at > strftime('%Y-%m-%dT%H:%M:%SZ','now')
                LIMIT 1
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING,
                        "[BountyDAO] findActiveByTarget failed for " + targetUuid, e);
            }
            return null;
        });
    }

    // ── Select all active bounties (for board cache) ──────────

    public CompletableFuture<List<BountyEntry>> loadActiveBounties() {
        return db.supplyAsync(() -> {
            List<BountyEntry> bounties = new ArrayList<>();
            String sql = """
                SELECT * FROM bounties
                WHERE status='ACTIVE'
                  AND expires_at > strftime('%Y-%m-%dT%H:%M:%SZ','now')
                ORDER BY posted_at DESC
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bounties.add(mapRow(rs));
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] loadActiveBounties failed", e);
            }
            return bounties;
        });
    }

    // ── Find bounty by ID ─────────────────────────────────────

    public CompletableFuture<BountyEntry> findById(String id) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM bounties WHERE id=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] findById failed for " + id, e);
            }
            return null;
        });
    }

    // ── Expire stale bounties ─────────────────────────────────

    public CompletableFuture<List<String>> expireOldBounties() {
        return db.supplyAsync(() -> {
            List<String> expiredIds = new ArrayList<>();
            String selectSql = """
                SELECT id FROM bounties
                WHERE status='ACTIVE'
                  AND expires_at <= strftime('%Y-%m-%dT%H:%M:%SZ','now')
                """;
            String updateSql = "UPDATE bounties SET status='EXPIRED' WHERE id=?";
            try (Connection conn = db.getConnection()) {
                // Collect IDs first
                try (PreparedStatement ps = conn.prepareStatement(selectSql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) expiredIds.add(rs.getString("id"));
                }
                // Batch expire
                if (!expiredIds.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        for (String id : expiredIds) {
                            ps.setString(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] expireOldBounties failed", e);
            }
            return expiredIds;
        });
    }

    // ── Bounty head tracking (anti-dupe) ──────────────────────

    public CompletableFuture<Boolean> insertHead(String headId, String bountyId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO bounty_heads (head_id, bounty_id, submitted) VALUES (?,?,0)")) {
                ps.setString(1, headId);
                ps.setString(2, bountyId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] insertHead failed for " + headId, e);
                return false;
            }
        });
    }

    /**
     * Atomically marks a head as submitted.
     * Returns true ONLY if the head existed and was not already submitted.
     * This is the anti-dupe guard — only one submit per head.
     */
    public CompletableFuture<Boolean> submitHead(String headId) {
        return db.supplyAsync(() -> {
            String sql = """
                UPDATE bounty_heads SET submitted=1
                WHERE head_id=? AND submitted=0
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, headId);
                int rows = ps.executeUpdate();
                return rows > 0;  // 0 = already submitted or not found
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] submitHead failed for " + headId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> isHeadSubmitted(String headId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT submitted FROM bounty_heads WHERE head_id=?")) {
                ps.setString(1, headId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("submitted") == 1;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[BountyDAO] isHeadSubmitted failed for " + headId, e);
            }
            return false;
        });
    }

    // ── Row mapper ────────────────────────────────────────────

    private BountyEntry mapRow(ResultSet rs) throws SQLException {
        return new BountyEntry(
                rs.getString("id"),
                rs.getString("clan_id_poster"),
                rs.getString("clan_id_target"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getLong("amount"),
                BountyEntry.Status.valueOf(rs.getString("status")),
                rs.getInt("head_claimed") == 1,
                Instant.parse(rs.getString("posted_at")),
                Instant.parse(rs.getString("expires_at"))
        );
    }
}