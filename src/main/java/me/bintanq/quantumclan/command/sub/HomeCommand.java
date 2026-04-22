package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanHomeGUI;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanRole;
import me.bintanq.quantumclan.model.ClanMember;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class HomeCommand {
    private final QuantumClan plugin;
    public HomeCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.home.use")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (clan.getHomes().isEmpty()) { plugin.sendMessage(player, "home.no-homes"); return; }
        if (args.length == 0) {
            ClanHomeGUI.open(plugin, player, clan); return;
        }
        // Direct teleport
        Clan.ClanHome home = clan.getHome(args[0]);
        if (home == null) { plugin.sendMessage(player, "home.teleport-not-found", "{value}", args[0]); return; }
        if (!player.hasPermission("quantumclan.bypass.cooldown") && plugin.getBuffTracker().isOnHomeCooldown(player.getUniqueId())) {
            plugin.sendMessage(player, "home.teleport-cooldown", "{value}", plugin.getBuffTracker().getHomeRemainingCooldown(player.getUniqueId()));
            return;
        }
        Location loc = home.toBukkitLocation();
        if (loc == null) { plugin.sendMessage(player, "home.teleport-not-found", "{value}", home.getName()); return; }
        player.teleport(loc);
        plugin.getBuffTracker().setLastHomeTeleport(player.getUniqueId());
        plugin.sendMessage(player, "home.teleport-success", "{value}", home.getName());
    }
}
