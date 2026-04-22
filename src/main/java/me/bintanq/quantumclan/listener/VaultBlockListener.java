package me.bintanq.quantumclan.listener;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * VaultBlockListener — intercepts right-clicks on the configured vault block
 * in the Clan Hall and opens that clan's vault GUI.
 *
 * The vault block location is set via /qclanadmin hall setvaultblock and
 * persisted in halls.yml under hall.vault-block.
 *
 * Only players inside the hall whose clan has access may interact.
 */
public class VaultBlockListener implements Listener {

    private final QuantumClan plugin;

    public VaultBlockListener(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if this block is the configured vault block
        Location vaultLoc = plugin.getHallConfigManager().getVaultBlockLocation();
        if (vaultLoc == null) return;
        if (!isSameBlock(block.getLocation(), vaultLoc)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!plugin.getConfigManager().isVaultEnabled()) {
            plugin.sendMessage(player, "vault.no-permission-open");
            return;
        }

        // Must be inside the hall region
        if (plugin.getHallConfigManager().isEnabled()
                && !plugin.getClanHallManager().isInsideHall(player.getUniqueId())) {
            plugin.sendMessage(player, "vault.hall-only");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        // Hall access check
        if (plugin.getHallConfigManager().isEnabled()
                && !plugin.getClanHallManager().hasAccess(clan.getId())) {
            plugin.sendMessage(player, "hall.no-access");
            return;
        }

        // Role permission
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-open-vault")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        plugin.getClanVaultManager().openVault(player, clan);
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().getName().equals(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}