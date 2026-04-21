package me.bintanq.quantumclan.schematic;

import org.bukkit.Location;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction layer for clan hall schematic placement.
 * Two implementations:
 *   - NBTStructureProvider  : uses Paper's built-in StructureManager (zero dependency)
 *   - FAWEStructureProvider : uses FastAsyncWorldEdit if available
 */
public interface SchematicProvider {

    /**
     * Returns a human-readable name for this provider (used in logs).
     */
    String getName();

    /**
     * Returns true if this provider is available in the current environment.
     * Called during engine selection in AUTO mode.
     */
    boolean isAvailable();

    /**
     * Paste a schematic/structure at the given location asynchronously.
     *
     * @param file     The schematic file to paste (.nbt for NBT, .schem for FAWE)
     * @param location The target paste location (origin corner)
     * @param ignoreAir Whether air blocks in the schematic should be skipped
     * @return CompletableFuture that completes with true on success, false on failure
     */
    CompletableFuture<Boolean> paste(File file, Location location, boolean ignoreAir);

    /**
     * Save the region between two corners into a schematic file asynchronously.
     *
     * @param file    Target file to write
     * @param corner1 First corner of the region
     * @param corner2 Second corner of the region
     * @return CompletableFuture that completes with true on success, false on failure
     */
    CompletableFuture<Boolean> save(File file, Location corner1, Location corner2);

    /**
     * Returns the expected file extension for schematics used by this provider.
     * e.g. "nbt" for NBT, "schem" for FAWE
     */
    String getFileExtension();
}