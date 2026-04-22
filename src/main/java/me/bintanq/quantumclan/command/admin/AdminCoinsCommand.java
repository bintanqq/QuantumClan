// ════════════════════════════════════════════════════════════════════════════
// FILE: AdminCoinsCommand.java  — fix hardcoded "Gagal memberikan coins."
// ════════════════════════════════════════════════════════════════════════════
package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class AdminCoinsCommand {
    private final QuantumClan plugin;

    public AdminCoinsCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(Player player, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "error.unknown-subcommand");
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "error.invalid-number");
            return;
        }
        if (amount <= 0) {
            plugin.sendMessage(player, "coins.grant-invalid");
            return;
        }
        plugin.getCoinsProvider().grant(target.getUniqueId(), amount, "admin-grant")
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        // FIX: was hardcoded "Gagal memberikan coins."
                        plugin.sendMessage(player, "admin.coins-grant-failed");
                        return;
                    }
                    String targetName = target.getName() != null ? target.getName() : args[0];
                    plugin.sendMessage(player, "coins.grant-success",
                            "{player}", targetName,
                            "{value}", String.valueOf(amount));
                    Player online = Bukkit.getPlayer(target.getUniqueId());
                    if (online != null) {
                        plugin.sendMessage(online, "coins.grant-received",
                                "{value}", String.valueOf(amount));
                    }
                }));
    }
}