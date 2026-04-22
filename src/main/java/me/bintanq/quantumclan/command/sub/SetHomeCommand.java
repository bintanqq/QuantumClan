package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHomeCommand implements SubCommand {

    private final QuantumClan plugin;

    public SetHomeCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.home.set")) return;
        if (args.length == 0) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
        if (!plugin.checkRole(player, "can-set-home")) return;

        String name = args[0];
        int maxHomes = plugin.getConfigManager().getMaxHomes(clan.getLevel());
        if (clan.getHome(name) == null && clan.getHomeCount() >= maxHomes) {
            plugin.sendMessage(player, "home.set-max");
            return;
        }

        Clan.ClanHome home = Clan.ClanHome.create(clan.getId(), name, player.getLocation());
        plugin.getClanManager().setHome(clan.getId(), home).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "home.set-success", "{value}", name);
                    else    plugin.sendMessage(player, "error.transaction-processing");
                }));
    }
}
