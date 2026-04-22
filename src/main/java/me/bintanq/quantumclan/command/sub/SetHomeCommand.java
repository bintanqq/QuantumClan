package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SetHomeCommand {

    private final QuantumClan plugin;

    public SetHomeCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.home.set")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-set-home")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

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