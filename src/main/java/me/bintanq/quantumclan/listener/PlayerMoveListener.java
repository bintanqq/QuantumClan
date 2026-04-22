package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles player movement events.
 *
 * Currently used for:
 *  - Spy Scroll: update the compass target direction when a tracked player moves.
 *    SpyScrollManager maintains the active sessions; this listener only fires
 *    the update if the moving player IS the target of someone's spy scroll.
 *
 * Performance notes:
 *  - Checks SpyScrollManager.hasActiveSessions() first — if no spy scrolls are
 *    active the handler returns immediately (one ConcurrentHashMap.isEmpty() call).
 *  - Only tracks HEAD changes (not every block step) to reduce unnecessary calls:
 *    the event fires on every movement packet but we only care when the target
 *    actually moves to a different block (location X/Y/Z changed).
 */
public class PlayerMoveListener implements Listener {

    private final QuantumClan plugin;

    public PlayerMoveListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Fast exit — no active spy scroll sessions
        if (!plugin.getSpyScrollManager().hasActiveSessions()) return;

        // Only fire update when block position changes (not head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player moved = event.getPlayer();

        // Notify SpyScrollManager that this player (potential target) moved
        // The manager will update compass targets for all observers tracking this player
        plugin.getSpyScrollManager().onTargetMoved(moved);
    }
}