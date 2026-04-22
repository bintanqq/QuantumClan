package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.entity.Player;

/**
 * Handles /qclan vault
 *
 * Opens the Clan Vault GUI for the player.
 * Permission check: can-open-vault (roles.yml)
 */
public class VaultCommand {

    private final QuantumClan plugin;

    public VaultCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.vault.use")) {
            plugin.sendMessage(player, "error.no-permission");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-in-clan");
            return;
        }

        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-open-vault")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return;
        }

        plugin.getClanVaultManager().openVault(player, clan);
    }
}