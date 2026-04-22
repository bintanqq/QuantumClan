package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanTopGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TopCommand implements SubCommand {
    private final QuantumClan plugin;
    public TopCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!player.hasPermission("quantumclan.top")) { plugin.sendMessage(player, "error.no-permission"); return; }
        ClanTopGUI.open(plugin, player);
    }
}
