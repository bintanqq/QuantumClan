package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TransferCommand {
    private final QuantumClan plugin;
    public TransferCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.transfer")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan transfer <player>"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!clan.getLeaderUuid().equals(player.getUniqueId())) { plugin.sendMessage(player, "error.no-permission"); return; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { plugin.sendMessage(player, "error.player-not-found", "{player}", args[0]); return; }
        if (target.equals(player)) { plugin.sendMessage(player, "clan.transfer-self"); return; }
        if (!clan.hasMember(target.getUniqueId())) { plugin.sendMessage(player, "clan.kick-not-in-your-clan"); return; }
        plugin.getClanManager().transferLeadership(clan.getId(), player.getUniqueId(), target.getUniqueId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) return;
                    plugin.sendMessage(player, "clan.transfer-success", "{player}", target.getName());
                    plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                            plugin.sendMessage(m, "clan.transfer-broadcast", "{player}", target.getName()));
                }));
    }
}
