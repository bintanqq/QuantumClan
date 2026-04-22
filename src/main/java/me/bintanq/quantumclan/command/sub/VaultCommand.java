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
public class VaultCommand implements SubCommand {

    private final QuantumClan plugin;

    public VaultCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!plugin.checkPerm(player, "quantumclan.vault.use")) return;

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;

        if (!plugin.checkRole(player, "can-open-vault")) return;

        plugin.getClanVaultManager().openVault(player, clan);
    }
}
