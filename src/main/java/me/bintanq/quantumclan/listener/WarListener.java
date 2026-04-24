package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.manager.SocialManager;
import me.bintanq.quantumclan.model.Clan;
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

public class WarListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "/home", "/sethome", "/spawn", "/tp", "/tpa", "/tpaccept",
            "/warp", "/back", "/rtp", "/randomtp", "/wild"
    );

    private final QuantumClan plugin;

    public WarListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        UUID attackerUuid = attacker.getUniqueId();
        UUID victimUuid   = victim.getUniqueId();

        // ── War-specific friendly fire ────────────────────────
        WarSession war = plugin.getWarManager().getActiveSession();

        if (war != null && war.isActive()) {
            boolean attackerParticipates = war.isMemberParticipating(attackerUuid);
            boolean victimParticipates   = war.isMemberParticipating(victimUuid);

            if (!attackerParticipates && !victimParticipates) {
                // Neither in war — fall through to global checks below
            } else {
                if (!attackerParticipates || !victimParticipates) {
                    event.setCancelled(true);
                    return;
                }

                String attackerClan = war.getClanIdForMember(attackerUuid);
                String victimClan   = war.getClanIdForMember(victimUuid);

                if (attackerClan != null && attackerClan.equals(victimClan)) {
                    event.setCancelled(true);
                    attacker.sendActionBar(plugin.getMiniMessage().deserialize(
                            plugin.getMessagesManager().get("war.friendly-fire")));
                    return;
                }

                if (!war.isMemberAlive(victimUuid)) {
                    event.setCancelled(true);
                    return;
                }
                // War PvP is allowed — skip global checks
                return;
            }
        }

        // ── Global: Clan friendly fire ────────────────────────
        SocialManager social = plugin.getSocialManager();

        Clan attackerClan = plugin.getClanManager().getClanByPlayer(attackerUuid);
        Clan victimClan   = plugin.getClanManager().getClanByPlayer(victimUuid);

        if (attackerClan != null && victimClan != null) {
            // Same clan friendly fire
            if (attackerClan.getId().equals(victimClan.getId()) && social.isClanFriendlyFireDisabled()) {
                event.setCancelled(true);
                attacker.sendActionBar(plugin.getMiniMessage().deserialize(
                        plugin.getMessagesManager().get("social.friendly-fire-clan")));
                return;
            }
            // Allied clan friendly fire
            if (social.isAllyFriendlyFireDisabled()
                    && social.areAllied(attackerClan.getId(), victimClan.getId())) {
                event.setCancelled(true);
                attacker.sendActionBar(plugin.getMiniMessage().deserialize(
                        plugin.getMessagesManager().get("social.friendly-fire-ally")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getHookManager().isWorldGuardEnabled()) return;

        WarSession war = plugin.getWarManager().getActiveSession();
        if (war == null || !war.isActive()) return;

        Player player = event.getPlayer();
        if (isInArena(player.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(plugin.getMiniMessage().deserialize(
                    plugin.getMessagesManager().get("war.arena-no-break")));
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
            player.sendActionBar(plugin.getMiniMessage().deserialize(
                    plugin.getMessagesManager().get("war.arena-no-build")));
        }
    }

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
            player.sendActionBar(plugin.getMiniMessage().deserialize(
                    plugin.getMessagesManager().get("war.command-blocked")));
        }
    }

    private boolean isInArena(Location location) {
        Location arenaCenter = plugin.getWarConfigManager().getArenaLocation();
        if (arenaCenter == null) return false;
        if (location.getWorld() == null) return false;
        if (!location.getWorld().equals(arenaCenter.getWorld())) return false;
        double radius = plugin.getWarConfigManager().getArenaRadius();
        return location.distanceSquared(arenaCenter) <= radius * radius;
    }
}