package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanHallGUI;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /qclan hall [buy|tp]
 *
 * Subcommands:
 *   (no args) → open ClanHallGUI (info + buy button)
 *   buy       → open purchase confirmation GUI
 *   tp        → teleport to hall (requires access, blocked during war)
 */
public class HallCommand implements SubCommand {

    private final QuantumClan plugin;

    public HallCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.getHallConfigManager().isEnabled()) {
            plugin.sendMessage(player, "hall.disabled");
            return;
        }

        if (args.length == 0) {
            ClanHallGUI.open(plugin, player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "buy"  -> handleBuy(player);
            case "tp"   -> handleTp(player);
            default     -> ClanHallGUI.open(plugin, player);
        }
    }

    private void handleBuy(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }
        if (plugin.getClanHallManager().hasAccess(clan.getId())) {
            plugin.sendMessage(player, "hall.already-has-access");
            return;
        }
        // Check role permission
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-buy-hall")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }
        ClanHallGUI.open(plugin, player);
    }

    private void handleTp(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }
        if (!plugin.getClanHallManager().hasAccess(clan.getId())) {
            plugin.sendMessage(player, "hall.no-access");
            return;
        }

        // War block
        WarSession war = plugin.getWarManager().getActiveSession();
        if (war != null && war.isActive()
                && war.isMemberParticipating(player.getUniqueId())
                && war.isMemberAlive(player.getUniqueId())) {
            plugin.sendMessage(player, "hall.war-blocked");
            return;
        }

        var loc = plugin.getHallConfigManager().getTeleportLocation();
        if (loc == null) {
            plugin.sendMessage(player, "hall.region-not-set");
            return;
        }
        player.teleport(loc);
        plugin.sendMessage(player, "hall.teleported");
    }
}
