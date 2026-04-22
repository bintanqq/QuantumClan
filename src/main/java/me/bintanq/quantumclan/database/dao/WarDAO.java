package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.model.WarSession;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WarDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public WarDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert session ────────────────────────────────────────

    public CompletableFuture<Boolean> insertSession(WarSession session) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO war_sessions (id, format, started_at, ended_at, winner_clan_id)
                VALUES (?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, session.getId());
                ps.setString(2, session.getFormat().name());
                ps.setString(3, session.getStartedAt().toString());
                ps.setString(4, session.getEndedAt() != null
                        ? session.getEndedAt().toString() : null);
                ps.setString(5, session.getWinnerClanId());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[WarDAO] insertSession failed for " + session.getId(), e);
                return false;
            }
        });
    }

    // ── Update session end ────────────────────────────────────

    public CompletableFuture<Boolean> updateSessionEnd(String sessionId,
                                                       Instant endedAt,
                                                       String winnerClanId) {
        return db.supplyAsync(() -> {
            String sql = "UPDATE war_sessions SET ended_at=?, winner_clan_id=? WHERE id=?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, endedAt.toString());
                ps.setString(2, winnerClanId);
                ps.setString(3, sessionId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING,
                        "[WarDAO] updateSessionEnd failed for " + sessionId, e);
                return false;
            }
        });
    }

    // ── Batch insert participants ─────────────────────────────

    /**
     * Inserts all participants for a war session in a single transaction.
     * Used when war starts to seed the participants table.
     */
    public CompletableFuture<Boolean> insertParticipantsBatch(String sessionId,
                                                              Map<String, Set<UUID>> clanMembers) {
        return db.executeBatch(conn -> {
            String sql = """
                INSERT INTO war_participants
                  (session_id, clan_id, member_uuid, kills, eliminated)
                VALUES (?,?,?,0,0)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Set<UUID>> entry : clanMembers.entrySet()) {
                    for (UUID uuid : entry.getValue()) {
                        ps.setString(1, sessionId);
                        ps.setString(2, entry.getKey());
                        ps.setString(3, uuid.toString());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
        });
    }

    // ── Batch update kill counts ──────────────────────────────

    /**
     * Bulk-updates kill counts and elimination status for all participants.
     * Called once when war ends to persist final state.
     */
    public CompletableFuture<Boolean> updateParticipantsBatch(String sessionId,
                                                              Map<UUID, Integer> kills,
                                                              Set<UUID> eliminated) {
        return db.executeBatch(conn -> {
            String sql = """
                UPDATE war_participants
                SET kills=?, eliminated=?
                WHERE session_id=? AND member_uuid=?
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
                    UUID uuid = entry.getKey();
                    ps.setInt(1,    entry.getValue());
                    ps.setInt(2,    eliminated.contains(uuid) ? 1 : 0);
                    ps.setString(3, sessionId);
                    ps.setString(4, uuid.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    // ── Query ─────────────────────────────────────────────────

    /**
     * Returns total war wins for a given clan (for reputation calculations).
     */
    public CompletableFuture<Integer> countWins(String clanId) {
        return db.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM war_sessions WHERE winner_clan_id=?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[WarDAO] countWins failed for " + clanId, e);
            }
            return 0;
        });
    }
}