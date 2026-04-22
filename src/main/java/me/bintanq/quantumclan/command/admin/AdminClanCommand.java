package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanInfoGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class AdminClanCommand {

    private final QuantumClan plugin;

    public AdminClanCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendRaw(player, "<white>/qclanadmin clan <info|delete|setlevel|setreputation> <clan>");
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "info" -> {
                if (args.length < 2) { plugin.sendRaw(player, "<white>/qclanadmin clan info <clan>"); return; }
                String clanQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Clan clan = plugin.getClanManager().getClanByName(clanQuery);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery); return; }
                ClanInfoGUI.open(plugin, player, clan);
            }
            case "delete" -> {
                if (args.length < 2) { plugin.sendRaw(player, "<white>/qclanadmin clan delete <clan>"); return; }
                String clanQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Clan clan = plugin.getClanManager().getClanByName(clanQuery);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery); return; }
                String name = clan.getName();
                plugin.getClanManager().disbandClan(clan.getId()).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-deleted", "{clan}", name)));
            }
            case "setlevel" -> {
                // Syntax: setlevel <level> <clan...> OR setlevel <clan...> <level>
                // We use: /qclanadmin clan setlevel <clan> <level>
                // Level is always the LAST arg, clan name is everything in between
                if (args.length < 3) { plugin.sendRaw(player, "<white>/qclanadmin clan setlevel <clan> <level>"); return; }
                String levelStr = args[args.length - 1];
                String clanQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                Clan clan = plugin.getClanManager().getClanByName(clanQuery);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery); return; }
                int level;
                try { level = Integer.parseInt(levelStr); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
                level = Math.max(1, Math.min(level, plugin.getConfigManager().getMaxLevel()));
                int finalLevel = level;
                plugin.getClanManager().adminSetLevel(clan.getId(), level).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-level-set",
                                        "{clan}", clan.getName(),
                                        "{value}", String.valueOf(finalLevel))));
            }
            case "setreputation" -> {
                if (args.length < 3) { plugin.sendRaw(player, "<white>/qclanadmin clan setreputation <clan> <amount>"); return; }
                String repStr = args[args.length - 1];
                String clanQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                Clan clan = plugin.getClanManager().getClanByName(clanQuery);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery); return; }
                int rep;
                try { rep = Integer.parseInt(repStr); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
                int finalRep = rep;
                plugin.getClanManager().setReputation(clan.getId(), rep).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-reputation-set",
                                        "{clan}", clan.getName(),
                                        "{value}", String.valueOf(finalRep))));
            }
            default -> plugin.sendRaw(player, "<white>/qclanadmin clan <info|delete|setlevel|setreputation> <clan>");
        }
    }
}