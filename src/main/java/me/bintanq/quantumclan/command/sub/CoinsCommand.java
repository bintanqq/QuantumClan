package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.CoinsShopGUI;
import org.bukkit.entity.Player;

public class CoinsCommand {

    private final QuantumClan plugin;

    public CoinsCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.coins.shop")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }
        CoinsShopGUI.open(plugin, player);
    }
}