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

import java.util.UUID;

/**
 * Handles player death for three systems:
 *
 * 1. DEATH PROTECTION — if the dead player has an active death protection buff,
 *    keep their inventory and cancel the item drops.
 *
 * 2. BOUNTY HEAD DROP — if the dead player has an active bounty, drop a custom
 *    player skull with PDC markers at the death location.
 *
 * 3. WAR ELIMINATION — if the death occurs during an active war and the player
 *    is a war participant, mark them eliminated and check if the war should end.
 */
public class PlayerDeathListener implements Listener {

    // PDC keys for bounty head identification
    public static final String PDC_BOUNTY_HEAD_ID  = "quantumclan_bounty_head_id";
    public static final String PDC_BOUNTY_ID        = "quantumclan_bounty_id";
    public static final String PDC_TARGET_UUID      = "quantumclan_target_uuid";

    private final QuantumClan plugin;
    private final MiniMessage mm;

    // NamespacedKeys (initialised once)
    private final NamespacedKey keyHeadId;
    private final NamespacedKey keyBountyId;
    private final NamespacedKey keyTargetUuid;

    public PlayerDeathListener(QuantumClan plugin) {
        this.plugin       = plugin;
        this.mm           = plugin.getMiniMessage();
        this.keyHeadId    = new NamespacedKey(plugin, PDC_BOUNTY_HEAD_ID);
        this.keyBountyId  = new NamespacedKey(plugin, PDC_BOUNTY_ID);
        this.keyTargetUuid = new NamespacedKey(plugin, PDC_TARGET_UUID);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID   uuid = dead.getUniqueId();

        // ── 1. Death Protection ───────────────────────────────
        if (plugin.getBuffTracker().hasDeathProtection(uuid)) {
            // Consume the protection (one-time use)
            plugin.getBuffTracker().consumeDeathProtection(uuid);

            // Keep inventory — clear drops and cancel experience loss
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Notify the player (will be shown after respawn)
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

            // Attribute kill to the killer if they are also a war participant
            Player killer = dead.getKiller();
            if (killer != null) {
                UUID killerUuid   = killer.getUniqueId();
                String killerClan = activeWar.getClanIdForMember(killerUuid);
                if (killerClan != null && !killerClan.equals(deadClanId)) {
                    activeWar.recordKill(killerUuid, killerClan);
                }
            }

            // Eliminate the dead player
            if (deadClanId != null) {
                activeWar.eliminateMember(uuid, deadClanId);
                plugin.sendMessage(dead, "war.eliminated");
            }

            // Check war-end conditions
            plugin.getWarManager().checkWarEnd(activeWar);
        }
    }

    // ── Bounty head helpers ───────────────────────────────────

    /**
     * Creates and drops a custom skull item tagged with PDC bounty markers.
     * The head is marked with a unique head_id (used for anti-dupe on submit).
     */
    private void dropBountyHead(PlayerDeathEvent event, Player dead, BountyEntry bounty) {
        // Generate a unique head ID for this drop
        String headId = UUID.randomUUID().toString();

        // Create skull item
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();

        if (meta == null) return;

        // Set skull owner to the dead player
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(dead.getUniqueId()));

        // Set display name
        meta.displayName(mm.deserialize(
                "<red>Kepala <yellow>" + dead.getName()
                        + "</yellow> <gray>[Bounty]"));

        // Add lore
        meta.lore(java.util.List.of(
                mm.deserialize("<gray>Submit dengan: <yellow>/qclan bounty submit"),
                mm.deserialize("<dark_gray>Head ID: <gray>" + headId)
        ));

        // Write PDC markers
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyHeadId,     PersistentDataType.STRING, headId);
        pdc.set(keyBountyId,   PersistentDataType.STRING, bounty.getId());
        pdc.set(keyTargetUuid, PersistentDataType.STRING, dead.getUniqueId().toString());

        skull.setItemMeta(meta);

        // Register head in DB (anti-dupe — head_id tracked in bounty_heads table)
        plugin.getBountyDAO().insertHead(headId, bounty.getId());

        // Mark bounty as head-claimed in memory and DB
        bounty.setHeadClaimed(true);
        plugin.getBountyDAO().markHeadClaimed(bounty.getId());

        // Drop at death location
        if (dead.getLocation().getWorld() != null) {
            dead.getLocation().getWorld().dropItemNaturally(dead.getLocation(), skull);
        }

        // Broadcast bounty head drop announcement
        String broadcast = plugin.getMessagesManager()
                .get("bounty.head-drop", "{player}", dead.getName());
        plugin.broadcast(broadcast);
    }
}