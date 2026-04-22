package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.entity.Player;

public class AdminReloadCommand {
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