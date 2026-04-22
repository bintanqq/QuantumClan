package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContributionDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public ContributionDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert log entry ──────────────────────────────────────

    public CompletableFuture<Boolean> logContribution(UUID uuid, String clanId,
                                                      int points, String reason) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO contribution_log (uuid, clan_id, points, reason)
                VALUES (?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, clanId);
                ps.setInt(3,    points);
                ps.setString(4, reason != null ? reason : "");
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING,
                        "[ContributionDAO] logContribution failed for " + uuid, e);
                return false;
            }
        });
    }

    // ── Load history for a player ─────────────────────────────

    public CompletableFuture<List<LogEntry>> loadHistory(UUID uuid, int limit) {
        return db.supplyAsync(() -> {
            List<LogEntry> entries = new ArrayList<>();
            String sql = """
                SELECT * FROM contribution_log
                WHERE uuid=?
                ORDER BY timestamp DESC
                LIMIT ?
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new LogEntry(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("clan_id"),
                                rs.getInt("points"),
                                rs.getString("reason"),
                                Instant.parse(rs.getString("timestamp"))
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING,
                        "[ContributionDAO] loadHistory failed for " + uuid, e);
            }
            return entries;
        });
    }

    // ── Total points for a player in a clan ───────────────────

    public CompletableFuture<Integer> totalPoints(UUID uuid, String clanId) {
        return db.supplyAsync(() -> {
            String sql = """
                SELECT SUM(points) FROM contribution_log
                WHERE uuid=? AND clan_id=?
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING,
                        "[ContributionDAO] totalPoints failed for " + uuid, e);
            }
            return 0;
        });
    }

    // ── Nested: LogEntry ──────────────────────────────────────

    public static class LogEntry {
        public final UUID    uuid;
        public final String  clanId;
        public final int     points;
        public final String  reason;
        public final Instant timestamp;

        public LogEntry(UUID uuid, String clanId, int points,
                        String reason, Instant timestamp) {
            this.uuid      = uuid;
            this.clanId    = clanId;
            this.points    = points;
            this.reason    = reason;
            this.timestamp = timestamp;
        }
    }
}