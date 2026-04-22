package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.BountyEntry;
import me.bintanq.quantumclan.model.WarSession;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Handles player death for three systems:
 *
 * 1. DEATH PROTECTION — keep inventory if buff is active.
 * 2. BOUNTY HEAD DROP — drop a custom skull tagged with PDC markers.
 *    All head text is loaded from messages.yml (no hardcoded strings).
 * 3. WAR ELIMINATION — mark player eliminated, check war-end conditions.
 */
public class PlayerDeathListener implements Listener {

    public static final String PDC_BOUNTY_HEAD_ID = "quantumclan_bounty_head_id";
    public static final String PDC_BOUNTY_ID       = "quantumclan_bounty_id";
    public static final String PDC_TARGET_UUID     = "quantumclan_target_uuid";

    private final QuantumClan plugin;
    private final MiniMessage mm;

    private final NamespacedKey keyHeadId;
    private final NamespacedKey keyBountyId;
    private final NamespacedKey keyTargetUuid;

    public PlayerDeathListener(QuantumClan plugin) {
        this.plugin        = plugin;
        this.mm            = plugin.getMiniMessage();
        this.keyHeadId     = new NamespacedKey(plugin, PDC_BOUNTY_HEAD_ID);
        this.keyBountyId   = new NamespacedKey(plugin, PDC_BOUNTY_ID);
        this.keyTargetUuid = new NamespacedKey(plugin, PDC_TARGET_UUID);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID   uuid = dead.getUniqueId();

        // ── 1. Death Protection ───────────────────────────────
        if (plugin.getBuffTracker().hasDeathProtection(uuid)) {
            plugin.getBuffTracker().consumeDeathProtection(uuid);

            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player respawned = Bukkit.getPlayer(uuid);
                if (respawned != null) {
                    plugin.sendMessage(respawned, "shop.death-protection-used");
                }
            }, 5L);
        }

        // ── 2. Bounty Head Drop ───────────────────────────────
        BountyEntry bounty = plugin.getBountyManager().getActiveBountyForTarget(uuid);
        if (bounty != null && bounty.isActive() && !bounty.isHeadClaimed()) {
            dropBountyHead(event, dead, bounty);
        }

        // ── 3. War Elimination ────────────────────────────────
        WarSession activeWar = plugin.getWarManager().getActiveSession();
        if (activeWar != null && activeWar.isActive()
                && activeWar.isMemberParticipating(uuid)
                && activeWar.isMemberAlive(uuid)) {

            String deadClanId = activeWar.getClanIdForMember(uuid);

            Player killer = dead.getKiller();
            if (killer != null) {
                UUID killerUuid   = killer.getUniqueId();
                String killerClan = activeWar.getClanIdForMember(killerUuid);
                if (killerClan != null && !killerClan.equals(deadClanId)) {
                    activeWar.recordKill(killerUuid, killerClan);
                }
            }

            if (deadClanId != null) {
                activeWar.eliminateMember(uuid, deadClanId);
                plugin.sendMessage(dead, "war.eliminated");
            }

            plugin.getWarManager().checkWarEnd(activeWar);
        }
    }

    // ── Bounty head helpers ───────────────────────────────────

    private void dropBountyHead(PlayerDeathEvent event, Player dead, BountyEntry bounty) {
        String headId = UUID.randomUUID().toString();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();

        if (meta == null) return;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(dead.getUniqueId()));

        // Name from messages.yml — no hardcoded strings
        String headName = plugin.getMessagesManager()
                .get("gui.bounty-head-name", "{player}", dead.getName());
        meta.displayName(mm.deserialize("<!italic>" + headName));

        // Lore from messages.yml
        String loreLine1 = plugin.getMessagesManager().get("gui.bounty-head-lore1");
        String loreLine2 = plugin.getMessagesManager()
                .get("gui.bounty-head-lore2", "{id}", headId);
        meta.lore(List.of(
                mm.deserialize("<!italic>" + loreLine1),
                mm.deserialize("<!italic>" + loreLine2)
        ));

        // PDC markers
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyHeadId,     PersistentDataType.STRING, headId);
        pdc.set(keyBountyId,   PersistentDataType.STRING, bounty.getId());
        pdc.set(keyTargetUuid, PersistentDataType.STRING, dead.getUniqueId().toString());

        skull.setItemMeta(meta);

        // Register head in DB (anti-dupe)
        plugin.getBountyDAO().insertHead(headId, bounty.getId());

        // Mark bounty head-claimed
        bounty.setHeadClaimed(true);
        plugin.getBountyDAO().markHeadClaimed(bounty.getId());

        // Drop at death location
        if (dead.getLocation().getWorld() != null) {
            dead.getLocation().getWorld().dropItemNaturally(dead.getLocation(), skull);
        }

        // Broadcast from messages.yml
        String broadcast = plugin.getMessagesManager()
                .get("bounty.head-drop", "{player}", dead.getName());
        plugin.broadcast(broadcast);
    }
}