package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.UpgradeGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class UpgradeCommand implements SubCommand {
    private final QuantumClan plugin;
    public UpgradeCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!plugin.checkPerm(player, "quantumclan.clan.upgrade")) return;
        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
        if (!plugin.checkRole(player, "can-upgrade")) return;
        UpgradeGUI.open(plugin, player, clan);
    }
}
