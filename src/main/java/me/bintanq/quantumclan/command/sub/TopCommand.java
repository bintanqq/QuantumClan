package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanTopGUI;
import org.bukkit.entity.Player;

public class TopCommand {
    private final QuantumClan plugin;
    public TopCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.top")) { plugin.sendMessage(player, "error.no-permission"); return; }
        ClanTopGUI.open(plugin, player);
    }
}