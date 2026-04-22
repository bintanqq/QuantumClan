package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AnnounceCommand {

    private final QuantumClan plugin;

    public AnnounceCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.announce")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-announce")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        // BUG FIX: was missing return after sending cooldown message
        if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
            plugin.sendMessage(player, "clan.announce-cooldown",
                    "{value}", String.valueOf(plugin.getBuffTracker().getAnnounceRemainingCooldown(clan.getId())));
            return; // ← this return was missing in original
        }

        if (args.length == 0) {
            plugin.sendRaw(player, plugin.getMessagesManager().get("error.unknown-subcommand"));
            return;
        }

        String msg = String.join(" ", args);

        // Set cooldown BEFORE broadcasting (prevents race condition)
        plugin.getBuffTracker().setAnnounceCooldown(clan.getId());

        // Build broadcast from messages.yml template
        // {newline} in the template is replaced with actual newline component
        String rawTemplate = plugin.getMessagesManager().get("clan.announce-broadcast",
                "{tag}", clan.getFormattedTag(),
                "{message}", msg);

        // Replace literal {newline} with actual newline for visual spacing
        String processed = rawTemplate.replace("{newline}", "\n");

        MiniMessage mm = plugin.getMiniMessage();
        Component broadcast = mm.deserialize(processed);

        // Broadcast to ALL players on the server
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        Bukkit.getConsoleSender().sendMessage(broadcast);
    }
}