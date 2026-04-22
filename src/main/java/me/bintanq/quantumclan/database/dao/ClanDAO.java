package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import me.bintanq.quantumclan.model.Clan;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClanDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public ClanDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Insert ────────────────────────────────────────────────

    public CompletableFuture<Boolean> insert(Clan clan) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO clans
                  (id, name, tag, tag_color, level, money, reputation,
                   leader_uuid, shield_until, hall_world, hall_x, hall_y, hall_z, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1,  clan.getId());
                ps.setString(2,  clan.getName());
                ps.setString(3,  clan.getTag());
                ps.setString(4,  clan.getTagColor());
                ps.setInt(5,     clan.getLevel());
                ps.setLong(6,    clan.getMoney());
                ps.setInt(7,     clan.getReputation());
                ps.setString(8,  clan.getLeaderUuid().toString());
                ps.setString(9,  clan.getShieldUntil() != null
                        ? clan.getShieldUntil().toString() : null);
                ps.setString(10, clan.getHallWorld());
                ps.setDouble(11, clan.getHallX());
                ps.setDouble(12, clan.getHallY());
                ps.setDouble(13, clan.getHallZ());
                ps.setString(14, clan.getCreatedAt().toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] insert failed for " + clan.getId(), e);
                return false;
            }
        });
    }

    // ── Update ────────────────────────────────────────────────

    public CompletableFuture<Boolean> update(Clan clan) {
        return db.supplyAsync(() -> {
            String sql = """
                UPDATE clans SET
                  name=?, tag=?, tag_color=?, level=?, money=?, reputation=?,
                  leader_uuid=?, shield_until=?, hall_world=?, hall_x=?, hall_y=?, hall_z=?
                WHERE id=?
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1,  clan.getName());
                ps.setString(2,  clan.getTag());
                ps.setString(3,  clan.getTagColor());
                ps.setInt(4,     clan.getLevel());
                ps.setLong(5,    clan.getMoney());
                ps.setInt(6,     clan.getReputation());
                ps.setString(7,  clan.getLeaderUuid().toString());
                ps.setString(8,  clan.getShieldUntil() != null
                        ? clan.getShieldUntil().toString() : null);
                ps.setString(9,  clan.getHallWorld());
                ps.setDouble(10, clan.getHallX());
                ps.setDouble(11, clan.getHallY());
                ps.setDouble(12, clan.getHallZ());
                ps.setString(13, clan.getId());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] update failed for " + clan.getId(), e);
                return false;
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────

    public CompletableFuture<Boolean> delete(String clanId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clans WHERE id=?")) {
                ps.setString(1, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] delete failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Select by ID ──────────────────────────────────────────

    public CompletableFuture<Clan> findById(String id) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clans WHERE id=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] findById failed for " + id, e);
            }
            return null;
        });
    }

    // ── Select by name ────────────────────────────────────────

    public CompletableFuture<Clan> findByName(String name) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clans WHERE LOWER(name)=LOWER(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] findByName failed for " + name, e);
            }
            return null;
        });
    }

    // ── Select by tag ─────────────────────────────────────────

    public CompletableFuture<Clan> findByTag(String tag) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clans WHERE LOWER(tag)=LOWER(?)")) {
                ps.setString(1, tag);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] findByTag failed for " + tag, e);
            }
            return null;
        });
    }

    // ── Load all clans (startup cache) ────────────────────────

    public CompletableFuture<List<Clan>> loadAll() {
        return db.supplyAsync(() -> {
            List<Clan> clans = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM clans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) clans.add(mapRow(rs));
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] loadAll failed", e);
            }
            return clans;
        });
    }

    // ── Leaderboard ───────────────────────────────────────────

    public CompletableFuture<List<Clan>> loadTopByReputation(int limit) {
        return db.supplyAsync(() -> {
            List<Clan> clans = new ArrayList<>();
            String sql = "SELECT * FROM clans ORDER BY reputation DESC LIMIT ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) clans.add(mapRow(rs));
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] loadTopByReputation failed", e);
            }
            return clans;
        });
    }

    // ── Homes ─────────────────────────────────────────────────

    public CompletableFuture<List<Clan.ClanHome>> loadHomes(String clanId) {
        return db.supplyAsync(() -> {
            List<Clan.ClanHome> homes = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM clan_homes WHERE clan_id=?")) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        homes.add(new Clan.ClanHome(
                                rs.getString("id"),
                                rs.getString("clan_id"),
                                rs.getString("name"),
                                rs.getString("world"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] loadHomes failed for clan " + clanId, e);
            }
            return homes;
        });
    }

    public CompletableFuture<Boolean> insertHome(Clan.ClanHome home) {
        return db.supplyAsync(() -> {
            String sql = """
                INSERT INTO clan_homes (id, clan_id, name, world, x, y, z, yaw, pitch)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, home.getId());
                ps.setString(2, home.getClanId());
                ps.setString(3, home.getName());
                ps.setString(4, home.getWorld());
                ps.setDouble(5, home.getX());
                ps.setDouble(6, home.getY());
                ps.setDouble(7, home.getZ());
                ps.setFloat(8,  home.getYaw());
                ps.setFloat(9,  home.getPitch());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] insertHome failed", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteHome(String homeId) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clan_homes WHERE id=?")) {
                ps.setString(1, homeId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] deleteHome failed for " + homeId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deleteHomeByName(String clanId, String name) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM clan_homes WHERE clan_id=? AND LOWER(name)=LOWER(?)")) {
                ps.setString(1, clanId);
                ps.setString(2, name);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] deleteHomeByName failed", e);
                return false;
            }
        });
    }

    // ── Partial updates ───────────────────────────────────────

    public CompletableFuture<Boolean> updateMoney(String clanId, long money) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clans SET money=? WHERE id=?")) {
                ps.setLong(1, money);
                ps.setString(2, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] updateMoney failed", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateReputation(String clanId, int reputation) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clans SET reputation=? WHERE id=?")) {
                ps.setInt(1, reputation);
                ps.setString(2, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] updateReputation failed", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateShield(String clanId, Instant shieldUntil) {
        return db.supplyAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE clans SET shield_until=? WHERE id=?")) {
                ps.setString(1, shieldUntil != null ? shieldUntil.toString() : null);
                ps.setString(2, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[ClanDAO] updateShield failed", e);
                return false;
            }
        });
    }

    // ── Row mapper ────────────────────────────────────────────

    private Clan mapRow(ResultSet rs) throws SQLException {
        String shieldStr = rs.getString("shield_until");
        Instant shield   = (shieldStr != null) ? Instant.parse(shieldStr) : null;

        return new Clan(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("tag"),
                rs.getString("tag_color"),
                rs.getInt("level"),
                rs.getLong("money"),
                rs.getInt("reputation"),
                UUID.fromString(rs.getString("leader_uuid")),
                shield,
                rs.getString("hall_world"),
                rs.getDouble("hall_x"),
                rs.getDouble("hall_y"),
                rs.getDouble("hall_z"),
                Instant.parse(rs.getString("created_at"))
        );
    }
}