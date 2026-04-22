package me.bintanq.quantumclan.command;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.admin.*;
import me.bintanq.quantumclan.command.sub.SubCommand;
import me.bintanq.quantumclan.command.sub.VaultCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Routes /qclanadmin <subcommand> to the appropriate admin handler.
 * All help text comes from messages.yml — no hardcoded color strings.
 */
public class QClanAdminCommand implements CommandExecutor, TabCompleter {

    private final QuantumClan plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public QClanAdminCommand(QuantumClan plugin) {
        this.plugin = plugin;
        register("reload",   new AdminReloadCommand(plugin));
        register("coins",    new AdminCoinsCommand(plugin));
        register("setarena", new AdminSetArenaCommand(plugin));
        register("war",      new AdminWarCommand(plugin));
        register("clan",     new AdminClanCommand(plugin));
        register("hall",     new AdminHallCommand(plugin));
        register("vault",    new AdminVaultCommand(plugin));
    }

    private void register(String name, SubCommand cmd) {
        subCommands.put(name.toLowerCase(), cmd);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return true;
        }
        if (!player.hasPermission("quantumclan.admin")) {
            plugin.sendMessage(player, "error.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(player);
            return true;
        }

        String subName = args[0].toLowerCase();

        if (subName.equals("help")) {
            sendAdminHelp(player);
            return true;
        }

        if (subName.equals("give")) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            if (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("coins")) {
                SubCommand coinsCmd = subCommands.get("coins");
                if (coinsCmd != null) {
                    coinsCmd.execute(player, Arrays.copyOfRange(subArgs, 1, subArgs.length));
                }
            } else {
                plugin.sendMessage(player, "admin.help-give-usage");
            }
            return true;
        }

        SubCommand cmd = subCommands.get(subName);
        if (cmd != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            cmd.execute(player, subArgs);
        } else {
            sendAdminHelp(player);
        }
        return true;
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private void sendAdminHelp(Player player) {
        var msg = plugin.getMessagesManager();
        plugin.sendRaw(player, msg.get("help.admin-header"));

        String entry = msg.getRaw("help.admin-entry");
        if (entry == null) entry = "<aqua>/qclanadmin {cmd} <dark_gray>- <gray>{desc}";

        String[][] cmds = {
                { "reload",                                    "quantumclan.admin.reload",   msg.get("help.admin-cmd-reload") },
                { "give coins <player> <amount>",              "quantumclan.admin.coins",    msg.get("help.admin-cmd-give") },
                { "setarena",                                  "quantumclan.admin.setarena", msg.get("help.admin-cmd-setarena") },
                { "war <start|end>",                           "quantumclan.admin.war",      msg.get("help.admin-cmd-war") },
                { "clan <info|delete|setlevel|setreputation>", "quantumclan.admin.clan",     msg.get("help.admin-cmd-clan") },
                { "hall <setregion|grant|revoke|...>",         "quantumclan.admin",          msg.get("help.admin-cmd-hall") },
                { "vault <clear|inspect>",                     "quantumclan.admin.vault",    msg.get("help.admin-cmd-vault") },
        };

        final String finalEntry = entry;
        for (String[] triple : cmds) {
            if (player.hasPermission(triple[1])) {
                plugin.sendRaw(player, finalEntry
                        .replace("{cmd}", triple[0])
                        .replace("{desc}", triple[2] != null ? triple[2] : ""));
            }
        }

        plugin.sendRaw(player, msg.get("help.footer"));
    }

    // ── Tab complete ─────────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("quantumclan.admin")) return List.of();

        if (args.length == 1) {
            return List.of("reload", "give", "setarena", "war", "clan", "hall", "vault", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "give"   -> List.of("coins");
                case "war"    -> List.of("start", "end");
                case "clan"   -> List.of("info", "delete", "setlevel", "setreputation");
                case "hall"   -> List.of("setregion", "setschematic", "paste", "addnpc",
                        "removenpc", "listnpc", "setcost", "grant", "revoke",
                        "info", "reload", "setvaultblock", "clearvaultblock");
                case "vault"  -> List.of("clear", "inspect");
                default       -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hall")) {
            return switch (args[1].toLowerCase()) {
                case "addnpc", "removenpc" -> List.of("CLAN_SHOP", "CONTRIBUTION_SHOP",
                                "COINS_SHOP", "WAR_REGISTER", "CLAN_INFO", "UPGRADE", "HALL_INFO")
                        .stream().filter(s -> s.startsWith(args[2].toUpperCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }
        return List.of();
    }
}