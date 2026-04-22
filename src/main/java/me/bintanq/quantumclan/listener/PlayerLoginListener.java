package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dao.MemberDAO.PendingBuff;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

/**
 * Fires on player join to:
 *  1. Apply any pending clan buffs stored in clan_buffs_pending (offline buff delivery).
 *  2. Ensure a coins_balance row exists for new players.
 *
 * All DB reads are async; effect application is dispatched back to main thread.
 */
public class PlayerLoginListener implements Listener {

    private final QuantumClan plugin;

    public PlayerLoginListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        // Ensure coins balance row exists (no-op if already present)
        plugin.getCoinsDAO().ensureExists(uuid);

        // Load and apply pending buffs async
        plugin.getMemberDAO().loadPendingBuffs(uuid).thenAccept(buffs -> {
            if (buffs.isEmpty()) return;

            // Apply on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) return;

                for (PendingBuff buff : buffs) {
                    if (!buff.isStillValid()) {
                        // Expired while offline — delete and skip
                        plugin.getMemberDAO().deletePendingBuff(buff.id);
                        continue;
                    }

                    int remainingTicks = buff.remainingTicks();
                    if (remainingTicks <= 0) {
                        plugin.getMemberDAO().deletePendingBuff(buff.id);
                        continue;
                    }

                    PotionEffectType type = resolveEffect(buff.effectType);
                    if (type == null) {
                        plugin.getMemberDAO().deletePendingBuff(buff.id);
                        continue;
                    }

                    online.addPotionEffect(new PotionEffect(
                            type,
                            remainingTicks,
                            buff.amplifier,
                            true,   // ambient — subtle particles
                            true,   // show particles
                            true    // show icon
                    ));

                    // Remove from pending after applying
                    plugin.getMemberDAO().deletePendingBuff(buff.id);
                }
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Resolves a PotionEffectType from its name string.
     * Returns null if unknown (bad config / legacy data).
     */
    private PotionEffectType resolveEffect(String name) {
        if (name == null) return null;
        try {
            return PotionEffectType.getByName(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}