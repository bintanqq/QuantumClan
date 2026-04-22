package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * /qclan announce <message>
 *
 * BUG FIX #5: Broadcast now uses newline format (visual gap/list style)
 * by replacing {newline} tokens in the messages.yml template with actual
 * Component newlines before broadcasting.
 *
 * BUG FIX cooldown: Added missing return after cooldown message (was fixed before,
 * keeping it correct here).
 *
 * No hardcoded messages — all strings come from messages.yml.
 */
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

        // BUG FIX cooldown: was missing return
        if (plugin.getBuffTracker().isOnAnnounceCooldown(clan.getId())) {
            plugin.sendMessage(player, "clan.announce-cooldown",
                    "{value}", String.valueOf(plugin.getBuffTracker()
                            .getAnnounceRemainingCooldown(clan.getId())));
            return;
        }

        if (args.length == 0) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }

        String msg = String.join(" ", args);

        // Set cooldown BEFORE broadcasting (prevents race condition with double-click)
        plugin.getBuffTracker().setAnnounceCooldown(clan.getId());

        // Load template from messages.yml
        String rawTemplate = plugin.getMessagesManager().get("clan.announce-broadcast",
                "{tag}", clan.getFormattedTag(),
                "{message}", msg);

        // BUG FIX #5: Split on {newline} token, then send each segment as a separate
        // Component line. This creates visible gaps between sections in the broadcast,
        // matching the "list style" requested.
        MiniMessage mm = plugin.getMiniMessage();
        String[] lines = rawTemplate.split("\\{newline\\}");

        // Build a combined component with real newlines between segments
        Component broadcast = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                // Blank segment = empty line (visual gap)
                broadcast = broadcast.append(Component.newline());
            } else {
                broadcast = broadcast.append(mm.deserialize(line));
            }
            if (i < lines.length - 1) {
                broadcast = broadcast.append(Component.newline());
            }
        }

        // Broadcast to all players and console
        final Component finalBroadcast = broadcast;
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalBroadcast));
        Bukkit.getConsoleSender().sendMessage(finalBroadcast);
    }
}