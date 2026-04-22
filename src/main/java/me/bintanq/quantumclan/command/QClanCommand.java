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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Routes /qclan <subcommand> to the appropriate SubCommand handler.
 * With no arguments, opens the Main Menu GUI.
 * /qclan help shows help text from messages.yml.
 */
public class QClanCommand implements CommandExecutor, TabCompleter {

    private final QuantumClan plugin;

    private final CreateCommand       create;
    private final InviteCommand       invite;
    private final KickCommand         kick;
    private final LeaveCommand        leave;
    private final InfoCommand         info;
    private final HomeCommand         home;
    private final SetHomeCommand      sethome;
    private final DelHomeCommand      delhome;
    private final DepositCommand      deposit;
    private final ShopCommand         shop;
    private final BountyCommand       bounty;
    private final WarCommand          war;
    private final UpgradeCommand      upgrade;
    private final TopCommand          top;
    private final RoleCommand         role;
    private final TransferCommand     transfer;
    private final DisbandCommand      disband;
    private final AnnounceCommand     announce;
    private final ContributionCommand contribution;
    private final AcceptCommand       accept;
    private final DeclineCommand      decline;
    private final CoinsCommand        coins;

    public QClanCommand(QuantumClan plugin) {
        this.plugin       = plugin;
        create       = new CreateCommand(plugin);
        invite       = new InviteCommand(plugin);
        kick         = new KickCommand(plugin);
        leave        = new LeaveCommand(plugin);
        info         = new InfoCommand(plugin);
        home         = new HomeCommand(plugin);
        sethome      = new SetHomeCommand(plugin);
        delhome      = new DelHomeCommand(plugin);
        deposit      = new DepositCommand(plugin);
        shop         = new ShopCommand(plugin);
        bounty       = new BountyCommand(plugin);
        war          = new WarCommand(plugin);
        upgrade      = new UpgradeCommand(plugin);
        top          = new TopCommand(plugin);
        role         = new RoleCommand(plugin);
        transfer     = new TransferCommand(plugin);
        disband      = new DisbandCommand(plugin);
        announce     = new AnnounceCommand(plugin);
        contribution = new ContributionCommand(plugin);
        accept       = new AcceptCommand(plugin);
        decline      = new DeclineCommand(plugin);
        coins        = new CoinsCommand(plugin);
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

        // No args → open Main Menu GUI
        if (args.length == 0) {
            MainMenuGUI.open(plugin, player);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "help"         -> sendHelp(player);
            case "create"       -> create.execute(player, subArgs);
            case "invite"       -> invite.execute(player, subArgs);
            case "accept"       -> accept.execute(player, subArgs);
            case "decline"      -> decline.execute(player, subArgs);
            case "kick"         -> kick.execute(player, subArgs);
            case "leave"        -> leave.execute(player, subArgs);
            case "info"         -> info.execute(player, subArgs);
            case "home"         -> home.execute(player, subArgs);
            case "sethome"      -> sethome.execute(player, subArgs);
            case "delhome"      -> delhome.execute(player, subArgs);
            case "deposit"      -> deposit.execute(player, subArgs);
            case "shop"         -> shop.execute(player, subArgs);
            case "bounty"       -> bounty.execute(player, subArgs);
            case "war"          -> war.execute(player, subArgs);
            case "upgrade"      -> upgrade.execute(player, subArgs);
            case "top"          -> top.execute(player, subArgs);
            case "role"         -> role.execute(player, subArgs);
            case "transfer"     -> transfer.execute(player, subArgs);
            case "disband"      -> disband.execute(player, subArgs);
            case "announce"     -> announce.execute(player, subArgs);
            case "contribution",
                 "contrib"      -> contribution.execute(player, subArgs);
            case "coins"        -> coins.execute(player, subArgs);
            case "menu"         -> MainMenuGUI.open(plugin, player);
            default             -> plugin.sendMessage(player, "error.unknown-subcommand");
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
        };

        final String finalEntry = entry;
        for (String[] triple : cmds) {
            if (player.hasPermission(triple[1])) {
                plugin.sendRaw(player, finalEntry
                        .replace("{cmd}", triple[0])
                        .replace("{desc}", triple[2]));
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
                    "announce", "contribution", "coins", "menu", "help"
            );
            String partial = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }

        if (args.length >= 2) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
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