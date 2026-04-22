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
 *   2. ChatListener intercepts AsyncPlayerChatEvent / AsyncChatEvent,
 *      checks {@link #hasPendingInput(UUID)}, cancels the event, and calls
 *      {@link #handleInput(UUID, String)}.
 *   3. Input auto-cancels after {@code chat-input-timeout} seconds (configurable).
 *   4. Input cancelled automatically on player quit via {@link #cancelInput(UUID)}.
 *
 * "batal" (case-insensitive) typed by the player triggers the cancel callback.
 *
 * All timeouts run as Bukkit scheduler tasks so they respect plugin lifecycle.
 * Tasks are cancelled when the input is resolved (success, cancel, or timeout).
 */
public class ChatInputManager {

    // ── Inner record ──────────────────────────────────────────

    private static class PendingInput {
        final Consumer<String> onInput;   // called with the raw input string
        final Runnable onCancel;          // called on cancel or timeout
        final int taskId;                 // Bukkit scheduler task ID for timeout

        PendingInput(Consumer<String> onInput, Runnable onCancel, int taskId) {
            this.onInput   = onInput;
            this.onCancel  = onCancel;
            this.taskId    = taskId;
        }
    }

    // ── Fields ────────────────────────────────────────────────

    private final QuantumClan plugin;

    /** UUID → pending input session. One active session per player at a time. */
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    /** The cancel keyword — case-insensitive match. */
    private static final String CANCEL_KEYWORD = "batal";

    public ChatInputManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Prompts a player for chat input.
     * Sends {@code promptMessage} (MiniMessage) to the player, then waits for
     * their next chat message.
     *
     * If the player already has a pending input session it is cancelled first.
     *
     * @param player        The player to prompt
     * @param promptMessage MiniMessage-formatted prompt string (already parsed if needed,
     *                      or raw — caller decides). Pass null to skip sending the message.
     * @param onInput       Called on the main thread with the player's raw input string.
     *                      Will NOT be called if the player types the cancel keyword.
     * @param onCancel      Called on the main thread when the input is cancelled (by the
     *                      player typing "batal", timing out, or logging out). May be null.
     */
    public void prompt(Player player, String promptMessage,
                       Consumer<String> onInput, Runnable onCancel) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing session silently (no cancel callback for the old one)
        cancelSilent(uuid);

        // Send the prompt message
        if (promptMessage != null && !promptMessage.isBlank()) {
            plugin.sendRaw(player, promptMessage);
        }

        // Schedule auto-expire
        int timeoutSeconds = plugin.getConfigManager().getChatInputTimeout();
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingInput session = pending.remove(uuid);
            if (session != null) {
                if (session.onCancel != null) {
                    session.onCancel.run();
                }
            }
        }, timeoutSeconds * 20L).getTaskId();

        pending.put(uuid, new PendingInput(onInput, onCancel, taskId));
    }

    /**
     * Convenience overload without a prompt message.
     * Useful when the message was already sent (e.g. multi-step input).
     */
    public void awaitInput(Player player, Consumer<String> onInput, Runnable onCancel) {
        prompt(player, null, onInput, onCancel);
    }

    /**
     * Called by ChatListener when a player sends a chat message.
     * If the player has a pending session, consumes the message,
     * cancels the timeout task, and fires the appropriate callback on the main thread.
     *
     * @param uuid  Player UUID
     * @param input Raw chat message (already trimmed by caller)
     * @return true if the message was consumed (cancel the chat event), false otherwise
     */
    public boolean handleInput(UUID uuid, String input) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return false;

        // Cancel timeout task
        Bukkit.getScheduler().cancelTask(session.taskId);

        // Dispatch on main thread (callbacks may modify world state)
        if (CANCEL_KEYWORD.equalsIgnoreCase(input.trim())) {
            // Player typed "batal"
            if (session.onCancel != null) {
                Bukkit.getScheduler().runTask(plugin, session.onCancel);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> session.onInput.accept(input.trim()));
        }

        return true; // consumed
    }

    /**
     * Cancels a pending input session for the given player.
     * Fires the onCancel callback. Called on player quit.
     *
     * @param uuid Player UUID
     */
    public void cancelInput(UUID uuid) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);

        if (session.onCancel != null) {
            // Player already offline — run cancel callback sync on main thread
            // (do not send messages to offline player)
            Bukkit.getScheduler().runTask(plugin, session.onCancel);
        }
    }

    /**
     * Cancels a pending input session without triggering the onCancel callback.
     * Used internally when overwriting an existing session.
     */
    private void cancelSilent(UUID uuid) {
        PendingInput session = pending.remove(uuid);
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId);
    }

    /**
     * Returns true if the given player currently has a pending chat input session.
     * Used by ChatListener to decide whether to intercept the message.
     */
    public boolean hasPendingInput(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * Clears all pending sessions without firing any callbacks.
     * Call from onDisable to prevent memory leaks if the plugin shuts down mid-input.
     */
    public void clearAll() {
        for (PendingInput session : pending.values()) {
            Bukkit.getScheduler().cancelTask(session.taskId);
        }
        pending.clear();
    }
}