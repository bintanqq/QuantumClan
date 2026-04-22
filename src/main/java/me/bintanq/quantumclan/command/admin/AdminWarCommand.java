package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.entity.Player;

public class AdminWarCommand implements SubCommand {
    private final QuantumClan plugin;

    public AdminWarCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (plugin.getWarManager().getActiveSession() != null) {
                    plugin.sendMessage(player, "admin.war-already-running");
                    return;
                }
                plugin.getWarManager().createSession();
                plugin.getWarManager().startWar();
                plugin.sendMessage(player, "admin.war-started");
            }
            case "end" -> {
                WarSession war = plugin.getWarManager().getActiveSession();
                if (war == null || !war.isActive()) {
                    plugin.sendMessage(player, "admin.war-no-active");
                    return;
                }
                plugin.getWarManager().endWar(null);
                plugin.sendMessage(player, "admin.war-ended");
            }
            default -> plugin.sendMessage(player, "error.unknown-subcommand");
        }
    }
}
