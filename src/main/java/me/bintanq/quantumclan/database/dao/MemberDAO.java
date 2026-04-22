package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.model.ClanMember;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemberDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public MemberDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert ────────────────────────────────────────────────

    public CompletableFuture<Boolean> insert(ClanMember member) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO clan_members (uuid, clan_id, role, contribution_points, joined_at)
                VALUES (?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, member.getUuid().toString());
                ps.setString(2, member.getClanId());
                ps.setString(3, member.getRole());
                ps.setInt(4,    member.getContributionPoints());
                ps.setString(5, member.getJoinedAt().toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] insert failed for " + member.getUuid(), e);
                return false;
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────

    public CompletableFuture<Boolean> delete(UUID uuid) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clan_members WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] delete failed for " + uuid, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteByClan(String clanId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clan_members WHERE clan_id=?")) {
                ps.setString(1, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] deleteByClan failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Select by UUID ────────────────────────────────────────

    public CompletableFuture<ClanMember> findByUuid(UUID uuid) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clan_members WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] findByUuid failed for " + uuid, e);
            }
            return null;
        });
    }

    // ── Select by clan ────────────────────────────────────────

    public CompletableFuture<List<ClanMember>> findByClan(String clanId) {
        return db.supplyAsync(() -> {
            List<ClanMember> members = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clan_members WHERE clan_id=?")) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) members.add(mapRow(rs));
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] findByClan failed for " + clanId, e);
            }
            return members;
        });
    }

    // ── Load all members (startup cache) ──────────────────────

    public CompletableFuture<List<ClanMember>> loadAll() {
        return db.supplyAsync(() -> {
            List<ClanMember> members = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) members.add(mapRow(rs));
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] loadAll failed", e);
            }
            return members;
        });
    }

    // ── Update role ───────────────────────────────────────────

    public CompletableFuture<Boolean> updateRole(UUID uuid, String role) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clan_members SET role=? WHERE uuid=?")) {
                ps.setString(1, role);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] updateRole failed for " + uuid, e);
                return false;
            }
        });
    }

    // ── Update contribution points ────────────────────────────

    public CompletableFuture<Boolean> updateContribution(UUID uuid, int points) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clan_members SET contribution_points=? WHERE uuid=?")) {
                ps.setInt(1, points);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] updateContribution failed for " + uuid, e);
                return false;
            }
        });
    }

    // ── Pending buffs ─────────────────────────────────────────

    public CompletableFuture<Boolean> insertPendingBuff(String id, UUID uuid,
                                                        String effectType, int amplifier,
                                                        int durationSeconds, Instant expiresAt) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO clan_buffs_pending
                  (id, uuid, effect_type, amplifier, duration_seconds, expires_at)
                VALUES (?,?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, uuid.toString());
                ps.setString(3, effectType);
                ps.setInt(4,    amplifier);
                ps.setInt(5,    durationSeconds);
                ps.setString(6, expiresAt.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] insertPendingBuff failed for " + uuid, e);
                return false;
            }
        });
    }

    public CompletableFuture<List<PendingBuff>> loadPendingBuffs(UUID uuid) {
        return db.supplyAsync(() -> {
            List<PendingBuff> buffs = new ArrayList<>();
            String sql = """
                SELECT * FROM clan_buffs_pending
                WHERE uuid=? AND expires_at > strftime('%Y-%m-%dT%H:%M:%SZ','now')
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        buffs.add(new PendingBuff(
                                rs.getString("id"),
                                uuid,
                                rs.getString("effect_type"),
                                rs.getInt("amplifier"),
                                rs.getInt("duration_seconds"),
                                Instant.parse(rs.getString("expires_at"))
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] loadPendingBuffs failed for " + uuid, e);
            }
            return buffs;
        });
    }

    public CompletableFuture<Boolean> deletePendingBuff(String buffId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clan_buffs_pending WHERE id=?")) {
                ps.setString(1, buffId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] deletePendingBuff failed for " + buffId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteExpiredBuffs() {
        return db.supplyAsync(() -> {
            String sql = "DELETE FROM clan_buffs_pending " +
                    "WHERE expires_at <= strftime('%Y-%m-%dT%H:%M:%SZ','now')";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[MemberDAO] deleteExpiredBuffs failed", e);
                return false;
            }
        });
    }

    // ── Row mapper ────────────────────────────────────────────

    private ClanMember mapRow(ResultSet rs) throws SQLException {
        return new ClanMember(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("clan_id"),
                rs.getString("role"),
                rs.getInt("contribution_points"),
                Instant.parse(rs.getString("joined_at"))
        );
    }

    // ── Nested: PendingBuff ───────────────────────────────────

    public static class PendingBuff {
        public final String  id;
        public final UUID    uuid;
        public final String  effectType;
        public final int     amplifier;
        public final int     durationSeconds;
        public final Instant expiresAt;

        public PendingBuff(String id, UUID uuid, String effectType,
                           int amplifier, int durationSeconds, Instant expiresAt) {
            this.id              = id;
            this.uuid            = uuid;
            this.effectType      = effectType;
            this.amplifier       = amplifier;
            this.durationSeconds = durationSeconds;
            this.expiresAt       = expiresAt;
        }

        /** Remaining duration in ticks at the time this is called. */
        public int remainingTicks() {
            long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            if (remaining <= 0) return 0;
            return (int) (remaining * 20);
        }

        public boolean isStillValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}