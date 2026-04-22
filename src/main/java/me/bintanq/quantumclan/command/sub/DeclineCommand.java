package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeclineCommand implements SubCommand {
    private final QuantumClan plugin;
    public DeclineCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        String clanId = plugin.getClanManager().getInvite(player.getUniqueId());
        if (clanId == null) { plugin.sendMessage(player, "clan.invite-no-pending"); return; }
        Clan clan = plugin.getClanManager().getClanById(clanId);
        String name = clan != null ? clan.getName() : "Unknown";
        plugin.getClanManager().removeInvite(player.getUniqueId());
        plugin.sendMessage(player, "clan.invite-declined", "{clan}", name);
        if (clan != null) {
            plugin.getClanManager().getOnlineMembers(clanId).forEach(m ->
                    plugin.sendMessage(m, "clan.invite-declined-broadcast", "{player}", player.getName()));
        }
    }
}
