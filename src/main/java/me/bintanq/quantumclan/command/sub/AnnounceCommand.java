package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

public class AnnounceCommand {
    private final QuantumClan plugin;
    public AnnounceCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.announce")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-announce")) {
            plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole()); return;
        }
        if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
            plugin.sendMessage(player, "clan.announce-cooldown", "{value}", plugin.getBuffTracker().getAnnounceRemainingCooldown(clan.getId())); return;
        }
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan announce <pesan>"); return; }
        String msg = String.join(" ", args);
        plugin.getBuffTracker().setAnnounceCooldown(clan.getId());
        String broadcast = plugin.getMessagesManager().get("clan.announce-broadcast",
                "{tag}", clan.getColoredTag(), "{message}", msg);
        plugin.broadcast(broadcast);
    }
}