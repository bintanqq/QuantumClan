package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.WarGUI;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.entity.Player;

public class WarCommand {
    private final QuantumClan plugin;
    public WarCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (args.length == 0) { WarGUI.open(plugin, player); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        switch (args[0].toLowerCase()) {
            case "register" -> {
                if (!player.hasPermission("quantumclan.war.register")) { plugin.sendMessage(player, "error.no-permission"); return; }
                plugin.getWarManager().registerClan(player, clan);
            }
            case "leave" -> {
                if (!player.hasPermission("quantumclan.war.leave")) { plugin.sendMessage(player, "error.no-permission"); return; }
                WarSession war = plugin.getWarManager().getActiveSession();
                if (war == null) { plugin.sendMessage(player, "war.leave-not-registered"); return; }
                plugin.getWarManager().unregisterClan(player, clan);
            }
            default -> WarGUI.open(plugin, player);
        }
    }
}