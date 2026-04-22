package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /qclan create
 * Flow: sends name prompt → waits chat → sends tag prompt → waits chat → creates clan.
 */
public class CreateCommand implements SubCommand {

    private final QuantumClan plugin;

    public CreateCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.clan.create")) return;
        if (plugin.getClanManager().isInClan(player.getUniqueId())) {
            plugin.sendMessage(player, "clan.create-already-in-clan"); return;
        }

        int maxName = plugin.getConfigManager().getMaxNameLength();
        int maxTag  = plugin.getConfigManager().getMaxTagLength();
        double cost = plugin.getConfigManager().getClanCreationCost();

        // Prompt: clan name
        plugin.getChatInputManager().prompt(player,
                plugin.getMessagesManager().get("clan.create-prompt-name", "{value}", maxName),
                name -> {
                    // Validate name
                    if (name.length() > maxName) {
                        plugin.sendMessage(player, "clan.create-name-invalid"); return;
                    }
                    if (!name.matches("[a-zA-Z0-9 _]+")) {
                        plugin.sendMessage(player, "clan.create-name-invalid"); return;
                    }
                    if (plugin.getClanManager().isClanNameTaken(name)) {
                        plugin.sendMessage(player, "clan.create-name-taken", "{clan}", name); return;
                    }

                    // Prompt: clan tag
                    plugin.getChatInputManager().prompt(player,
                            plugin.getMessagesManager().get("clan.create-prompt-tag", "{value}", maxTag),
                            tag -> {
                                if (tag.length() > maxTag) {
                                    plugin.sendMessage(player, "clan.create-tag-invalid"); return;
                                }
                                if (!tag.matches("[a-zA-Z0-9]+")) {
                                    plugin.sendMessage(player, "clan.create-tag-invalid"); return;
                                }
                                if (plugin.getClanManager().isClanTagTaken(tag)) {
                                    plugin.sendMessage(player, "clan.create-tag-taken", "{tag}", tag); return;
                                }

                                // Check balance
                                if (!player.hasPermission("quantumclan.bypass.cost")) {
                                    plugin.getEconomyProvider().has(player, cost).thenAccept(hasFunds -> {
                                        if (!hasFunds) {
                                            Bukkit.getScheduler().runTask(plugin, () ->
                                                    plugin.sendMessage(player, "clan.create-insufficient-funds",
                                                            "{value}", plugin.getEconomyProvider().format(cost)));
                                            return;
                                        }
                                        // Deduct and create
                                        plugin.getEconomyProvider().withdraw(player, cost).thenAccept(withdrawn -> {
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                if (!withdrawn) {
                                                    plugin.sendMessage(player, "clan.create-insufficient-funds",
                                                            "{value}", plugin.getEconomyProvider().format(cost));
                                                    return;
                                                }
                                                createClan(player, name, tag, cost);
                                            });
                                        });
                                    });
                                } else {
                                    createClan(player, name, tag, 0);
                                }
                            },
                            () -> plugin.sendMessage(player, "clan.create-cancelled"));
                },
                () -> plugin.sendMessage(player, "clan.create-cancelled"));
    }

    private void createClan(Player player, String name, String tag, double cost) {
        plugin.getClanManager().createClan(name, tag, player.getUniqueId())
                .thenAccept(clan -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clan == null) {
                            // Refund
                            if (cost > 0) plugin.getEconomyProvider().deposit(player, cost);
                            plugin.sendMessage(player, "error.unknown-subcommand");
                            return;
                        }
                        plugin.sendMessage(player, "clan.create-success",
                                "{clan}", clan.getName(),
                                "{tag}", clan.getTag());
                    });
                });
    }
}
