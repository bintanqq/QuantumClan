package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DelHomeCommand {
    private final QuantumClan plugin;
    public DelHomeCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.home.delete")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan delhome <nama>"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-delete-home")) {
            plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole()); return;
        }
        plugin.getClanManager().deleteHome(clan.getId(), args[0]).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "home.delete-success", "{value}", args[0]);
                    else plugin.sendMessage(player, "home.delete-not-found", "{value}", args[0]);
                }));
    }
}