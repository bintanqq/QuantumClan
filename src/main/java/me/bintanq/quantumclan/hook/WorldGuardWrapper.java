package me.bintanq.quantumclan.hook;

import org.bukkit.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

public class WorldGuardWrapper {

    public static void createRegion(String regionName, Location min, Location max) {
        WorldGuard wg = WorldGuard.getInstance();
        RegionManager rm = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(min.getWorld()));

        if (rm != null) {
            BlockVector3 bv1 = BlockVector3.at(min.getX(), min.getY(), min.getZ());
            BlockVector3 bv2 = BlockVector3.at(max.getX(), max.getY(), max.getZ());
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, bv1, bv2);
            rm.addRegion(region);
            try {
                rm.saveChanges();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}