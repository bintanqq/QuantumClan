package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCoinsCommand implements SubCommand {
    private final QuantumClan plugin;

    public AdminCoinsCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, "error.unknown-subcommand");
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, "error.invalid-number");
            return;
        }
        if (amount <= 0) {
            plugin.sendMessage(sender, "coins.grant-invalid");
            return;
        }
        plugin.getCoinsProvider().grant(target.getUniqueId(), amount, "admin-grant")
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        plugin.sendMessage(sender, "admin.coins-grant-failed");
                        return;
                    }
                    String targetName = target.getName() != null ? target.getName() : args[0];
                    plugin.sendMessage(sender, "coins.grant-success",
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
