package me.bintanq.quantumclan.schematic.impl;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.schematic.SchematicProvider;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * SchematicProvider implementation using FastAsyncWorldEdit (FAWE).
 * Handles .schem (Sponge schematic v2/v3) format.
 * All heavy I/O runs on FAWE's own async thread pool.
 */
public class FAWEStructureProvider implements SchematicProvider {

    private final QuantumClan plugin;

    public FAWEStructureProvider(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "FAWEStructure (FastAsyncWorldEdit)";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.fastasyncworldedit.core.FaweAPI");
            return org.bukkit.Bukkit.getPluginManager()
                    .getPlugin("FastAsyncWorldEdit") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getFileExtension() {
        return "schem";
    }

    @Override
    public CompletableFuture<Boolean> paste(File file, Location location, boolean ignoreAir) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!file.exists()) {
            plugin.getLogger().warning("[FAWEStructureProvider] File not found: " + file.getPath());
            future.complete(false);
            return future;
        }

        World world = location.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[FAWEStructureProvider] World is null for paste location.");
            future.complete(false);
            return future;
        }

        // FAWE handles its own async — run on a FAWE-safe thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                plugin.getLogger().warning(
                        "[FAWEStructureProvider] Unknown clipboard format for: " + file.getName());
                future.complete(false);
                return;
            }

            try (FileInputStream fis = new FileInputStream(file);
                 ClipboardReader reader = format.getReader(fis)) {

                Clipboard clipboard = reader.read();

                com.sk89q.worldedit.world.World weWorld =
                        BukkitAdapter.adapt(world);

                BlockVector3 origin = BlockVector3.at(
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()
                );

                try (EditSession editSession =
                             WorldEdit.getInstance().newEditSessionBuilder()
                                     .world(weWorld)
                                     .build()) {

                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    Operation operation = holder
                            .createPaste(editSession)
                            .to(origin)
                            .ignoreAirBlocks(ignoreAir)
                            .build();

                    Operations.complete(operation);
                    editSession.flushSession();
                }

                future.complete(true);

            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[FAWEStructureProvider] IOException during paste of: "
                                + file.getName(), e);
                future.complete(false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[FAWEStructureProvider] Unexpected error during paste.", e);
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
            plugin.getLogger().warning(
                    "[FAWEStructureProvider] Both corners must be in the same world.");
            future.complete(false);
            return future;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

                BlockVector3 min = BlockVector3.at(
                        Math.min(corner1.getBlockX(), corner2.getBlockX()),
                        Math.min(corner1.getBlockY(), corner2.getBlockY()),
                        Math.min(corner1.getBlockZ(), corner2.getBlockZ())
                );
                BlockVector3 max = BlockVector3.at(
                        Math.max(corner1.getBlockX(), corner2.getBlockX()),
                        Math.max(corner1.getBlockY(), corner2.getBlockY()),
                        Math.max(corner1.getBlockZ(), corner2.getBlockZ())
                );

                CuboidRegion region = new CuboidRegion(weWorld, min, max);
                BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(min);

                try (EditSession editSession =
                             WorldEdit.getInstance().newEditSessionBuilder()
                                     .world(weWorld)
                                     .build()) {

                    ForwardExtentCopy copy = new ForwardExtentCopy(
                            editSession, region, clipboard, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    copy.setCopyingBiomes(false);
                    Operations.complete(copy);
                }

                // Ensure parent directory exists
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(file);
                     ClipboardWriter writer =
                             BuiltInClipboardFormat.FAST.getWriter(fos)) {
                    writer.write(clipboard);
                }

                future.complete(true);

            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[FAWEStructureProvider] IOException during save to: "
                                + file.getPath(), e);
                future.complete(false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[FAWEStructureProvider] Unexpected error during save.", e);
                future.complete(false);
            }
        });

        return future;
    }
}