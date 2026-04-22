package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dao.VaultDAO;
import me.bintanq.quantumclan.gui.ClanVaultGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Clan Vault system.
 *
 * BUG FIX 2: openVault now checks that the player is inside the Clan Hall
 *            (if the hall system is enabled). If the player is not in the hall,
 *            they receive the hall.no-access message.
 *
 * BUG FIX 4: On open, the slot count is always recalculated from the clan's
 *            CURRENT level so an upgraded vault is immediately reflected.
 */
public class ClanVaultManager {

    private final QuantumClan plugin;
    private final VaultDAO vaultDAO;

    private final Map<String, ItemStack[]> openVaults         = new ConcurrentHashMap<>();
    private final Map<String, UUID>        vaultOpener        = new ConcurrentHashMap<>();
    private final Map<String, String>      originalSerialized = new ConcurrentHashMap<>();
    private final Set<UUID>                adminInspectors    = ConcurrentHashMap.newKeySet();

    public ClanVaultManager(QuantumClan plugin, VaultDAO vaultDAO) {
        this.plugin   = plugin;
        this.vaultDAO = vaultDAO;
    }

    // ── Open vault ────────────────────────────────────────────

    /**
     * Opens the clan vault GUI for a player.
     *
     * BUG FIX 2: If the hall system is enabled and the vault feature requires
     *            players to be inside the hall, this check is enforced here.
     */
    public void openVault(Player player, Clan clan) {
        // BUG FIX 2: Hall-only access check
        if (plugin.getHallConfigManager().isEnabled()) {
            if (!plugin.getClanHallManager().isInsideHall(player.getUniqueId())) {
                plugin.sendMessage(player, "vault.hall-only");
                return;
            }
            // Also verify the clan actually has hall access
            if (!plugin.getClanHallManager().hasAccess(clan.getId())) {
                plugin.sendMessage(player, "hall.no-access");
                return;
            }
        }

        String clanId = clan.getId();
        UUID   uuid   = player.getUniqueId();

        // Concurrent access check
        if (vaultOpener.containsKey(clanId)) {
            UUID opener = vaultOpener.get(clanId);
            if (!opener.equals(uuid)) {
                Player openerPlayer = Bukkit.getPlayer(opener);
                String openerName = openerPlayer != null
                        ? openerPlayer.getName()
                        : plugin.getMessagesManager().get("vault.opener-unknown");
                plugin.sendMessage(player, "vault.in-use", "{player}", openerName);
                return;
            }
            closeVaultSilent(uuid, clanId);
        }

        // BUG FIX 4: always recalculate slot count from CURRENT level
        int slotCount = getVaultSlotCount(clan);

        if (openVaults.containsKey(clanId)) {
            doOpenGui(player, clan, openVaults.get(clanId), slotCount);
            return;
        }

        vaultDAO.loadVault(clanId, slotCount).thenAccept(contents ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String snapshot = VaultDAO.serialize(contents);
                    originalSerialized.put(clanId, snapshot != null ? snapshot : "");

                    openVaults.put(clanId, contents);
                    vaultOpener.put(clanId, uuid);

                    doOpenGui(player, clan, contents, slotCount);
                })
        );
    }

    public void openVaultAdmin(Player admin, Clan clan) {
        String clanId   = clan.getId();
        int    slotCount = getVaultSlotCount(clan);

        vaultDAO.loadVault(clanId, slotCount).thenAccept(contents ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    adminInspectors.add(admin.getUniqueId());
                    openVaults.put(clanId + ":admin:" + admin.getUniqueId(), contents);
                    doOpenAdminGui(admin, clan, contents, slotCount);
                })
        );
    }

    private void doOpenGui(Player player, Clan clan, ItemStack[] contents, int slotCount) {
        ClanVaultGUI gui = new ClanVaultGUI(plugin, player, clan, contents, false);
        player.openInventory(gui.build());
    }

    private void doOpenAdminGui(Player admin, Clan clan, ItemStack[] contents, int slotCount) {
        ClanVaultGUI gui = new ClanVaultGUI(plugin, admin, clan, contents, true);
        admin.openInventory(gui.build());
    }

    // ── Close vault ───────────────────────────────────────────

    public void onVaultClose(Player player, Clan clan) {
        String clanId = clan.getId();
        UUID   uuid   = player.getUniqueId();

        if (adminInspectors.remove(uuid)) {
            openVaults.remove(clanId + ":admin:" + uuid);
            return;
        }

        if (!uuid.equals(vaultOpener.get(clanId))) return;

        ItemStack[] current = openVaults.remove(clanId);
        vaultOpener.remove(clanId);

        if (current == null) return;

        String currentSerialized = VaultDAO.serialize(current);
        String original          = originalSerialized.remove(clanId);

        boolean isDirty = !java.util.Objects.equals(currentSerialized, original);

        if (isDirty && currentSerialized != null) {
            vaultDAO.saveVault(clanId, current).thenAccept(ok -> {
                if (!ok) {
                    plugin.getLogger().warning("[ClanVaultManager] Failed to save vault for clan " + clanId);
                }
            });
        }
    }

    private void closeVaultSilent(UUID uuid, String clanId) {
        openVaults.remove(clanId);
        vaultOpener.remove(clanId);
        originalSerialized.remove(clanId);
    }

    // ── Admin clear ───────────────────────────────────────────

    public CompletableFuture<Boolean> clearVault(String clanId) {
        if (vaultOpener.containsKey(clanId)) {
            UUID opener = vaultOpener.remove(clanId);
            openVaults.remove(clanId);
            originalSerialized.remove(clanId);
            Player p = opener != null ? Bukkit.getPlayer(opener) : null;
            if (p != null) {
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            }
        }
        return vaultDAO.clearVault(clanId);
    }

    // ── Slot count ────────────────────────────────────────────

    /**
     * BUG FIX 4: Always reads vault rows from CURRENT clan level.
     */
    public int getVaultSlotCount(Clan clan) {
        // Re-fetch the clan from cache to ensure we have latest level
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        int level = (latest != null) ? latest.getLevel() : clan.getLevel();
        int rows  = plugin.getConfigManager().getVaultRows(level);
        rows = Math.max(1, Math.min(6, rows));
        return rows * 9;
    }

    // ── Session queries ───────────────────────────────────────

    public boolean isVaultOpen(String clanId)   { return vaultOpener.containsKey(clanId); }
    public UUID    getVaultOpener(String clanId) { return vaultOpener.get(clanId); }
    public boolean isAdminInspector(UUID uuid)   { return adminInspectors.contains(uuid); }

    // ── Shutdown ──────────────────────────────────────────────

    public void shutdown() {
        for (Map.Entry<String, ItemStack[]> entry : openVaults.entrySet()) {
            String clanId = entry.getKey();
            if (clanId.contains(":admin:")) continue;

            ItemStack[] contents = entry.getValue();
            String currentSerialized = VaultDAO.serialize(contents);
            String original = originalSerialized.get(clanId);

            if (!java.util.Objects.equals(currentSerialized, original) && currentSerialized != null) {
                try {
                    vaultDAO.saveVault(clanId, contents).get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    plugin.getLogger().warning("[ClanVaultManager] Shutdown save failed for "
                            + clanId + ": " + e.getMessage());
                }
            }
        }
        openVaults.clear();
        vaultOpener.clear();
        originalSerialized.clear();
    }
}