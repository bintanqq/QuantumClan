package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Handles /qclanadmin vault <subcommand>.
 * All messages come from messages.yml — no hardcoded strings.
 */
public class AdminVaultCommand implements SubCommand {

    private final QuantumClan plugin;

    public AdminVaultCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "clear"   -> handleClear(player, Arrays.copyOfRange(args, 1, args.length));
            case "inspect" -> handleInspect(player, Arrays.copyOfRange(args, 1, args.length));
            default        -> sendHelp(player);
        }
    }

    private void handleClear(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "admin.vault-usage");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanVaultManager().clearVault(clan.getId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player,
                                ok ? "vault.admin-clear-success" : "vault.admin-clear-failed",
                                "{clan}", clan.getName())));
    }

    private void handleInspect(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "admin.vault-usage");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanVaultManager().openVaultAdmin(player, clan);
        plugin.sendMessage(player, "vault.admin-inspect-opened", "{clan}", clan.getName());
    }

    private void sendHelp(Player player) {
        plugin.sendMessage(player, "help.admin-header");
        plugin.sendRaw(player, plugin.getMessagesManager().get("help.admin-entry")
                .replace("{cmd}", "vault clear <clan>")
                .replace("{desc}", plugin.getMessagesManager().get("help.admin-cmd-vault")));
        plugin.sendRaw(player, plugin.getMessagesManager().get("help.admin-entry")
                .replace("{cmd}", "vault inspect <clan>")
                .replace("{desc}", plugin.getMessagesManager().get("help.admin-cmd-vault-inspect")));
    }
}
