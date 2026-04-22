package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class InviteCommand {

    private final QuantumClan plugin;

    public InviteCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.invite")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        if (args.length == 0) {
            plugin.sendRaw(player, "<red>/qclan invite <player>");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-invite")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        // Check if clan is full
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
            plugin.sendRaw(player, "<red>Kamu tidak bisa mengundang dirimu sendiri.");
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

        // Register the invite
        plugin.getClanManager().addInvite(target.getUniqueId(), clan.getId());

        // Notify inviter
        plugin.sendMessage(player, "clan.invite-sent", "{player}", target.getName());

        // Notify invitee
        plugin.sendMessage(target, "clan.invite-received", "{clan}", clan.getName());

        // Auto-expire invite after chat-input-timeout seconds
        int timeoutSeconds = plugin.getConfigManager().getChatInputTimeout();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove invite only if it's still the same clan (player might have received a new one)
            String pending = plugin.getClanManager().getInvite(target.getUniqueId());
            if (clan.getId().equals(pending)) {
                plugin.getClanManager().removeInvite(target.getUniqueId());
            }
        }, timeoutSeconds * 20L);
    }
}