package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active Spy Scroll tracking sessions.
 *
 * A "session" = observer tracks a specific target for X seconds.
 * - Observer gets compass pointing to target + actionbar with location
 * - Target is notified once (optionally configurable)
 * - Session auto-expires after spy-scroll-duration seconds
 *
 * Update cycle: every spy-scroll-update-interval ticks (default 60 = 3 seconds).
 * PlayerMoveListener calls onTargetMoved() — but SpyScrollManager only
 * updates if the internal scheduler fires (rate-limited to 3 sec).
 *
 * Performance: listener checks hasActiveSessions() first — early exit if empty.
 */
public class SpyScrollManager {

    private final QuantumClan plugin;
    private final MiniMessage mm;

    /** observerUuid → SpySession */
    private final Map<UUID, SpySession> sessions = new ConcurrentHashMap<>();

    /** targetUuid → set of observers tracking this target */
    private final Map<UUID, Set<UUID>> targetToObservers = new ConcurrentHashMap<>();

    private int updateTaskId = -1;

    public SpyScrollManager(QuantumClan plugin) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        startUpdateScheduler();
    }

    // ── Session management ────────────────────────────────────

    /**
     * Starts a spy scroll tracking session.
     *
     * @param observer The player using the scroll
     * @param target   The player being tracked
     */
    public void startSession(Player observer, Player target) {
        UUID observerUuid = observer.getUniqueId();
        UUID targetUuid   = target.getUniqueId();

        // Remove existing session if any
        removeSession(observerUuid);

        int durationSec = plugin.getConfigManager().getSpyScrollDuration();
        long expiryMillis = System.currentTimeMillis() + durationSec * 1000L;

        SpySession session = new SpySession(observerUuid, targetUuid, expiryMillis);
        sessions.put(observerUuid, session);
        targetToObservers.computeIfAbsent(targetUuid, k -> ConcurrentHashMap.newKeySet())
                .add(observerUuid);

        // Point compass at target
        if (target.isOnline()) {
            observer.setCompassTarget(target.getLocation());
        }

        String trackingMsg = plugin.getMessagesManager().get("spy-scroll.tracking",
                "{player}", target.getName(),
                "{duration}", String.valueOf(durationSec / 60));
        observer.sendActionBar(mm.deserialize(trackingMsg));

        plugin.getLogger().info("[SpyScroll] " + observer.getName() + " tracking " + target.getName());
    }

    public void removeSession(UUID observerUuid) {
        SpySession session = sessions.remove(observerUuid);
        if (session == null) return;

        Set<UUID> observers = targetToObservers.get(session.targetUuid);
        if (observers != null) {
            observers.remove(observerUuid);
            if (observers.isEmpty()) targetToObservers.remove(session.targetUuid);
        }
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    /**
     * Called by PlayerMoveListener when a tracked player moves.
     * Updates compass for all observers tracking that player.
     */
    public void onTargetMoved(Player target) {
        UUID targetUuid = target.getUniqueId();
        Set<UUID> observers = targetToObservers.get(targetUuid);
        if (observers == null || observers.isEmpty()) return;

        for (UUID observerUuid : observers) {
            Player observer = Bukkit.getPlayer(observerUuid);
            if (observer != null && observer.isOnline()) {
                observer.setCompassTarget(target.getLocation());
            }
        }
    }

    // ── Scheduler ─────────────────────────────────────────────

    private void startUpdateScheduler() {
        int interval = plugin.getConfigManager().getSpyScrollUpdateInterval();
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval)
                .getTaskId();
    }

    private void tick() {
        if (sessions.isEmpty()) return;

        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            SpySession session = e.getValue();
            UUID observerUuid  = e.getKey();

            // Check expiry
            if (now >= session.expiryMillis) {
                // Cleanup target map
                Set<UUID> observers = targetToObservers.get(session.targetUuid);
                if (observers != null) {
                    observers.remove(observerUuid);
                    if (observers.isEmpty()) targetToObservers.remove(session.targetUuid);
                }
                Player observer = Bukkit.getPlayer(observerUuid);
                if (observer != null) {
                    String expiredMsg = plugin.getMessagesManager().get("spy-scroll.expired");
                    observer.sendActionBar(mm.deserialize(expiredMsg));
                }
                return true; // remove
            }

            // Update actionbar for observer
            Player observer = Bukkit.getPlayer(observerUuid);
            Player target   = Bukkit.getPlayer(session.targetUuid);

            if (observer == null || !observer.isOnline()) {
                return true; // observer offline — remove session
            }

            if (target == null || !target.isOnline()) {
                String offlineMsg = plugin.getMessagesManager().get("spy-scroll.target-offline",
                        "{player}", Bukkit.getOfflinePlayer(session.targetUuid).getName() != null
                                ? Bukkit.getOfflinePlayer(session.targetUuid).getName()
                                : session.targetUuid.toString().substring(0, 8));
                observer.sendActionBar(mm.deserialize(offlineMsg));
                return false; // keep session — target may come back
            }

            // Update compass
            observer.setCompassTarget(target.getLocation());

            long remainingSec = (session.expiryMillis - now) / 1000L;
            String actionbarMsg = plugin.getMessagesManager().get("spy-scroll.actionbar",
                    "{player}", target.getName(),
                    "{world}",  target.getWorld().getName(),
                    "{x}",      String.valueOf((int) target.getLocation().getX()),
                    "{y}",      String.valueOf((int) target.getLocation().getY()),
                    "{z}",      String.valueOf((int) target.getLocation().getZ()),
                    "{time}",   String.valueOf(remainingSec));
            observer.sendActionBar(mm.deserialize(actionbarMsg));

            return false;
        });
    }

    public void stopScheduler() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
    }

    // ── Nested: SpySession ────────────────────────────────────

    private static class SpySession {
        final UUID observerUuid;
        final UUID targetUuid;
        final long expiryMillis;

        SpySession(UUID observerUuid, UUID targetUuid, long expiryMillis) {
            this.observerUuid = observerUuid;
            this.targetUuid   = targetUuid;
            this.expiryMillis = expiryMillis;
        }
    }
}