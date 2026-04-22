package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.HallAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager for the Clan Hall System.
 *
 * Responsibilities:
 *  - In-memory cache of clans with active hall access
 *  - Grant / revoke access (syncs to DB via HallDAO)
 *  - Passive buff application for players inside the hall region
 *  - Discount calculation injection point for shops and upgrades
 *  - Inside-region tracking per player
 *  - Hourly expiry cleanup scheduler
 *  - Buff refresh scheduler (every N seconds)
 */
public class ClanHallManager {

    private final QuantumClan plugin;

    /** clanId → HallAccess (only clans with active, non-expired access) */
    private final Map<String, HallAccess> accessCache = new ConcurrentHashMap<>();

    /** Players currently inside the hall region */
    private final Set<UUID> insideHall = ConcurrentHashMap.newKeySet();

    /** Corner selection for /qclanadmin hall setregion (per-admin UUID) */
    private final Map<UUID, Location> pendingCorner1 = new ConcurrentHashMap<>();

    private int buffRefreshTaskId = -1;
    private int expiryTaskId      = -1;

    public ClanHallManager(QuantumClan plugin) {
        this.plugin = plugin;
    }

    // ── Startup ───────────────────────────────────────────────

    public void init() {
        if (!plugin.getHallConfigManager().isEnabled()) {
            plugin.getLogger().info("[ClanHall] Hall system disabled in halls.yml.");
            return;
        }

        // Load active access records from DB
        plugin.getHallDAO().loadAllActive().thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (HallAccess access : list) {
                    if (access.isValid()) {
                        accessCache.put(access.getClanId(), access);
                    }
                }
                plugin.getLogger().info("[ClanHall] Loaded " + accessCache.size()
                        + " active hall access records.");
            });
        });

        startBuffRefreshScheduler();
        startExpiryScheduler();
    }

    public void shutdown() {
        if (buffRefreshTaskId != -1) Bukkit.getScheduler().cancelTask(buffRefreshTaskId);
        if (expiryTaskId != -1) Bukkit.getScheduler().cancelTask(expiryTaskId);
        insideHall.clear();
    }

    // ── Access control ────────────────────────────────────────

    /**
     * Returns true if the given clan currently has valid hall access.
     * Cache-first; does NOT query the database.
     */
    public boolean hasAccess(String clanId) {
        HallAccess access = accessCache.get(clanId);
        if (access == null) return false;
        if (!access.isValid()) {
            accessCache.remove(clanId);
            return false;
        }
        return true;
    }

    /**
     * Grants hall access to the clan, persisting to DB.
     * Returns CompletableFuture<Boolean> — false on DB failure.
     */
    public CompletableFuture<Boolean> grantAccess(String clanId) {
        Instant expiresAt = null;
        if (!plugin.getHallConfigManager().isPermanentMode()) {
            int days = plugin.getHallConfigManager().getDurationDays();
            expiresAt = Instant.now().plusSeconds(days * 86400L);
        }

        final Instant finalExpiry = expiresAt;
        return plugin.getHallDAO().insertAccess(clanId, finalExpiry).thenApply(ok -> {
            if (ok) {
                HallAccess access = new HallAccess(clanId, Instant.now(), finalExpiry, true);
                accessCache.put(clanId, access);
            }
            return ok;
        });
    }

    /**
     * Revokes hall access for the clan.
     */
    public CompletableFuture<Boolean> revokeAccess(String clanId) {
        accessCache.remove(clanId);
        return plugin.getHallDAO().revokeAccess(clanId);
    }

    /** Returns all clan IDs currently in the access cache. */
    public Set<String> getAccessClanIds() {
        return accessCache.keySet();
    }

    // ── Inside-hall tracking ──────────────────────────────────

    /**
     * Called by HallListener when a player enters the hall region.
     * Applies passive buffs immediately.
     */
    public void onPlayerEnterHall(Player player) {
        insideHall.add(player.getUniqueId());
        applyPassiveBuffs(player);
        plugin.sendMessage(player, "hall.enter");
    }

    /**
     * Called by HallListener when a player exits the hall region.
     * Removes passive buffs.
     */
    public void onPlayerExitHall(Player player) {
        insideHall.remove(player.getUniqueId());
        removePassiveBuffs(player);
        plugin.sendMessage(player, "hall.exit");
    }

    public void onPlayerQuit(UUID uuid) {
        insideHall.remove(uuid);
    }

    /** Returns true if the player is currently inside the hall region. */
    public boolean isInsideHall(UUID uuid) {
        return insideHall.contains(uuid);
    }

    /** Returns an unmodifiable snapshot of players currently inside. */
    public Set<UUID> getPlayersInsideHall() {
        return java.util.Collections.unmodifiableSet(insideHall);
    }

    // ── Passive buffs ─────────────────────────────────────────

    private void applyPassiveBuffs(Player player) {
        if (!plugin.getHallConfigManager().isPassiveBuffsEnabled()) return;

        int refreshSec = plugin.getHallConfigManager().getBuffRefreshIntervalSeconds();
        // Apply for double the refresh interval to ensure they don't expire between refreshes
        int durationTicks = (refreshSec * 2 + 5) * 20;

        applyBuffIfEnabled(player, "regeneration", PotionEffectType.REGENERATION, durationTicks);
        applyBuffIfEnabled(player, "speed",        PotionEffectType.SPEED,        durationTicks);
        applyBuffIfEnabled(player, "strength",     PotionEffectType.STRENGTH,     durationTicks);
        applyBuffIfEnabled(player, "haste",        PotionEffectType.HASTE,        durationTicks);
    }

    private void applyBuffIfEnabled(Player player, String buffName,
                                    PotionEffectType type, int durationTicks) {
        if (!plugin.getHallConfigManager().isBuffEnabled(buffName)) return;
        int amplifier = plugin.getHallConfigManager().getBuffAmplifier(buffName);
        boolean ambient = plugin.getHallConfigManager().isBuffAmbient(buffName);
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, !ambient, true));
    }

    private void removePassiveBuffs(Player player) {
        if (!plugin.getHallConfigManager().isPassiveBuffsEnabled()) return;
        removeBuffIfEnabled(player, "regeneration", PotionEffectType.REGENERATION);
        removeBuffIfEnabled(player, "speed",        PotionEffectType.SPEED);
        removeBuffIfEnabled(player, "strength",     PotionEffectType.STRENGTH);
        removeBuffIfEnabled(player, "haste",        PotionEffectType.HASTE);
    }

    private void removeBuffIfEnabled(Player player, String buffName, PotionEffectType type) {
        if (plugin.getHallConfigManager().isBuffEnabled(buffName)) {
            player.removePotionEffect(type);
        }
    }

    private void startBuffRefreshScheduler() {
        if (!plugin.getHallConfigManager().isPassiveBuffsEnabled()) return;
        int intervalSec = plugin.getHallConfigManager().getBuffRefreshIntervalSeconds();
        long intervalTicks = Math.max(20L, intervalSec * 20L);
        buffRefreshTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : insideHall) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    applyPassiveBuffs(p);
                }
            }
        }, intervalTicks, intervalTicks).getTaskId();
    }

    // ── Discount injection ────────────────────────────────────

    /**
     * Returns the discounted cost for a player. If the player is inside the
     * hall and their clan has access, applies the configured discount.
     *
     * @param player   The player making a purchase
     * @param baseCost The original cost
     * @param shopType "clan-shop", "contribution-shop", "coins-shop", or "upgrade"
     * @return Adjusted cost (may be same as baseCost if no discount applies)
     */
    public long applyDiscount(Player player, long baseCost, String shopType) {
        if (!plugin.getHallConfigManager().isDiscountsEnabled()) return baseCost;
        if (!isInsideHall(player.getUniqueId())) return baseCost;

        // Check clan has hall access
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !hasAccess(clan.getId())) return baseCost;

        double multiplier = plugin.getHallConfigManager().getDiscountMultiplier(shopType);
        return Math.max(0L, Math.round(baseCost * multiplier));
    }

    /**
     * Overload for int cost (contribution points).
     */
    public int applyDiscount(Player player, int baseCost, String shopType) {
        return (int) applyDiscount(player, (long) baseCost, shopType);
    }

    /**
     * Returns the discount percent for display purposes (0-100).
     */
    public int getDiscountPercent(Player player, String shopType) {
        if (!plugin.getHallConfigManager().isDiscountsEnabled()) return 0;
        if (!isInsideHall(player.getUniqueId())) return 0;
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !hasAccess(clan.getId())) return 0;
        return plugin.getHallConfigManager().getDiscountPercent(shopType);
    }

    // ── Expiry cleanup ────────────────────────────────────────

    private void startExpiryScheduler() {
        // Run every hour (72000 ticks)
        expiryTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getHallDAO().cleanExpired().thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Remove expired entries from cache
                        accessCache.entrySet().removeIf(e -> !e.getValue().isValid());
                        plugin.getLogger().info("[ClanHall] Expired access records cleaned.");
                    }));
        }, 72000L, 72000L).getTaskId();
    }

    // ── Region corner selection (admin setregion) ─────────────

    public boolean hasPendingCorner1(UUID adminUuid) {
        return pendingCorner1.containsKey(adminUuid);
    }

    public void setPendingCorner1(UUID adminUuid, Location loc) {
        pendingCorner1.put(adminUuid, loc);
    }

    public Location consumePendingCorner1(UUID adminUuid) {
        return pendingCorner1.remove(adminUuid);
    }

    // ── Purchase flow ─────────────────────────────────────────

    /**
     * Processes a hall access purchase for a clan.
     * Deducts from clan treasury and grants access.
     *
     * @param player The player triggering the purchase (must be in clan)
     * @return CompletableFuture<Boolean> — true on success
     */
    public CompletableFuture<Boolean> purchaseAccess(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return CompletableFuture.completedFuture(false);
        }

        if (hasAccess(clan.getId())) {
            plugin.sendMessage(player, "hall.already-has-access");
            return CompletableFuture.completedFuture(false);
        }

        long cost = plugin.getHallConfigManager().getPurchaseCost();

        if (!clan.hasMoney(cost)) {
            plugin.sendMessage(player, "hall.insufficient-funds",
                    "{cost}", plugin.getEconomyProvider().format(cost),
                    "{balance}", plugin.getEconomyProvider().format(clan.getMoney()));
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getClanManager().spendClanMoney(clan.getId(), cost)
                .thenCompose(spent -> {
                    if (!spent) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return grantAccess(clan.getId()).thenApply(granted -> {
                        if (!granted) {
                            // Rollback treasury
                            plugin.getClanManager().depositMoney(player.getUniqueId(), cost);
                        }
                        return granted;
                    });
                });
    }
}