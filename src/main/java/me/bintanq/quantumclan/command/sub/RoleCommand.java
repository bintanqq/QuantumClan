package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RoleCommand {
    private final QuantumClan plugin;
    public RoleCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.role")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) { plugin.sendRaw(player, "<red>/qclan role set <player> <role>"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-set-role")) {
            plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole()); return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { plugin.sendMessage(player, "error.player-not-found", "{player}", args[1]); return; }
        String newRole = args[2].toLowerCase();
        if (plugin.getRolesConfigManager().getRole(newRole) == null) { plugin.sendMessage(player, "clan.role-invalid", "{role}", newRole); return; }
        plugin.getClanManager().setMemberRole(player.getUniqueId(), target.getUniqueId(), newRole)
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) { plugin.sendMessage(player, "clan.role-higher"); return; }
                    plugin.sendMessage(player, "clan.role-set-success", "{player}", target.getName(), "{role}", newRole);
                    plugin.getClanManager().getOnlineMembers(clan.getId()).forEach(m ->
                            plugin.sendMessage(m, "clan.role-set-broadcast", "{player}", target.getName(), "{role}", newRole));
                }));
    }
}
