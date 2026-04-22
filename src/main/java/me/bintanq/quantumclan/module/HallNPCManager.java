package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.config.HallConfigManager.NpcPointConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages NPC interaction points for the Clan Hall.
 *
 * NPC Backend priority (auto-detected):
 *  1. Citizens (if present)
 *  2. ZNPCSPlus (if present)
 *  3. FancyNpcs (if present)
 *  4. Fallback: Invisible Armor Stand with interaction detection
 *
 * Each NPC point has a TYPE that determines which GUI opens on right-click.
 * Types: CLAN_SHOP, CONTRIBUTION_SHOP, COINS_SHOP, WAR_REGISTER,
 *        CLAN_INFO, UPGRADE, HALL_INFO
 */
public class HallNPCManager implements Listener {

    /** PDC key used to tag fallback armor stands */
    private static final String PDC_NPC_TYPE_KEY = "qc_hall_npc_type";

    private final QuantumClan plugin;
    private final NamespacedKey npcTypeKey;

    /** entityUUID → NPC type string (for fallback armor stands) */
    private final Map<UUID, String> armorStandNpcs = new ConcurrentHashMap<>();

    /** Loaded NPC point configs */
    private final List<NpcPointConfig> npcPoints = new ArrayList<>();

    // NPC backend availability flags
    private final boolean citizensAvailable;
    private final boolean znpcsPlusAvailable;
    private final boolean fancyNpcsAvailable;

    public HallNPCManager(QuantumClan plugin) {
        this.plugin = plugin;
        this.npcTypeKey = new NamespacedKey(plugin, PDC_NPC_TYPE_KEY);

        citizensAvailable  = plugin.getHookManager().isCitizensEnabled();
        znpcsPlusAvailable = plugin.getHookManager().isZNPCsPlusEnabled();
        fancyNpcsAvailable = plugin.getHookManager().isFancyNpcsEnabled();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Lifecycle ─────────────────────────────────────────────

    public void spawnAll() {
        npcPoints.clear();
        npcPoints.addAll(plugin.getHallConfigManager().loadNpcPoints());

        for (NpcPointConfig cfg : npcPoints) {
            spawnNpc(cfg);
        }
        plugin.getLogger().info("[ClanHall] Spawned " + npcPoints.size() + " NPC points.");
    }

    public void despawnAll() {
        if (citizensAvailable) {
            despawnCitizensNpcs();
        } else {
            // Despawn fallback armor stands
            for (UUID entityUuid : armorStandNpcs.keySet()) {
                for (var world : Bukkit.getWorlds()) {
                    for (Entity e : world.getEntities()) {
                        if (e.getUniqueId().equals(entityUuid)) {
                            e.remove();
                            break;
                        }
                    }
                }
            }
            armorStandNpcs.clear();
        }
        npcPoints.clear();
    }

    // ── Spawn single NPC ──────────────────────────────────────

    private void spawnNpc(NpcPointConfig cfg) {
        Location loc = cfg.toBukkitLocation();
        if (loc == null) {
            plugin.getLogger().warning("[ClanHall] Cannot spawn NPC '" + cfg.key
                    + "' — world '" + cfg.world + "' not loaded.");
            return;
        }

        if (citizensAvailable) {
            spawnCitizensNpc(cfg, loc);
        } else {
            // Fallback: armor stand
            spawnArmorStandNpc(cfg, loc);
        }
    }

    // ── Citizens backend ──────────────────────────────────────

    private void spawnCitizensNpc(NpcPointConfig cfg, Location loc) {
        try {
            net.citizensnpcs.api.CitizensAPI api = net.citizensnpcs.api.CitizensAPI.getNPCRegistry() != null
                    ? null : null; // just class-check done in HookManager
            // Use reflection to avoid hard compile dependency
            Class<?> citizensApiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = citizensApiClass.getMethod("getNPCRegistry").invoke(null);
            Class<?> registryClass = registry.getClass();
            Class<?> entityTypeClass = Class.forName("org.bukkit.entity.EntityType");

            Object npc = registryClass.getMethod("createNPC", entityTypeClass, String.class)
                    .invoke(registry, EntityType.PLAYER, cfg.name);

            Class<?> npcClass = npc.getClass();
            // Spawn at location
            npcClass.getMethod("spawn", Location.class).invoke(npc, loc);

            // Store NPC ID in config for later removal
            Object npcId = npcClass.getMethod("getId").invoke(npc);
            plugin.getHallConfigManager().saveNpcPoint(
                    cfg.key, cfg.type, cfg.name,
                    cfg.world, cfg.x, cfg.y, cfg.z,
                    cfg.yaw, cfg.pitch,
                    "citizens:" + npcId);

            plugin.getLogger().info("[ClanHall] Spawned Citizens NPC '" + cfg.name + "' id=" + npcId);
        } catch (Exception e) {
            plugin.getLogger().warning("[ClanHall] Citizens NPC spawn failed, using armor stand fallback: "
                    + e.getMessage());
            spawnArmorStandNpc(cfg, loc);
        }
    }

    private void despawnCitizensNpcs() {
        for (NpcPointConfig cfg : npcPoints) {
            if (cfg.entityId != null && cfg.entityId.startsWith("citizens:")) {
                try {
                    int npcId = Integer.parseInt(cfg.entityId.substring(9));
                    Class<?> citizensApiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
                    Object registry = citizensApiClass.getMethod("getNPCRegistry").invoke(null);
                    Object npc = registry.getClass().getMethod("getById", int.class).invoke(registry, npcId);
                    if (npc != null) {
                        npc.getClass().getMethod("destroy").invoke(npc);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[ClanHall] Failed to despawn Citizens NPC: " + e.getMessage());
                }
            }
        }
    }

    // ── Fallback: Armor Stand NPC ─────────────────────────────

    private void spawnArmorStandNpc(NpcPointConfig cfg, Location loc) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCustomName(plugin.getMiniMessage().stripTags(cfg.name));
            stand.setCustomNameVisible(true);
            stand.setSmall(false);
            stand.setArms(false);
            stand.setBasePlate(false);
            // Tag with NPC type
            stand.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, cfg.type);
            armorStandNpcs.put(stand.getUniqueId(), cfg.type);

            plugin.getLogger().info("[ClanHall] Spawned fallback armor stand NPC '"
                    + cfg.name + "' type=" + cfg.type);
        });
    }

    // ── Interaction handling ──────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        String npcType = armorStandNpcs.get(stand.getUniqueId());
        if (npcType == null) {
            // Double-check PDC in case of server restart cache miss
            npcType = stand.getPersistentDataContainer().get(npcTypeKey, PersistentDataType.STRING);
            if (npcType == null) return;
            armorStandNpcs.put(stand.getUniqueId(), npcType);
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        openNpcGui(player, npcType);
    }

    // ── GUI routing ───────────────────────────────────────────

    public void openNpcGui(Player player, String npcType) {
        switch (npcType.toUpperCase()) {
            case "CLAN_SHOP" -> {
                me.bintanq.quantumclan.model.Clan clan =
                        plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
                me.bintanq.quantumclan.gui.ClanShopGUI.open(plugin, player, clan);
            }
            case "CONTRIBUTION_SHOP" -> {
                if (plugin.getClanManager().getClanByPlayer(player.getUniqueId()) == null) {
                    plugin.sendMessage(player, "clan.not-in-clan"); return;
                }
                me.bintanq.quantumclan.gui.ContributionShopGUI.open(plugin, player);
            }
            case "COINS_SHOP" -> me.bintanq.quantumclan.gui.CoinsShopGUI.open(plugin, player);
            case "WAR_REGISTER" -> me.bintanq.quantumclan.gui.WarGUI.open(plugin, player);
            case "CLAN_INFO" -> {
                me.bintanq.quantumclan.model.Clan clan =
                        plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
                me.bintanq.quantumclan.gui.ClanInfoGUI.open(plugin, player, clan);
            }
            case "UPGRADE" -> {
                me.bintanq.quantumclan.model.Clan clan =
                        plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
                me.bintanq.quantumclan.gui.UpgradeGUI.open(plugin, player, clan);
            }
            case "HALL_INFO" -> me.bintanq.quantumclan.gui.ClanHallGUI.open(plugin, player);
            default -> plugin.getLogger().warning("[HallNPCManager] Unknown NPC type: " + npcType);
        }
    }

    // ── Admin commands: add/remove NPC ────────────────────────

    public void addNpcPoint(Player admin, String type, String name) {
        String key = type.toLowerCase() + "_" + System.currentTimeMillis();
        Location loc = admin.getLocation();

        plugin.getHallConfigManager().saveNpcPoint(
                key, type.toUpperCase(), name,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                null);

        NpcPointConfig cfg = new NpcPointConfig(key, type.toUpperCase(), name,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), null);
        npcPoints.add(cfg);
        spawnNpc(cfg);

        plugin.sendMessage(admin, "hall.npc-added", "{type}", type, "{name}", name);
    }

    public void removeNpcPoint(Player admin, String type) {
        npcPoints.removeIf(cfg -> cfg.type.equalsIgnoreCase(type));
        plugin.getHallConfigManager().removeNpcPoint(type.toLowerCase());
        // Despawn matching armor stands
        armorStandNpcs.entrySet().removeIf(entry -> {
            if (entry.getValue().equalsIgnoreCase(type)) {
                for (var world : Bukkit.getWorlds()) {
                    for (Entity e : world.getEntities()) {
                        if (e.getUniqueId().equals(entry.getKey())) {
                            e.remove();
                            break;
                        }
                    }
                }
                return true;
            }
            return false;
        });
        plugin.sendMessage(admin, "hall.npc-removed", "{type}", type);
    }

    public List<NpcPointConfig> listNpcPoints() {
        return java.util.Collections.unmodifiableList(npcPoints);
    }
}