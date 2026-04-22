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
 * Access is now gated at the point of entry:
 *  - VaultBlockListener handles block-click access (requires being inside hall)
 *  - /qclan vault command is removed (vault is block-only)
 *  - Admin /qclanadmin vault inspect|clear still works regardless of location
 *
 * Slot count recalculated from current clan level on every open (BUG FIX 4).
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

    // ── Open vault (called by VaultBlockListener) ─────────────

    /**
     * Opens the clan vault GUI for a player.
     * Access and hall checks are performed by the caller (VaultBlockListener).
     * This method only handles concurrency and GUI opening.
     */
    public void openVault(Player player, Clan clan) {
        String clanId = clan.getId();
        UUID   uuid   = player.getUniqueId();

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
                }));
    }

    // ── Admin inspect ─────────────────────────────────────────

    public void openVaultAdmin(Player admin, Clan clan) {
        String clanId   = clan.getId();
        int    slotCount = getVaultSlotCount(clan);

        vaultDAO.loadVault(clanId, slotCount).thenAccept(contents ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    adminInspectors.add(admin.getUniqueId());
                    openVaults.put(clanId + ":admin:" + admin.getUniqueId(), contents);
                    ClanVaultGUI gui = new ClanVaultGUI(plugin, admin, clan, contents, true);
                    admin.openInventory(gui.build());
                }));
    }

    private void doOpenGui(Player player, Clan clan, ItemStack[] contents, int slotCount) {
        ClanVaultGUI gui = new ClanVaultGUI(plugin, player, clan, contents, false);
        player.openInventory(gui.build());
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
                if (!ok) plugin.getLogger().warning(
                        "[ClanVaultManager] Failed to save vault for clan " + clanId);
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
            if (opener != null) {
                Player p = Bukkit.getPlayer(opener);
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            }
        }
        return vaultDAO.clearVault(clanId);
    }

    // ── Slot count ────────────────────────────────────────────

    public int getVaultSlotCount(Clan clan) {
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        int level = (latest != null) ? latest.getLevel() : clan.getLevel();
        int rows  = Math.max(1, Math.min(6, plugin.getConfigManager().getVaultRows(level)));
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