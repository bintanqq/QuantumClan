package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import org.bukkit.command.CommandSender;

public class AdminReloadCommand implements SubCommand {
    private final QuantumClan plugin;
    public AdminReloadCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();
        plugin.getMessagesManager().reload();
        plugin.getShopConfigManager().reload();
        plugin.getWarConfigManager().reload();
        plugin.getRolesConfigManager().reload();
        plugin.getGuiConfigManager().reload();
        plugin.getHallConfigManager().reload();
        plugin.sendMessage(sender, "admin.reload-success");
    }
}
