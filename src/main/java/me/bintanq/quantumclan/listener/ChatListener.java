package me.bintanq.quantumclan.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Handles two responsibilities:
 *
 * 1. Chat input interception — if a player has a pending ChatInputManager session,
 *    cancel the event and forward the text to the manager.
 *
 * 2. Clan tag injection — if clan chat is enabled in config, prepend or append
 *    the player's clan tag to their chat message using MiniMessage formatting.
 *
 * Priority: LOWEST for input interception (catch it first, before other plugins).
 * Priority: HIGH for tag injection (after chat plugins format the message).
 */
public class ChatListener implements Listener {

    private final QuantumClan plugin;
    private final MiniMessage mm;

    public ChatListener(QuantumClan plugin) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
    }

    // ── Input interception (LOWEST — catch before other plugins) ──

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        if (!plugin.getChatInputManager().hasPendingInput(uuid)) return;

        // Extract plain text from the Adventure Component
        String rawText = PlainTextComponentSerializer.plainText()
                .serialize(event.originalMessage());

        // Cancel the chat event — this message is consumed as input
        event.setCancelled(true);

        // Forward to ChatInputManager (it dispatches callbacks to main thread)
        plugin.getChatInputManager().handleInput(uuid, rawText);
    }

    // ── Clan tag injection (HIGH — after chat format plugins) ─────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClanChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isClanChatEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
        if (clan == null) return; // Not in a clan — no tag

        ClanMember member = plugin.getClanManager().getMember(uuid);
        if (member == null) return;

        String position   = plugin.getConfigManager().getChatTagPosition(); // PREFIX or SUFFIX
        String formatStr  = plugin.getConfigManager().getChatFormat();
        String coloredTag = clan.getColoredTag();

        // Build the final renderer
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String playerName = source.getName();

            // Serialize original message to plain then re-wrap in MiniMessage-safe form
            String plainMessage = PlainTextComponentSerializer.plainText()
                    .serialize(message);

            // Escape any MiniMessage tags in the player's message for safety
            String escapedMessage = mm.escapeTags(plainMessage);

            String formatted = formatStr
                    .replace("{tag}",     coloredTag)
                    .replace("{player}",  playerName)
                    .replace("{message}", escapedMessage);

            return mm.deserialize(formatted);
        });
    }
}