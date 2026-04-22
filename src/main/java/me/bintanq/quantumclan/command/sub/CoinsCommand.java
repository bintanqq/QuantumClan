package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.CoinsShopGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinsCommand implements SubCommand {

    private final QuantumClan plugin;

    public CoinsCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!player.hasPermission("quantumclan.coins.shop")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }
        CoinsShopGUI.open(plugin, player);
    }
}
