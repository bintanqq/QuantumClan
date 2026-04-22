package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import org.bukkit.entity.Player;

public class AdminReloadCommand implements SubCommand {
    private final QuantumClan plugin;
    public AdminReloadCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        plugin.reload();
        plugin.getMessagesManager().reload();
        plugin.getShopConfigManager().reload();
        plugin.getWarConfigManager().reload();
        plugin.getRolesConfigManager().reload();
        plugin.sendMessage(player, "admin.reload-success");
    }
}
