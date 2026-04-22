package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.ClanInfoGUI;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AdminClanCommand {
    private final QuantumClan plugin;
    public AdminClanCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclanadmin clan <info|delete|setlevel|setreputation> <clan>"); return; }
        switch (args[0].toLowerCase()) {
            case "info" -> {
                if (args.length < 2) { plugin.sendRaw(player, "<red>/qclanadmin clan info <clan>"); return; }
                Clan clan = plugin.getClanManager().getClanByName(args[1]);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", args[1]); return; }
                ClanInfoGUI.open(plugin, player, clan);
            }
            case "delete" -> {
                if (args.length < 2) { plugin.sendRaw(player, "<red>/qclanadmin clan delete <clan>"); return; }
                Clan clan = plugin.getClanManager().getClanByName(args[1]);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", args[1]); return; }
                String name = clan.getName();
                plugin.getClanManager().disbandClan(clan.getId()).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-deleted", "{clan}", name)));
            }
            case "setlevel" -> {
                if (args.length < 3) { plugin.sendRaw(player, "<red>/qclanadmin clan setlevel <clan> <level>"); return; }
                Clan clan = plugin.getClanManager().getClanByName(args[1]);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", args[1]); return; }
                int level; try { level = Integer.parseInt(args[2]); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
                level = Math.max(1, Math.min(level, plugin.getConfigManager().getMaxLevel()));
                int finalLevel = level;
                plugin.getClanManager().adminSetLevel(clan.getId(), level).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-level-set", "{clan}", clan.getName(), "{value}", String.valueOf(finalLevel))));
            }
            case "setreputation" -> {
                if (args.length < 3) { plugin.sendRaw(player, "<red>/qclanadmin clan setreputation <clan> <amount>"); return; }
                Clan clan = plugin.getClanManager().getClanByName(args[1]);
                if (clan == null) { plugin.sendMessage(player, "clan.not-found", "{clan}", args[1]); return; }
                int rep; try { rep = Integer.parseInt(args[2]); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
                int finalRep = rep;
                plugin.getClanManager().setReputation(clan.getId(), rep).thenAccept(ok ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                plugin.sendMessage(player, "admin.clan-reputation-set", "{clan}", clan.getName(), "{value}", String.valueOf(finalRep))));
            }
            default -> plugin.sendRaw(player, "<red>/qclanadmin clan <info|delete|setlevel|setreputation> <clan>");
        }
    }
}