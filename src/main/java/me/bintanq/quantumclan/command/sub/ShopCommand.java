package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanShopGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements SubCommand {
    private final QuantumClan plugin;

    public ShopCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.shop.use")) return;

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;

        if (!plugin.checkRole(player, "can-access-shop")) return;
        ClanShopGUI.open(plugin, player, clan);
    }
}
