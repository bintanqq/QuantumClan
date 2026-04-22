package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * Handles hall region entry/exit detection and access control.
 *
 * Responsibilities:
 *  1. PlayerMoveEvent: detect region entry/exit (fallback when WorldGuard unavailable)
 *  2. Access check: only members of clans with hall access may enter
 *  3. War protection: push back war participants trying to enter
 *  4. Apply/remove passive buffs on region transitions
 *  5. Clean up on player quit
 *
 * WorldGuard handles building/PVP flags; this listener handles membership access.
 */
public class HallListener implements Listener {

    private final QuantumClan plugin;

    public HallListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only process when crossing block boundaries for performance
        if (sameBlock(event.getFrom(), event.getTo())) return;
        if (!plugin.getHallConfigManager().isEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean wasInside = plugin.getClanHallManager().isInsideHall(uuid);
        boolean isInside  = plugin.getHallConfigManager().isInsideRegion(event.getTo());

        if (isInside && !wasInside) {
            // Entering the hall region
            handleEntry(event, player);
        } else if (!isInside && wasInside) {
            // Exiting the hall region
            plugin.getClanHallManager().onPlayerExitHall(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getHallConfigManager().isEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean wasInside = plugin.getClanHallManager().isInsideHall(uuid);
        boolean destInside = event.getTo() != null
                && plugin.getHallConfigManager().isInsideRegion(event.getTo());

        if (destInside && !wasInside) {
            // Check access before allowing teleport into hall
            if (!canEnter(player)) {
                event.setCancelled(true);
                plugin.sendMessage(player, "hall.no-access");
                return;
            }
            // War check
            if (isWarParticipant(player)) {
                event.setCancelled(true);
                plugin.sendMessage(player, "hall.war-blocked");
                return;
            }
            plugin.getClanHallManager().onPlayerEnterHall(player);
        } else if (!destInside && wasInside) {
            plugin.getClanHallManager().onPlayerExitHall(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getHallConfigManager().isEnabled()) return;
        plugin.getClanHallManager().onPlayerQuit(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void handleEntry(PlayerMoveEvent event, Player player) {
        // War participants cannot enter
        if (isWarParticipant(player)) {
            // Push player back
            event.setTo(event.getFrom());
            plugin.sendMessage(player, "hall.war-blocked");
            return;
        }

        // Access check
        if (!canEnter(player)) {
            event.setTo(event.getFrom());
            plugin.sendMessage(player, "hall.no-access");
            return;
        }

        plugin.getClanHallManager().onPlayerEnterHall(player);
    }

    /**
     * Returns true if the player's clan has valid hall access.
     * Admins with quantumclan.admin bypass access check.
     */
    private boolean canEnter(Player player) {
        if (player.hasPermission("quantumclan.admin")) return true;

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return false;

        return plugin.getClanHallManager().hasAccess(clan.getId());
    }

    /**
     * Returns true if the player is an active participant in a running war.
     */
    private boolean isWarParticipant(Player player) {
        WarSession war = plugin.getWarManager().getActiveSession();
        if (war == null || !war.isActive()) return false;
        UUID uuid = player.getUniqueId();
        return war.isMemberParticipating(uuid) && war.isMemberAlive(uuid);
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}