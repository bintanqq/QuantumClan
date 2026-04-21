package me.bintanq.quantumclan.schematic.impl;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.schematic.SchematicProvider;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * SchematicProvider implementation using Paper's built-in StructureManager API.
 * Saves/loads vanilla .nbt structure files — zero external dependency.
 *
 * Paste is dispatched async for file I/O, then placed on main thread
 * (StructureManager#place requires main thread).
 */
public class NBTStructureProvider implements SchematicProvider {

    private final QuantumClan plugin;

    public NBTStructureProvider(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "NBTStructure (Paper built-in)";
    }

    @Override
    public boolean isAvailable() {
        // StructureManager is always available in Paper 1.21.1
        try {
            Class.forName("org.bukkit.structure.StructureManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getFileExtension() {
        return "nbt";
    }

    @Override
    public CompletableFuture<Boolean> paste(File file, Location location, boolean ignoreAir) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!file.exists()) {
            plugin.getLogger().warning("[NBTStructureProvider] File not found: " + file.getPath());
            future.complete(false);
            return future;
        }

        World world = location.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[NBTStructureProvider] World is null for paste location.");
            future.complete(false);
            return future;
        }

        // File I/O on async thread, then place on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StructureManager structureManager = plugin.getServer().getStructureManager();
                Structure structure;

                try (FileInputStream fis = new FileInputStream(file)) {
                    structure = structureManager.loadStructure(fis);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "[NBTStructureProvider] Failed to load structure file: " + file.getName(), e);
                    future.complete(false);
                    return;
                }

                final Structure finalStructure = structure;

                // Place must happen on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        finalStructure.place(location, ignoreAir, new Random());
                        future.complete(true);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "[NBTStructureProvider] Failed to place structure at "
                                        + locationToString(location), e);
                        future.complete(false);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[NBTStructureProvider] Unexpected error during paste.", e);
                future.complete(false);
            }
        });

        return future;
    }

    @Override
    public CompletableFuture<Boolean> save(File file, Location corner1, Location corner2) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        World world = corner1.getWorld();
        if (world == null || !world.equals(corner2.getWorld())) {
            plugin.getLogger().warning("[NBTStructureProvider] Both corners must be in the same world.");
            future.complete(false);
            return future;
        }

        // Build structure on main thread, write file async
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                StructureManager structureManager = plugin.getServer().getStructureManager();

                // Calculate origin (minimum corner) and size
                int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
                int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
                int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
                int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
                int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
                int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

                Location origin = new Location(world, minX, minY, minZ);
                BlockVector size = new BlockVector(
                        maxX - minX + 1,
                        maxY - minY + 1,
                        maxZ - minZ + 1
                );

                // Use a temporary namespaced key for the in-memory structure
                NamespacedKey key = new NamespacedKey(plugin, "clan_hall_temp_"
                        + System.currentTimeMillis());
                Structure structure = structureManager.createStructure();
                structure.fill(origin, size, false);

                // Ensure parent directory exists
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }

                final Structure finalStructure = structure;

                // Write file on async thread
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        structureManager.saveStructure(finalStructure, fos);
                        future.complete(true);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING,
                                "[NBTStructureProvider] Failed to save structure to: "
                                        + file.getPath(), e);
                        future.complete(false);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[NBTStructureProvider] Unexpected error during save.", e);
                future.complete(false);
            }
        });

        return future;
    }

    // ── Helpers ───────────────────────────────────────────────

    private String locationToString(Location loc) {
        return String.format("[world=%s, x=%.1f, y=%.1f, z=%.1f]",
                loc.getWorld() != null ? loc.getWorld().getName() : "null",
                loc.getX(), loc.getY(), loc.getZ());
    }
}