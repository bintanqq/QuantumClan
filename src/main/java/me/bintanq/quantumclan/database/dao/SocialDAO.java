package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO for clan alliances and rivalries.
 *
 * Alliances are bidirectional, stored once with clan_id_a < clan_id_b lexicographically.
 * Rivalries are unidirectional: A rivals B does not mean B rivals A.
 */
public class SocialDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public SocialDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    // ── Alliances ─────────────────────────────────────────────

    /**
     * Inserts an alliance between two clans.
     * The caller must ensure clanA < clanB lexicographically.
     */
    public CompletableFuture<Boolean> insertAlliance(String clanA, String clanB) {
        return db.supplyAsync(() -> {
            String sql = "INSERT INTO clan_alliances (clan_id_a, clan_id_b) VALUES (?, ?)";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanA);
                ps.setString(2, clanB);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to insert alliance", e);
                return false;
            }
        });
    }

    /**
     * Deletes an alliance between two clans.
     * The caller must ensure clanA < clanB lexicographically.
     */
    public CompletableFuture<Boolean> deleteAlliance(String clanA, String clanB) {
        return db.supplyAsync(() -> {
            String sql = "DELETE FROM clan_alliances WHERE clan_id_a = ? AND clan_id_b = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanA);
                ps.setString(2, clanB);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to delete alliance", e);
                return false;
            }
        });
    }

    /**
     * Loads all alliances from the database.
     * Returns pairs of [clanA, clanB] where clanA < clanB.
     */
    public CompletableFuture<List<String[]>> loadAllAlliances() {
        return db.supplyAsync(() -> {
            List<String[]> result = new ArrayList<>();
            String sql = "SELECT clan_id_a, clan_id_b FROM clan_alliances";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{rs.getString("clan_id_a"), rs.getString("clan_id_b")});
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to load alliances", e);
            }
            return result;
        });
    }

    // ── Rivalries ─────────────────────────────────────────────

    /**
     * Inserts a unidirectional rivalry: declarerClan rivals targetClan.
     */
    public CompletableFuture<Boolean> insertRivalry(String declarerClan, String targetClan) {
        return db.supplyAsync(() -> {
            String sql = "INSERT INTO clan_rivalries (declarer_clan_id, target_clan_id) VALUES (?, ?)";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, declarerClan);
                ps.setString(2, targetClan);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to insert rivalry", e);
                return false;
            }
        });
    }

    /**
     * Deletes a unidirectional rivalry.
     */
    public CompletableFuture<Boolean> deleteRivalry(String declarerClan, String targetClan) {
        return db.supplyAsync(() -> {
            String sql = "DELETE FROM clan_rivalries WHERE declarer_clan_id = ? AND target_clan_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, declarerClan);
                ps.setString(2, targetClan);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to delete rivalry", e);
                return false;
            }
        });
    }

    /**
     * Loads all rivalries from the database.
     * Returns pairs of [declarerClanId, targetClanId].
     */
    public CompletableFuture<List<String[]>> loadAllRivalries() {
        return db.supplyAsync(() -> {
            List<String[]> result = new ArrayList<>();
            String sql = "SELECT declarer_clan_id, target_clan_id FROM clan_rivalries";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{rs.getString("declarer_clan_id"), rs.getString("target_clan_id")});
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to load rivalries", e);
            }
            return result;
        });
    }

    /**
     * Deletes all alliances and rivalries involving a given clan (used on clan disband).
     */
    public CompletableFuture<Void> deleteAllForClan(String clanId) {
        return db.runAsync(() -> {
            try (Connection conn = db.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_alliances WHERE clan_id_a = ? OR clan_id_b = ?")) {
                    ps.setString(1, clanId);
                    ps.setString(2, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM clan_rivalries WHERE declarer_clan_id = ? OR target_clan_id = ?")) {
                    ps.setString(1, clanId);
                    ps.setString(2, clanId);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[SocialDAO] Failed to delete social data for clan " + clanId, e);
            }
        });
    }
}
