package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import org.bukkit.entity.Player;

public class AdminSetArenaCommand implements SubCommand {
    private final QuantumClan plugin;
    public AdminSetArenaCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        plugin.getWarConfigManager().saveArenaLocation(player.getLocation());
        plugin.sendMessage(player, "war.setarena-success");
    }
}
