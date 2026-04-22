package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class InviteCommand implements SubCommand {

    private final QuantumClan plugin;

    public InviteCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!plugin.checkPerm(player, "quantumclan.clan.invite")) return;
        if (args.length == 0) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
        if (!plugin.checkRole(player, "can-invite")) return;

        int maxMembers = plugin.getConfigManager().getMaxMembers(clan.getLevel());
        if (clan.getMemberCount() >= maxMembers) {
            plugin.sendMessage(player, "clan.invite-clan-full");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.sendMessage(player, "error.player-not-found", "{player}", args[0]);
            return;
        }

        if (target.equals(player)) {
            plugin.sendMessage(player, "clan.invite-self");
            return;
        }

        if (plugin.getClanManager().isInClan(target.getUniqueId())) {
            plugin.sendMessage(player, "clan.already-in-clan", "{player}", target.getName());
            return;
        }

        if (plugin.getClanManager().hasInvite(target.getUniqueId())) {
            plugin.sendMessage(player, "clan.invite-already-sent", "{player}", target.getName());
            return;
        }

        plugin.getClanManager().addInvite(target.getUniqueId(), clan.getId());
        plugin.sendMessage(player, "clan.invite-sent", "{player}", target.getName());
        plugin.sendMessage(target, "clan.invite-received", "{clan}", clan.getName());

        int timeoutSeconds = plugin.getConfigManager().getChatInputTimeout();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String pending = plugin.getClanManager().getInvite(target.getUniqueId());
            if (clan.getId().equals(pending)) {
                plugin.getClanManager().removeInvite(target.getUniqueId());
            }
        }, timeoutSeconds * 20L);
    }
}
