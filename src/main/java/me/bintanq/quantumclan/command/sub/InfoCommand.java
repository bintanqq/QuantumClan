package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanInfoGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class InfoCommand {
    private final QuantumClan plugin;
    public InfoCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.info")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan target;
        if (args.length > 0) {
            target = plugin.getClanManager().getClanByName(args[0]);
            if (target == null) target = plugin.getClanManager().getClanByTag(args[0]);
            if (target == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", args[0]); return; }
        } else {
            target = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (target == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        }
        ClanInfoGUI.open(plugin, player, target);
    }
}
