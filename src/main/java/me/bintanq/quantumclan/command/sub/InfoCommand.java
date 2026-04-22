package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanInfoGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class InfoCommand {

    private final QuantumClan plugin;

    public InfoCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.info")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        Clan target;
        if (args.length > 0) {
            // BUG FIX: join all args to support clan names with spaces
            String query = String.join(" ", args);
            target = plugin.getClanManager().getClanByName(query);
            // Also try by tag (tags cannot have spaces, but try args[0] as tag)
            if (target == null) {
                target = plugin.getClanManager().getClanByTag(args[0]);
            }
            if (target == null) {
                plugin.sendMessage(player, "clan.not-found", "{clan}", query);
                return;
            }
        } else {
            target = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (target == null) {
                plugin.sendMessage(player, "clan.not-in-clan");
                return;
            }
        }

        ClanInfoGUI.open(plugin, player, target);
    }
}