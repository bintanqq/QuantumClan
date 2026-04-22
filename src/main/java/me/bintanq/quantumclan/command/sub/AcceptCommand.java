// ════════════════════════════════════════════════════════════════════════════
// FILE: AcceptCommand.java  — fix hardcoded "Gagal bergabung."
// ════════════════════════════════════════════════════════════════════════════
package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AcceptCommand {
    private final QuantumClan plugin;

    public AcceptCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(Player player, String[] args) {
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
                        // FIX: was hardcoded "Gagal bergabung."
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