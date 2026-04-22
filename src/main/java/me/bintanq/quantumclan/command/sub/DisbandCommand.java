package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DisbandCommand {
    private final QuantumClan plugin;
    public DisbandCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.disband")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!clan.getLeaderUuid().equals(player.getUniqueId())) { plugin.sendMessage(player, "error.no-permission"); return; }
        String name = clan.getName();
        plugin.getClanManager().disbandClan(clan.getId()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.sendMessage(player, "clan.disband-success", "{clan}", name);
                    plugin.broadcast(plugin.getMessagesManager().get("clan.disband-broadcast", "{clan}", name));
                }));
    }
}
