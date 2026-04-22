package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveCommand implements SubCommand {
    private final QuantumClan plugin;
    public LeaveCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.clan.leave")) return;
        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
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
