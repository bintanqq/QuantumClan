package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.DisbandConfirmGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class DisbandCommand {

    private final QuantumClan plugin;

    public DisbandCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.disband")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        if (!clan.getLeaderUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "gui.disband-leader-only");
            return;
        }

        // Open confirmation GUI instead of immediate disband
        DisbandConfirmGUI.open(plugin, player, clan);
    }
}