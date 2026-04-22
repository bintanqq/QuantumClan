package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Enforces war-related rules during an active war session:
 *
 * 1. ARENA PROTECTION (when WorldGuard is NOT available as fallback):
 *    - Block break / block place inside the arena radius by non-participants.
 *    - Block break / place inside arena even by participants during war.
 *
 * 2. PVP ENFORCEMENT:
 *    - Outside war: block PvP between members of different clans if one is shielded
 *      (future extension point).
 *    - Inside war: only allow PvP between registered war participants from different clans.
 *      Friendly fire (same clan) is blocked.
 *
 * 3. COMMAND BLOCK:
 *    - Block dangerous commands (/home, /spawn, /tp, /warp) for active war participants
 *      to prevent escape from the arena.
 *
 * Note: If WorldGuard is present, its region rules will handle arena protection.
 * This listener acts as a fallback and handles war-specific PvP rules in all cases.
 */
public class WarListener implements Listener {

    // Commands blocked for war participants
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "/home", "/sethome", "/spawn", "/tp", "/tpa", "/tpaccept",
            "/warp", "/back", "/rtp", "/randomtp", "/wild"
    );

    private final QuantumClan plugin;

    public WarListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── PvP enforcement ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        WarSession war = plugin.getWarManager().getActiveSession();

        if (war != null && war.isActive()) {
            // Inside war — only allow damage between participants of different clans
            UUID attackerUuid = attacker.getUniqueId();
            UUID victimUuid   = victim.getUniqueId();

            boolean attackerParticipates = war.isMemberParticipating(attackerUuid);
            boolean victimParticipates   = war.isMemberParticipating(victimUuid);

            // If neither is a participant, don't interfere
            if (!attackerParticipates && !victimParticipates) return;

            // If attacker is not in war, block them from hitting war players
            if (!attackerParticipates || !attackerParticipates) {
                event.setCancelled(true);
                return;
            }

            // Both in war — block friendly fire (same clan)
            String attackerClan = war.getClanIdForMember(attackerUuid);
            String victimClan   = war.getClanIdForMember(victimUuid);

            if (attackerClan != null && attackerClan.equals(victimClan)) {
                event.setCancelled(true);
                attacker.sendActionBar(plugin.getMiniMessage()
                        .deserialize("<red>Friendly fire dinonaktifkan selama war!"));
                return;
            }

            // Block attacking eliminated players
            if (!war.isMemberAlive(victimUuid)) {
                event.setCancelled(true);
                return;
            }

            // Valid war hit — allow through
            return;
        }

        // Outside war — check clan shield (attacker trying to hit shielded clan member)
        // Shield only blocks bounty — PvP between clans is allowed unless handled by WorldGuard
    }

    // ── Arena block protection (fallback — no WorldGuard) ─────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getHookManager().isWorldGuardEnabled()) return; // WG handles this

        WarSession war = plugin.getWarManager().getActiveSession();
        if (war == null || !war.isActive()) return;

        Player player = event.getPlayer();
        if (isInArena(player.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getMiniMessage()
                    .deserialize("<red>Tidak bisa merusak blok di area war!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getHookManager().isWorldGuardEnabled()) return;

        WarSession war = plugin.getWarManager().getActiveSession();
        if (war == null || !war.isActive()) return;

        Player player = event.getPlayer();
        if (isInArena(player.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getMiniMessage()
                    .deserialize("<red>Tidak bisa menempatkan blok di area war!"));
        }
    }

    // ── Command block ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        WarSession war = plugin.getWarManager().getActiveSession();
        if (war == null || !war.isActive()) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        if (!war.isMemberParticipating(uuid) || !war.isMemberAlive(uuid)) return;

        String cmd = event.getMessage().toLowerCase().split(" ")[0];

        if (BLOCKED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getMiniMessage()
                    .deserialize("<red>Command ini diblokir selama war berlangsung!"));
        }
    }

    // ── Arena check ───────────────────────────────────────────

    /**
     * Returns true if the given location is within the configured arena radius.
     */
    private boolean isInArena(Location location) {
        Location arenaCenter = plugin.getWarConfigManager().getArenaLocation();
        if (arenaCenter == null) return false;
        if (location.getWorld() == null) return false;
        if (!location.getWorld().equals(arenaCenter.getWorld())) return false;

        double radius = plugin.getWarConfigManager().getArenaRadius();
        return location.distanceSquared(arenaCenter) <= radius * radius;
    }
}