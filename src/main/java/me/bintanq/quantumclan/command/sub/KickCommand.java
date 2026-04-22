package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.ClanRole;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class KickCommand {

    private final QuantumClan plugin;

    public KickCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.kick")) {
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
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-kick")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        String targetName = args[0];
        UUID targetUuid = resolveUuid(targetName);
        if (targetUuid == null) {
            plugin.sendMessage(player, "error.player-not-found", "{player}", targetName);
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            plugin.sendMessage(player, "clan.kick-self");
            return;
        }

        ClanMember targetMember = plugin.getClanManager().getMember(targetUuid);
        if (targetMember == null || !targetMember.getClanId().equals(clan.getId())) {
            plugin.sendMessage(player, "clan.kick-not-in-your-clan");
            return;
        }

        ClanMember kickerMember = plugin.getClanManager().getMember(player.getUniqueId());
        if (kickerMember == null) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        ClanRole kickerRole = plugin.getRolesConfigManager().getRole(kickerMember.getRole());
        ClanRole targetRole = plugin.getRolesConfigManager().getRole(targetMember.getRole());

        if (kickerRole == null || targetRole == null || !kickerRole.outranks(targetRole)
                || targetRole.isLeader()) {
            plugin.sendMessage(player, "clan.kick-higher-role");
            return;
        }

        Player targetOnline = Bukkit.getPlayer(targetUuid);
        String displayName = targetOnline != null
                ? targetOnline.getName()
                : Bukkit.getOfflinePlayer(targetUuid).getName();
        if (displayName == null) displayName = targetName;
        final String finalName = displayName;

        plugin.getClanManager().removeMember(targetUuid).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        plugin.sendMessage(player, "error.transaction-processing");
                        return;
                    }
                    plugin.sendMessage(player, "clan.kick-success", "{player}", finalName);
                    if (targetOnline != null && targetOnline.isOnline()) {
                        plugin.sendMessage(targetOnline, "clan.kick-broadcast",
                                "{player}", finalName, "{target}", player.getName());
                    }
                    plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m -> {
                        if (!m.equals(player)) {
                            plugin.sendMessage(m, "clan.kick-broadcast",
                                    "{player}", finalName, "{target}", player.getName());
                        }
                    });
                }));
    }

    @SuppressWarnings("deprecation")
    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.hasPlayedBefore() || op.isOnline()) return op.getUniqueId();
        return null;
    }
}