package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ContributionShopGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ContributionCommand implements SubCommand {
    private final QuantumClan plugin;
    public ContributionCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!player.hasPermission("quantumclan.contribution.shop")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (plugin.getClanManager().getClanByPlayer(player.getUniqueId()) == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        ContributionShopGUI.open(plugin, player);
    }
}
