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
 * Handles dua hal:
 *
 * 1. Chat input interception — jika player punya pending ChatInputManager session,
 *    cancel event dan forward teks ke manager.
 *
 * 2. Clan tag injection — jika chat.tag-enabled = true di config,
 *    tambahkan tag clan sebagai PREFIX (di depan display name) atau
 *    SUFFIX (di belakang display name).
 *    Chat format asli dari plugin lain (EssentialsChat, dll) tidak disentuh —
 *    kita hanya memodifikasi displayName player di event ini.
 *
 * Priority LOWEST untuk intercept input (tangkap sebelum plugin lain).
 * Priority HIGH untuk tag injection (setelah plugin chat lain format pesannya).
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

        // Extract plain text
        String rawText = PlainTextComponentSerializer.plainText()
                .serialize(event.originalMessage());

        // Cancel — pesan ini dikonsumsi sebagai input, bukan dikirim ke chat
        event.setCancelled(true);

        // Forward ke ChatInputManager (callback dispatch ke main thread)
        plugin.getChatInputManager().handleInput(uuid, rawText);
    }

    // ── 2. Clan tag injection (HIGH) ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClanChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isClanChatTagEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        Clan clan = plugin.getClanManager().getClanByPlayer(uuid);
        if (clan == null) return; // tidak di clan — skip

        String tagFormat  = plugin.getConfigManager().getChatTagFormat();
        String position   = plugin.getConfigManager().getChatTagPosition(); // PREFIX | SUFFIX
        String coloredTag = clan.getColoredTag();

        // Resolve tag component dari config format
        // tagFormat contoh: "<gray>[{tag}<gray>] " → ganti {tag} dengan colored tag
        String resolvedTag = tagFormat.replace("{tag}", coloredTag);
        Component tagComponent = mm.deserialize(resolvedTag);

        // Ambil display name saat ini
        Component originalName = event.getPlayer().displayName();

        // Inject tag ke display name untuk event ini saja
        Component newName;
        if ("SUFFIX".equals(position)) {
            newName = originalName.append(tagComponent);
        } else {
            // PREFIX (default)
            newName = tagComponent.append(originalName);
        }

        // Override display name hanya untuk event ini via renderer
        // Ini lebih aman daripada setDisplayName() permanen
        event.renderer((source, sourceDisplayName, message, viewer) ->
                newName
                        .append(Component.text(": "))
                        .append(message)
        );
    }
}