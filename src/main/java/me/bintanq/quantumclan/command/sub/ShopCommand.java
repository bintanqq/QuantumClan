package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanShopGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class ShopCommand {
    private final QuantumClan plugin;

    public ShopCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.shop.use")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-access-shop")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }
        ClanShopGUI.open(plugin, player, clan);
    }
}