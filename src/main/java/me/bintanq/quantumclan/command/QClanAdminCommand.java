package me.bintanq.quantumclan.command;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.admin.*;
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
 * Routes /qclanadmin <subcommand> to the appropriate admin handler.
 */
public class QClanAdminCommand implements CommandExecutor, TabCompleter {

    private final QuantumClan plugin;

    private final AdminReloadCommand  reload;
    private final AdminCoinsCommand   coins;
    private final AdminClanCommand    clan;
    private final AdminWarCommand     war;
    private final AdminSetArenaCommand setArena;

    public QClanAdminCommand(QuantumClan plugin) {
        this.plugin   = plugin;
        reload   = new AdminReloadCommand(plugin);
        coins    = new AdminCoinsCommand(plugin);
        clan     = new AdminClanCommand(plugin);
        war      = new AdminWarCommand(plugin);
        setArena = new AdminSetArenaCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only"); return true;
        }
        if (!player.hasPermission("quantumclan.admin")) {
            plugin.sendMessage(player, "error.no-permission"); return true;
        }
        if (args.length == 0) { sendAdminHelp(player); return true; }

        String sub     = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "reload"   -> reload.execute(player, subArgs);
            case "give"     -> {
                // /qclanadmin give coins <player> <amount>
                if (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("coins")) {
                    coins.execute(player, Arrays.copyOfRange(subArgs, 1, subArgs.length));
                } else {
                    plugin.sendRaw(player, "<red>/qclanadmin give coins <player> <amount>");
                }
            }
            case "setarena" -> setArena.execute(player, subArgs);
            case "war"      -> war.execute(player, subArgs);
            case "clan"     -> clan.execute(player, subArgs);
            default         -> sendAdminHelp(player);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("quantumclan.admin")) return List.of();

        if (args.length == 1) {
            return List.of("reload", "give", "setarena", "war", "clan").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "give"   -> List.of("coins");
                case "war"    -> List.of("start", "end");
                case "clan"   -> List.of("info", "delete", "setlevel", "setreputation");
                default       -> List.of();
            };
        }
        return List.of();
    }

    private void sendAdminHelp(Player player) {
        plugin.sendRaw(player, "<gold>━━━━━━ <yellow>QuantumClan Admin <gold>━━━━━━");
        plugin.sendRaw(player, "<yellow>/qclanadmin reload");
        plugin.sendRaw(player, "<yellow>/qclanadmin give coins <player> <amount>");
        plugin.sendRaw(player, "<yellow>/qclanadmin setarena");
        plugin.sendRaw(player, "<yellow>/qclanadmin war <start|end>");
        plugin.sendRaw(player, "<yellow>/qclanadmin clan <info|delete|setlevel|setreputation> <clan>");
        plugin.sendRaw(player, "<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}