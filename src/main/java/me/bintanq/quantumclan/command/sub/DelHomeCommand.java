package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DelHomeCommand implements SubCommand {
    private final QuantumClan plugin;
    public DelHomeCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.home.delete")) return;
        if (args.length == 0) { plugin.sendMessage(player, "home.delhome-usage"); return; }

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
        if (!plugin.checkRole(player, "can-delete-home")) return;
        plugin.getClanManager().deleteHome(clan.getId(), args[0]).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "home.delete-success", "{value}", args[0]);
                    else plugin.sendMessage(player, "home.delete-not-found", "{value}", args[0]);
                }));
    }
}
