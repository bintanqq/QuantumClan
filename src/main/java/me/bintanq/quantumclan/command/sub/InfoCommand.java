package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanInfoGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class InfoCommand implements SubCommand {

    private final QuantumClan plugin;

    public InfoCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!plugin.checkPerm(player, "quantumclan.clan.info")) return;

        Clan target;
        if (args.length > 0) {
            // Join all args to support clan names with spaces
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
            target = plugin.getPlayerClan(player);
            if (target == null) return;
        }

        ClanInfoGUI.open(plugin, player, target);
    }
}
