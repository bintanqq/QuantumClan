package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.command.CommandSender;

public class AdminWarCommand implements SubCommand {
    private final QuantumClan plugin;

    public AdminWarCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(sender, "error.unknown-subcommand");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (plugin.getWarManager().getActiveSession() != null) {
                    plugin.sendMessage(sender, "admin.war-already-running");
                    return;
                }
                plugin.getWarManager().createSession();
                plugin.getWarManager().startWar();
                plugin.sendMessage(sender, "admin.war-started");
            }
            case "end" -> {
                WarSession war = plugin.getWarManager().getActiveSession();
                if (war == null || !war.isActive()) {
                    plugin.sendMessage(sender, "admin.war-no-active");
                    return;
                }
                plugin.getWarManager().endWar(null);
                plugin.sendMessage(sender, "admin.war-ended");
            }
            default -> plugin.sendMessage(sender, "error.unknown-subcommand");
        }
    }
}
