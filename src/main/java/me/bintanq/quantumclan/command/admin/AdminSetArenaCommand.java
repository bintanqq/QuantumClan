package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.entity.Player;

public class AdminSetArenaCommand {
    private final QuantumClan plugin;
    public AdminSetArenaCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        plugin.getWarConfigManager().saveArenaLocation(player.getLocation());
        plugin.sendMessage(player, "war.setarena-success");
    }
}