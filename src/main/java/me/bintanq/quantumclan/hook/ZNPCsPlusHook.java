package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;

/** Soft hook for ZNPCSPlus. */
public class ZNPCsPlusHook {

    private final boolean available;

    public ZNPCsPlusHook(QuantumClan plugin) {
        var p = Bukkit.getPluginManager().getPlugin("ZNPCsPlus");
        boolean found = false;
        if (p != null && p.isEnabled()) {
            try { Class.forName("lol.pyr.znpcsplus.api.NpcApi"); found = true; }
            catch (ClassNotFoundException ignored) {}
        }
        this.available = found;
    }

    public boolean isAvailable() { return available; }
}