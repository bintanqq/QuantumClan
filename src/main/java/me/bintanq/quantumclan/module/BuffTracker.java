package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active in-memory buff state per player:
 *  - Last home teleport timestamp (for cooldown)
 *  - Death protection flag (one-time use per purchase)
 *  - XP boost active flag
 *  - Announce cooldown timestamp per clan
 *  - Death protection cooldown per player
 *
 * Clan buffs that apply to all online members are applied immediately.
 * Offline member buffs are stored to DB via MemberDAO (handled by ClanShopManager).
 */
public class BuffTracker {

    private final QuantumClan plugin;

    // uuid → last home teleport millis
    private final Map<UUID, Long> lastHomeTeleport = new ConcurrentHashMap<>();

    // uuid → has death protection active
    private final Set<UUID> deathProtectionActive = ConcurrentHashMap.newKeySet();

    // uuid → last death protection use millis (cooldown)
    private final Map<UUID, Long> lastDeathProtectionUse = new ConcurrentHashMap<>();

    // clanId → last announce millis
    private final Map<String, Long> lastClanAnnounce = new ConcurrentHashMap<>();

    // clanId → xp boost expiry millis (0 = no boost)
    private final Map<String, Long> xpBoostExpiry = new ConcurrentHashMap<>();

    public BuffTracker(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Home teleport cooldown ────────────────────────────────

    public long getLastHomeTeleport(UUID uuid) {
        return lastHomeTeleport.getOrDefault(uuid, 0L);
    }

    public void setLastHomeTeleport(UUID uuid) {
        lastHomeTeleport.put(uuid, System.currentTimeMillis());
    }

    public boolean isOnHomeCooldown(UUID uuid) {
        long cooldownMillis = plugin.getConfigManager().getHomeTeleportCooldown() * 1000L;
        return (System.currentTimeMillis() - getLastHomeTeleport(uuid)) < cooldownMillis;
    }

    public long getHomeRemainingCooldown(UUID uuid) {
        long cooldownMillis = plugin.getConfigManager().getHomeTeleportCooldown() * 1000L;
        long elapsed = System.currentTimeMillis() - getLastHomeTeleport(uuid);
        return Math.max(0, (cooldownMillis - elapsed) / 1000L);
    }

    // ── Death protection ──────────────────────────────────────

    public boolean hasDeathProtection(UUID uuid) {
        return deathProtectionActive.contains(uuid);
    }

    public void grantDeathProtection(UUID uuid) {
        deathProtectionActive.add(uuid);
    }

    public void consumeDeathProtection(UUID uuid) {
        deathProtectionActive.remove(uuid);
        lastDeathProtectionUse.put(uuid, System.currentTimeMillis());
    }

    public boolean isOnDeathProtectionCooldown(UUID uuid) {
        long cooldownMillis = plugin.getConfigManager().getDeathProtectionCooldown() * 1000L;
        long last = lastDeathProtectionUse.getOrDefault(uuid, 0L);
        return (System.currentTimeMillis() - last) < cooldownMillis;
    }

    public long getDeathProtectionRemainingCooldown(UUID uuid) {
        long cooldownMillis = plugin.getConfigManager().getDeathProtectionCooldown() * 1000L;
        long last = lastDeathProtectionUse.getOrDefault(uuid, 0L);
        return Math.max(0, (cooldownMillis - (System.currentTimeMillis() - last)) / 1000L);
    }

    // ── Clan announce cooldown ────────────────────────────────

    public boolean isOnAnnounceCooldown(String clanId) {
        long cooldownMillis = plugin.getConfigManager().getAnnounceCooldown() * 1000L;
        long last = lastClanAnnounce.getOrDefault(clanId, 0L);
        return (System.currentTimeMillis() - last) < cooldownMillis;
    }

    public long getAnnounceRemainingCooldown(String clanId) {
        long cooldownMillis = plugin.getConfigManager().getAnnounceCooldown() * 1000L;
        long last = lastClanAnnounce.getOrDefault(clanId, 0L);
        return Math.max(0, (cooldownMillis - (System.currentTimeMillis() - last)) / 1000L);
    }

    public void setAnnounceCooldown(String clanId) {
        lastClanAnnounce.put(clanId, System.currentTimeMillis());
    }

    // ── XP Boost ─────────────────────────────────────────────

    public void activateXpBoost(String clanId, int durationSeconds) {
        xpBoostExpiry.put(clanId, System.currentTimeMillis() + durationSeconds * 1000L);
    }

    public boolean hasXpBoost(String clanId) {
        long expiry = xpBoostExpiry.getOrDefault(clanId, 0L);
        if (expiry == 0) return false;
        if (System.currentTimeMillis() > expiry) {
            xpBoostExpiry.remove(clanId);
            return false;
        }
        return true;
    }

    public double getXpMultiplier(String clanId) {
        return hasXpBoost(clanId) ? plugin.getConfigManager().getXpBoostMultiplier() : 1.0;
    }

    // ── Apply clan buff to online members ──────────────────────

    /**
     * Applies a potion effect to all online members of the clan immediately.
     * Offline members are handled by ClanShopManager via pending buffs DB table.
     */
    public void applyClanBuff(String clanId, PotionEffectType effectType,
                              int amplifier, int durationSeconds) {
        int ticks = durationSeconds * 20;
        plugin.getClanManager().getOnlineMembers(clanId).forEach(player ->
                player.addPotionEffect(new PotionEffect(effectType, ticks, amplifier, true, true, true)));
    }

    // ── Cleanup on logout ─────────────────────────────────────

    public void onPlayerQuit(UUID uuid) {
        lastHomeTeleport.remove(uuid);
        // deathProtection and cooldown intentionally kept for session persistence
        // (death protection is purchased — keep until used or until next session reset)
    }
}