package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Cleans up all per-player runtime state when a player disconnects:
 *
 *  1. Cancel any pending ChatInputManager session (fires onCancel callback).
 *  2. Eliminate player from active war (disconnect = eliminated).
 *  3. Remove active SpyScroll session.
 *  4. Remove pending clan invite.
 *  5. Clear BuffTracker active flag if needed (buffs are persisted to DB by BuffTracker).
 */
public class PlayerQuitListener implements Listener {

    private final QuantumClan plugin;

    public PlayerQuitListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        // 1. Cancel pending chat input (fires onCancel, e.g. "Pembuatan clan dibatalkan")
        plugin.getChatInputManager().cancelInput(uuid);

        // 2. War elimination on disconnect
        WarManager warManager = plugin.getWarManager();
        WarSession activeWar  = warManager.getActiveSession();
        if (activeWar != null && activeWar.isActive()
                && activeWar.isMemberParticipating(uuid)
                && activeWar.isMemberAlive(uuid)) {

            String clanId = activeWar.getClanIdForMember(uuid);
            if (clanId != null) {
                activeWar.eliminateMember(uuid, clanId);

                // Broadcast disconnect elimination to war participants
                plugin.broadcast(plugin.getMessagesManager()
                        .get("war.disconnect-eliminated",
                                "{player}", player.getName()));

                // Check if war should end after this elimination
                warManager.checkWarEnd(activeWar);
            }
        }

        // 3. Remove spy scroll session
        plugin.getSpyScrollManager().removeSession(uuid);

        // 4. Remove pending invite
        plugin.getClanManager().removeInvite(uuid);

        // 5. Notify BuffTracker (clears in-memory active buff tracking for this player)
        plugin.getBuffTracker().onPlayerQuit(uuid);
    }
}