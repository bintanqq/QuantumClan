package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.DisbandConfirmGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DisbandCommand implements SubCommand {

    private final QuantumClan plugin;

    public DisbandCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.clan.disband")) return;

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;

        if (!clan.getLeaderUuid().equals(player.getUniqueId())) {
            plugin.sendMessage(player, "gui.disband-leader-only");
            return;
        }

        // Open confirmation GUI instead of immediate disband
        DisbandConfirmGUI.open(plugin, player, clan);
    }
}
