package me.bintanq.quantumclan.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
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
 *    cancels the event and forwards text to the manager.
 *    Cancel keyword: "cancel" (case-insensitive, English).
 *
 * 2. Clan tag injection — if chat.tag-enabled = true in config,
 *    adds the clan tag as PREFIX or SUFFIX to the player's display name
 *    for that chat event only.
 */
public class ChatListener implements Listener {

    private final QuantumClan plugin;
    private final MiniMessage mm;

    public ChatListener(QuantumClan plugin) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
    }

    // ── 1. Input interception (LOWEST) ───────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        if (!plugin.getChatInputManager().hasPendingInput(uuid)) return;

        String rawText = PlainTextComponentSerializer.plainText()
                .serialize(event.originalMessage());

        // Cancel — consumed as input, not sent to chat
        event.setCancelled(true);

        // Forward to ChatInputManager (dispatches callback to main thread)
        plugin.getChatInputManager().handleInput(uuid, rawText);
    }

    // ── 2. Clan tag injection (HIGH) ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClanChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isClanChatTagEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
        if (clan == null) return;

        String tagFormat  = plugin.getConfigManager().getChatTagFormat();
        String position   = plugin.getConfigManager().getChatTagPosition(); // PREFIX | SUFFIX
        String coloredTag = clan.getColoredTag();

        String resolvedTag = tagFormat.replace("{tag}", coloredTag);
        Component tagComponent = mm.deserialize(resolvedTag);

        Component originalName = event.getPlayer().displayName();

        Component newName;
        if ("SUFFIX".equals(position)) {
            newName = originalName.append(tagComponent);
        } else {
            // PREFIX (default)
            newName = tagComponent.append(originalName);
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                newName
                        .append(Component.text(": "))
                        .append(message)
        );
    }
}