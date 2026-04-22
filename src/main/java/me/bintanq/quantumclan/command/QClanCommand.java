package me.bintanq.quantumclan.command;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.*;
import me.bintanq.quantumclan.gui.MainMenuGUI;
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
 * Routes /qclan <subcommand> to the appropriate SubCommand handler.
 * With no arguments, opens the Main Menu GUI.
 * /qclan help shows help text from messages.yml.
 */
public class QClanCommand implements CommandExecutor, TabCompleter {

    private final QuantumClan plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public QClanCommand(QuantumClan plugin) {
        this.plugin = plugin;
        register("create",       new CreateCommand(plugin));
        register("invite",       new InviteCommand(plugin));
        register("kick",         new KickCommand(plugin));
        register("leave",        new LeaveCommand(plugin));
        register("info",         new InfoCommand(plugin));
        register("home",         new HomeCommand(plugin));
        register("sethome",      new SetHomeCommand(plugin));
        register("delhome",      new DelHomeCommand(plugin));
        register("deposit",      new DepositCommand(plugin));
        register("shop",         new ShopCommand(plugin));
        register("bounty",       new BountyCommand(plugin));
        register("war",          new WarCommand(plugin));
        register("upgrade",      new UpgradeCommand(plugin));
        register("top",          new TopCommand(plugin));
        register("role",         new RoleCommand(plugin));
        register("transfer",     new TransferCommand(plugin));
        register("disband",      new DisbandCommand(plugin));
        register("announce",     new AnnounceCommand(plugin));
        register("contribution", new ContributionCommand(plugin));
        register("contrib",      new ContributionCommand(plugin)); // Alias
        register("accept",       new AcceptCommand(plugin));
        register("decline",      new DeclineCommand(plugin));
        register("coins",        new CoinsCommand(plugin));
        register("hall",         new HallCommand(plugin));
        register("vault",        new VaultCommand(plugin));
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

        if (!player.hasPermission("quantumclan.use")) {
            plugin.sendMessage(player, "error.no-permission");
            return true;
        }

        if (args.length == 0) {
            MainMenuGUI.open(plugin, player);
            return true;
        }

        String subName = args[0].toLowerCase();

        if (subName.equals("help")) {
            sendHelp(player);
            return true;
        }
        
        if (subName.equals("menu")) {
            MainMenuGUI.open(plugin, player);
            return true;
        }

        SubCommand cmd = subCommands.get(subName);
        if (cmd != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            cmd.execute(player, subArgs);
        } else {
            plugin.sendMessage(player, "error.unknown-subcommand");
        }

        return true;
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        var msg = plugin.getMessagesManager();
        plugin.sendRaw(player, msg.get("help.header"));

        String entry = msg.getRaw("help.entry");
        if (entry == null) entry = "<aqua>/qclan {cmd} <dark_gray>- <gray>{desc}";

        String[][] cmds = {
                { "create",       "quantumclan.clan.create",       msg.get("help.cmd-create") },
                { "invite",       "quantumclan.clan.invite",       msg.get("help.cmd-invite") },
                { "accept",       "quantumclan.use",               msg.get("help.cmd-accept") },
                { "decline",      "quantumclan.use",               msg.get("help.cmd-decline") },
                { "kick",         "quantumclan.clan.kick",         msg.get("help.cmd-kick") },
                { "leave",        "quantumclan.clan.leave",        msg.get("help.cmd-leave") },
                { "info",         "quantumclan.clan.info",         msg.get("help.cmd-info") },
                { "home",         "quantumclan.home.use",          msg.get("help.cmd-home") },
                { "sethome",      "quantumclan.home.set",          msg.get("help.cmd-sethome") },
                { "delhome",      "quantumclan.home.delete",       msg.get("help.cmd-delhome") },
                { "deposit",      "quantumclan.clan.deposit",      msg.get("help.cmd-deposit") },
                { "shop",         "quantumclan.shop.use",          msg.get("help.cmd-shop") },
                { "bounty",       "quantumclan.bounty.board",      msg.get("help.cmd-bounty") },
                { "war",          "quantumclan.war.register",      msg.get("help.cmd-war") },
                { "upgrade",      "quantumclan.clan.upgrade",      msg.get("help.cmd-upgrade") },
                { "top",          "quantumclan.top",               msg.get("help.cmd-top") },
                { "role",         "quantumclan.clan.role",         msg.get("help.cmd-role") },
                { "transfer",     "quantumclan.clan.transfer",     msg.get("help.cmd-transfer") },
                { "disband",      "quantumclan.clan.disband",      msg.get("help.cmd-disband") },
                { "announce",     "quantumclan.clan.announce",     msg.get("help.cmd-announce") },
                { "contribution", "quantumclan.contribution.shop", msg.get("help.cmd-contribution") },
                { "coins",        "quantumclan.coins.shop",        msg.get("help.cmd-coins") },
                { "hall",         "quantumclan.use",               msg.get("help.cmd-hall", "{cmd}", "hall") },
        };

        final String finalEntry = entry;
        for (String[] triple : cmds) {
            if (player.hasPermission(triple[1])) {
                plugin.sendRaw(player, finalEntry
                        .replace("{cmd}", triple[0])
                        .replace("{desc}", triple[2] != null ? triple[2] : triple[0]));
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
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            List<String> subs = List.of(
                    "create", "invite", "accept", "decline", "kick", "leave", "info",
                    "home", "sethome", "delhome", "deposit", "shop", "bounty",
                    "war", "upgrade", "top", "role", "transfer", "disband",
                    "announce", "contribution", "coins", "hall", "menu", "help", "vault"
            );
            String partial = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length >= 2) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
                case "hall" -> {
                    if (args.length == 2) {
                        yield List.of("buy", "tp").stream()
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "bounty" -> {
                    if (args.length == 2) {
                        yield List.of("place", "board", "submit").stream()
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "war" -> {
                    if (args.length == 2) {
                        yield List.of("register", "leave").stream()
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    yield List.of();
                }
                case "role" -> {
                    if (args.length == 2) {
                        yield List.of("set").stream()
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (args.length == 4) {
                        yield plugin.getRolesConfigManager().getRoleNames().stream()
                                .filter(r -> r.startsWith(args[3].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }

        return List.of();
    }
}