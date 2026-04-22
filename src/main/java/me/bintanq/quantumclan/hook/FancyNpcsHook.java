package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;

/** Soft hook for FancyNpcs. */
public class FancyNpcsHook {

    private final boolean available;

    public FancyNpcsHook(QuantumClan plugin) {
        var p = Bukkit.getPluginManager().getPlugin("FancyNpcs");
        boolean found = false;
        if (p != null && p.isEnabled()) {
            try { Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin"); found = true; }
            catch (ClassNotFoundException ignored) {}
        }
        this.available = found;
    }

    public boolean isAvailable() { return available; }
}