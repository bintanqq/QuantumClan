package me.bintanq.quantumclan.util;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages chat-based input collection from players.
 *
 * Usage pattern:
 *   1. Call {@link #prompt(Player, String, Consumer, Runnable)} to send a prompt
 *      and register a callback for the next chat message.
 *   2. ChatListener intercepts AsyncChatEvent at LOWEST priority,
 *      checks {@link #hasPendingInput(UUID)}, cancels the event, and calls
 *      {@link #handleInput(UUID, String)}.
 *   3. Input auto-cancels after {@code chat-input-timeout} seconds (configurable).
 *   4. Input cancelled automatically on player quit via {@link #cancelInput(UUID)}.
 *
 * BUG FIX #4: Cancel keyword changed from "batal" to "cancel" (English, global plugin).
 */
public class ChatInputManager {

    // ── Inner record ──────────────────────────────────────────

    private static class PendingInput {
        final Consumer<String> onInput;
        final Runnable onCancel;
        final int taskId;

        PendingInput(Consumer<String> onInput, Runnable onCancel, int taskId) {
            this.onInput   = onInput;
            this.onCancel  = onCancel;
            this.taskId    = taskId;
        }
    }

    // ── Fields ────────────────────────────────────────────────

    private final QuantumClan plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    /**
     * BUG FIX #4: Was "batal" (Indonesian). Changed to "cancel" (English).
     */
    private static final String CANCEL_KEYWORD = "cancel";

    public ChatInputManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Prompts a player for chat input.
     * Sends {@code promptMessage} (MiniMessage) to the player, then waits for
     * their next chat message.
     *
     * If the player already has a pending input session it is cancelled first (silently).
     *
     * @param player        The player to prompt
     * @param promptMessage MiniMessage-formatted prompt string. Pass null to skip.
     * @param onInput       Called on the main thread with the player's raw input string.
     *                      Will NOT be called if the player types the cancel keyword.
     * @param onCancel      Called on the main thread when the input is cancelled.
     *                      May be null.
     */
    public void prompt(Player player, String promptMessage,
                       Consumer<String> onInput, Runnable onCancel) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing session silently
        cancelSilent(uuid);

        // Send the prompt message
        if (promptMessage != null && !promptMessage.isBlank()) {
            plugin.sendRaw(player, promptMessage);
        }

        // Schedule auto-expire
        int timeoutSeconds = plugin.getConfigManager().getChatInputTimeout();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingInput session = pending.remove(uuid);
            if (session != null && session.onCancel != null) {
                session.onCancel.run();
            }
        }, timeoutSeconds * 20L).getTaskId();

        pending.put(uuid, new PendingInput(onInput, onCancel, taskId));
    }

    /**
     * Convenience overload without a prompt message.
     */
    public void awaitInput(Player player, Consumer<String> onInput, Runnable onCancel) {
        prompt(player, null, onInput, onCancel);
    }

    /**
     * Called by ChatListener when a player sends a chat message.
     * Consumes the message, cancels the timeout task, and fires the callback.
     *
     * @param uuid  Player UUID
     * @param input Raw chat message (already trimmed by caller)
     * @return true if the message was consumed, false otherwise
     */
    public boolean handleInput(UUID uuid, String input) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return false;

        Bukkit.getScheduler().cancelTask(session.taskId);

        // BUG FIX #4: cancel keyword is now "cancel" (English)
        if (CANCEL_KEYWORD.equalsIgnoreCase(input.trim())) {
            if (session.onCancel != null) {
                Bukkit.getScheduler().runTask(plugin, session.onCancel);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> session.onInput.accept(input.trim()));
        }

        return true;
    }

    /**
     * Cancels a pending input session for the given player.
     * Fires the onCancel callback. Called on player quit.
     */
    public void cancelInput(UUID uuid) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);

        if (session.onCancel != null) {
            Bukkit.getScheduler().runTask(plugin, session.onCancel);
        }
    }

    /**
     * Cancels silently without triggering the onCancel callback.
     */
    private void cancelSilent(UUID uuid) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId);
    }

    public boolean hasPendingInput(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void clearAll() {
        for (PendingInput session : pending.values()) {
            Bukkit.getScheduler().cancelTask(session.taskId);
        }
        pending.clear();
    }
}