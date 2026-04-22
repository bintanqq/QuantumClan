package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptCommand implements SubCommand {
    private final QuantumClan plugin;

    public AcceptCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (plugin.getClanManager().isInClan(player.getUniqueId())) {
            plugin.sendMessage(player, "clan.create-already-in-clan");
            return;
        }
        String clanId = plugin.getClanManager().getInvite(player.getUniqueId());
        if (clanId == null) {
            plugin.sendMessage(player, "clan.invite-no-pending");
            return;
        }
        Clan clan = plugin.getClanManager().getClanById(clanId);
        if (clan == null) {
            plugin.getClanManager().removeInvite(player.getUniqueId());
            return;
        }
        plugin.getClanManager().removeInvite(player.getUniqueId());
        plugin.getClanManager().addMember(clanId, player.getUniqueId()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        plugin.sendMessage(player, "error.transaction-processing");
                        return;
                    }
                    plugin.sendMessage(player, "clan.invite-accepted", "{clan}", clan.getName());
                    plugin.getClanManager().getOnlineMembers(clanId).forEach(m -> {
                        if (!m.equals(player)) {
                            plugin.sendMessage(m, "clan.invite-accepted-broadcast",
                                    "{player}", player.getName());
                        }
                    });
                }));
    }
}
