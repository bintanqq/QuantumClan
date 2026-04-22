package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dao.VaultDAO;
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
 * Responsibilities:
 * - Load vault contents from DB async, then open GUI on main thread
 * - Cache open vault sessions per clan
 * - Track dirty state — only save when contents changed
 * - Concurrent access protection: only one GUI session per clan at a time
 * - Save vault on GUI close (InventoryCloseEvent → dirty check → async save)
 * - Handle admin inspect (read-only) and admin clear
 */
public class ClanVaultManager {

    private final QuantumClan plugin;
    private final VaultDAO vaultDAO;

    /**
     * Per-clan cached contents while vault is open.
     * clanId → ItemStack[] (the live inventory content)
     */
    private final Map<String, ItemStack[]> openVaults = new ConcurrentHashMap<>();

    /**
     * clanId → UUID of the player who has the vault open.
     * Used to enforce one-opener-at-a-time.
     */
    private final Map<String, UUID> vaultOpener = new ConcurrentHashMap<>();

    /**
     * clanId → original snapshot at open time for dirty-state detection.
     */
    private final Map<String, String> originalSerialized = new ConcurrentHashMap<>();

    /**
     * UUIDs of players in admin inspect (read-only) mode.
     * These players' close events will NOT trigger a save.
     */
    private final Set<UUID> adminInspectors = ConcurrentHashMap.newKeySet();

    public ClanVaultManager(QuantumClan plugin, VaultDAO vaultDAO) {
        this.plugin = plugin;
        this.vaultDAO = vaultDAO;
    }

    // ── Open vault ────────────────────────────────────────────

    /**
     * Opens the clan vault GUI for a player.
     * Loads from DB async, then opens GUI on main thread.
     *
     * @param player The player opening the vault
     * @param clan   The clan whose vault to open
     */
    public void openVault(Player player, Clan clan) {
        String clanId = clan.getId();
        UUID uuid = player.getUniqueId();

        // Concurrent access check
        if (vaultOpener.containsKey(clanId)) {
            UUID opener = vaultOpener.get(clanId);
            if (!opener.equals(uuid)) {
                // Another member has it open
                Player openerPlayer = Bukkit.getPlayer(opener);
                String openerName = openerPlayer != null
                        ? openerPlayer.getName()
                        : plugin.getMessagesManager().get("vault.opener-unknown");
                plugin.sendMessage(player, "vault.in-use", "{player}", openerName);
                return;
            }
            // Same player re-opening — close existing and reopen
            closeVaultSilent(uuid, clanId);
        }

        int slotCount = getVaultSlotCount(clan);

        // Load from cache if already loaded for this clan (shouldn't happen after above,
        // but defensive check)
        if (openVaults.containsKey(clanId)) {
            doOpenGui(player, clan, openVaults.get(clanId), slotCount);
            return;
        }

        // Load from DB async
        vaultDAO.loadVault(clanId, slotCount).thenAccept(contents ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Snapshot for dirty-state detection
                    String snapshot = VaultDAO.serialize(contents);
                    originalSerialized.put(clanId, snapshot != null ? snapshot : "");

                    openVaults.put(clanId, contents);
                    vaultOpener.put(clanId, uuid);

                    doOpenGui(player, clan, contents, slotCount);
                })
        );
    }

    /**
     * Opens the vault in admin read-only (inspect) mode.
     * The GUI shows the same content but close does NOT save.
     */
    public void openVaultAdmin(Player admin, Clan clan) {
        String clanId = clan.getId();
        int slotCount = getVaultSlotCount(clan);

        vaultDAO.loadVault(clanId, slotCount).thenAccept(contents ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    adminInspectors.add(admin.getUniqueId());
                    // Open a read-only GUI — player is marked as admin inspector
                    // so close won't save
                    openVaults.put(clanId + ":admin:" + admin.getUniqueId(), contents);
                    doOpenAdminGui(admin, clan, contents, slotCount);
                })
        );
    }

    private void doOpenGui(Player player, Clan clan, ItemStack[] contents, int slotCount) {
        me.bintanq.quantumclan.gui.ClanVaultGUI gui =
                new me.bintanq.quantumclan.gui.ClanVaultGUI(plugin, player, clan, contents, false);
        player.openInventory(gui.build());
    }

    private void doOpenAdminGui(Player admin, Clan clan, ItemStack[] contents, int slotCount) {
        me.bintanq.quantumclan.gui.ClanVaultGUI gui =
                new me.bintanq.quantumclan.gui.ClanVaultGUI(plugin, admin, clan, contents, true);
        admin.openInventory(gui.build());
    }

    // ── Close vault ───────────────────────────────────────────

    /**
     * Called by InventoryCloseEvent (via ClanVaultGUI) when a player closes their vault.
     * Saves if dirty, then clears the session.
     */
    public void onVaultClose(Player player, Clan clan) {
        String clanId = clan.getId();
        UUID uuid = player.getUniqueId();

        // Admin inspector — don't save, just clean up
        if (adminInspectors.remove(uuid)) {
            openVaults.remove(clanId + ":admin:" + uuid);
            return;
        }

        // Only the registered opener can trigger a save
        if (!uuid.equals(vaultOpener.get(clanId))) return;

        ItemStack[] current = openVaults.remove(clanId);
        vaultOpener.remove(clanId);

        if (current == null) return;

        // Dirty-state check
        String currentSerialized = VaultDAO.serialize(current);
        String original = originalSerialized.remove(clanId);

        boolean isDirty = !java.util.Objects.equals(currentSerialized, original);

        if (isDirty && currentSerialized != null) {
            vaultDAO.saveVault(clanId, current).thenAccept(ok -> {
                if (!ok) {
                    plugin.getLogger().warning("[ClanVaultManager] Failed to save vault for clan "
                            + clanId);
                }
            });
        }
    }

    /**
     * Silent close — used internally when re-opening vault for same player.
     * Does NOT save (avoids partial-state saves).
     */
    private void closeVaultSilent(UUID uuid, String clanId) {
        openVaults.remove(clanId);
        vaultOpener.remove(clanId);
        originalSerialized.remove(clanId);
    }

    // ── Admin clear ───────────────────────────────────────────

    /**
     * Clears all vault contents for a clan.
     * If vault is currently open, also clears the in-memory cache.
     */
    public CompletableFuture<Boolean> clearVault(String clanId) {
        // If vault is open, close and evict cache
        if (vaultOpener.containsKey(clanId)) {
            UUID opener = vaultOpener.remove(clanId);
            openVaults.remove(clanId);
            originalSerialized.remove(clanId);
            // Force-close the inventory if player is online
            Player p = opener != null ? Bukkit.getPlayer(opener) : null;
            if (p != null) {
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            }
        }
        return vaultDAO.clearVault(clanId);
    }

    // ── Slot count ────────────────────────────────────────────

    /**
     * Returns the number of STORAGE slots for the clan's vault
     * based on vault-rows from ConfigManager.
     * Storage slots = rows × 9 (navigation bar is separate, handled in GUI).
     */
    public int getVaultSlotCount(Clan clan) {
        int rows = plugin.getConfigManager().getVaultRows(clan.getLevel());
        // Clamp 1-6
        rows = Math.max(1, Math.min(6, rows));
        return rows * 9;
    }

    // ── Session queries ───────────────────────────────────────

    /** Returns true if any player has the vault open for this clan. */
    public boolean isVaultOpen(String clanId) {
        return vaultOpener.containsKey(clanId);
    }

    /** Returns the UUID of the player with the vault open, or null. */
    public UUID getVaultOpener(String clanId) {
        return vaultOpener.get(clanId);
    }

    /** Returns whether a player is in admin inspect mode. */
    public boolean isAdminInspector(UUID uuid) {
        return adminInspectors.contains(uuid);
    }

    // ── Shutdown ──────────────────────────────────────────────

    /**
     * Called on plugin disable. Force-saves all open vaults synchronously.
     * (DB thread pool is still alive at this point — shutdown happens after.)
     */
    public void shutdown() {
        for (Map.Entry<String, ItemStack[]> entry : openVaults.entrySet()) {
            String clanId = entry.getKey();
            if (clanId.contains(":admin:")) continue; // Skip admin inspect sessions

            ItemStack[] contents = entry.getValue();
            String currentSerialized = VaultDAO.serialize(contents);
            String original = originalSerialized.get(clanId);

            if (!java.util.Objects.equals(currentSerialized, original)
                    && currentSerialized != null) {
                // Block briefly to ensure save completes before DB pool shuts down
                try {
                    vaultDAO.saveVault(clanId, contents).get(3,
                            java.util.concurrent.TimeUnit.SECONDS);
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