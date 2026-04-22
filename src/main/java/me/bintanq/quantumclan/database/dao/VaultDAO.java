package me.bintanq.quantumclan.database.dao;

import me.bintanq.quantumclan.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO for the clan_vault table.
 * Uses SqlDialect for upsert compatibility between SQLite and MySQL.
 */
public class VaultDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public VaultDAO(DatabaseManager db, Logger logger) {
        this.db     = db;
        this.logger = logger;
    }

    // ── Load ──────────────────────────────────────────────────

    public CompletableFuture<ItemStack[]> loadVault(String clanId, int slotCount) {
        return db.supplyAsync(() -> {
            String sql = "SELECT contents FROM clan_vault WHERE clan_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String encoded = rs.getString("contents");
                        if (encoded == null || encoded.isBlank()) {
                            return new ItemStack[slotCount];
                        }
                        ItemStack[] items = deserialize(encoded);
                        if (items == null) return new ItemStack[slotCount];

                        if (items.length < slotCount) {
                            ItemStack[] expanded = new ItemStack[slotCount];
                            System.arraycopy(items, 0, expanded, 0, items.length);
                            return expanded;
                        }
                        return items;
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[VaultDAO] loadVault failed for " + clanId, e);
            }
            return new ItemStack[slotCount];
        });
    }

    // ── Save ──────────────────────────────────────────────────

    public CompletableFuture<Boolean> saveVault(String clanId, ItemStack[] contents) {
        return db.supplyAsync(() -> {
            String encoded = serialize(contents);
            if (encoded == null) {
                logger.warning("[VaultDAO] saveVault: serialization failed for clan " + clanId);
                return false;
            }

            // Use dialect-aware upsert
            String sql = db.getDialect().upsertVault();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                ps.setString(2, encoded);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[VaultDAO] saveVault failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Clear ─────────────────────────────────────────────────

    public CompletableFuture<Boolean> clearVault(String clanId) {
        return db.supplyAsync(() -> {
            String sql = db.getDialect().upsertVaultClear();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, clanId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[VaultDAO] clearVault failed for " + clanId, e);
                return false;
            }
        });
    }

    // ── Serialization helpers ─────────────────────────────────

    public static String serialize(ItemStack[] items) {
        if (items == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
                boos.writeInt(items.length);
                for (ItemStack item : items) {
                    boos.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[VaultDAO] Serialization error: " + e.getMessage(), e);
            return null;
        }
    }

    public static ItemStack[] deserialize(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
                int length = bois.readInt();
                ItemStack[] items = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    items[i] = (ItemStack) bois.readObject();
                }
                return items;
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[VaultDAO] Deserialization error: " + e.getMessage(), e);
            return null;
        }
    }
}