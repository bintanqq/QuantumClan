package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminSetArenaCommand implements SubCommand {
    private final QuantumClan plugin;
    public AdminSetArenaCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        plugin.getWarConfigManager().saveArenaLocation(player.getLocation());
        plugin.sendMessage(player, "war.setarena-success");
    }
}
