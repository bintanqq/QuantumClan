package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LeaveCommand {
    private final QuantumClan plugin;
    public LeaveCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.leave")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (clan.getLeaderUuid().equals(player.getUniqueId())) { plugin.sendMessage(player, "clan.leave-leader"); return; }
        String name = clan.getName();
        plugin.getClanManager().removeMember(player.getUniqueId()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) return;
                    plugin.sendMessage(player, "clan.leave-success", "{clan}", name);
                    plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                            plugin.sendMessage(m, "clan.leave-broadcast", "{player}", player.getName()));
                }));
    }
}