package me.bintanq.quantumclan.hook;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;

/**
 * Soft hook for the Citizens NPC plugin.
 * Uses reflection so Citizens is never a hard compile dependency.
 */
public class CitizensHook {

    private final QuantumClan plugin;
    private boolean available = false;

    public CitizensHook(QuantumClan plugin) {
        this.plugin = plugin;
        detect();
    }

    private void detect() {
        var p = Bukkit.getPluginManager().getPlugin("Citizens");
        if (p == null || !p.isEnabled()) return;
        try {
            Class.forName("net.citizensnpcs.api.CitizensAPI");
            available = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public boolean isAvailable() { return available; }
}