package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.manager.SocialManager;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Handles /qclan ally <propose|accept|decline|break|list>
 */
public class AllyCommand implements SubCommand {

    private final QuantumClan plugin;

    public AllyCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.ally")) return;

        Clan myClan = plugin.getPlayerClan(player);
        if (myClan == null) return;

        if (args.length == 0) {
            plugin.sendMessage(player, "social.ally-usage");
            return;
        }

        String sub = args[0].toLowerCase();
        SocialManager social = plugin.getSocialManager();

        switch (sub) {
            case "propose" -> {
                if (!plugin.checkRole(player, "can-ally")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.ally-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (target.getId().equals(myClan.getId())) {
                    plugin.sendMessage(player, "social.ally-self");
                    return;
                }
                if (social.areAllied(myClan.getId(), target.getId())) {
                    plugin.sendMessage(player, "social.ally-already", "{clan}", target.getName());
                    return;
                }
                if (social.getAllianceCount(myClan.getId()) >= social.getMaxAlliances()) {
                    plugin.sendMessage(player, "social.ally-max-reached");
                    return;
                }
                if (social.getAllianceCount(target.getId()) >= social.getMaxAlliances()) {
                    plugin.sendMessage(player, "social.ally-target-max", "{clan}", target.getName());
                    return;
                }
                if (social.hasPendingProposal(myClan.getId(), target.getId())) {
                    plugin.sendMessage(player, "social.ally-already-proposed", "{clan}", target.getName());
                    return;
                }
                // If the target already proposed to us, auto-accept
                if (social.hasPendingProposal(target.getId(), myClan.getId())) {
                    acceptAlliance(player, myClan, target, social);
                    return;
                }
                social.addProposal(myClan.getId(), target.getId());
                plugin.sendMessage(player, "social.ally-proposed", "{clan}", target.getName());
                // Notify target clan's online officers/leaders
                notifyTargetClan(target, myClan);
            }
            case "accept" -> {
                if (!plugin.checkRole(player, "can-ally")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.ally-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (!social.hasPendingProposal(target.getId(), myClan.getId())) {
                    plugin.sendMessage(player, "social.ally-no-proposal", "{clan}", target.getName());
                    return;
                }
                if (social.getAllianceCount(myClan.getId()) >= social.getMaxAlliances()) {
                    plugin.sendMessage(player, "social.ally-max-reached");
                    return;
                }
                acceptAlliance(player, myClan, target, social);
            }
            case "decline" -> {
                if (!plugin.checkRole(player, "can-ally")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.ally-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (!social.hasPendingProposal(target.getId(), myClan.getId())) {
                    plugin.sendMessage(player, "social.ally-no-proposal", "{clan}", target.getName());
                    return;
                }
                social.removeProposal(target.getId(), myClan.getId());
                plugin.sendMessage(player, "social.ally-declined", "{clan}", target.getName());
            }
            case "break" -> {
                if (!plugin.checkRole(player, "can-ally")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.ally-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (!social.areAllied(myClan.getId(), target.getId())) {
                    plugin.sendMessage(player, "social.ally-not-allied", "{clan}", target.getName());
                    return;
                }
                social.breakAlliance(myClan.getId(), target.getId());
                plugin.sendMessage(player, "social.ally-broken", "{clan}", target.getName());
                // Notify the other clan
                for (Player online : plugin.getClanManager().getOnlineMembers(target.getId())) {
                    plugin.sendMessage(online, "social.ally-broken-notify", "{clan}", myClan.getName());
                }
            }
            case "list" -> {
                Set<String> allies = social.getAllies(myClan.getId());
                if (allies.isEmpty()) {
                    plugin.sendMessage(player, "social.ally-list-empty");
                    return;
                }
                plugin.sendRaw(player, plugin.getMessagesManager().get("social.ally-list-header"));
                for (String allyId : allies) {
                    Clan allyClan = plugin.getClanManager().getClanById(allyId);
                    if (allyClan != null) {
                        plugin.sendRaw(player, plugin.getMessagesManager().get("social.ally-list-entry",
                                "{clan}", allyClan.getName(), "{tag}", allyClan.getFormattedTag()));
                    }
                }
                // Show pending incoming proposals
                Set<String> incoming = social.getIncomingProposals(myClan.getId());
                if (!incoming.isEmpty()) {
                    plugin.sendRaw(player, plugin.getMessagesManager().get("social.ally-pending-header"));
                    for (String proposerId : incoming) {
                        Clan proposer = plugin.getClanManager().getClanById(proposerId);
                        if (proposer != null) {
                            plugin.sendRaw(player, plugin.getMessagesManager().get("social.ally-pending-entry",
                                    "{clan}", proposer.getName()));
                        }
                    }
                }
            }
            default -> plugin.sendMessage(player, "social.ally-usage");
        }
    }

    private void acceptAlliance(Player player, Clan myClan, Clan target, SocialManager social) {
        social.createAlliance(myClan.getId(), target.getId());
        plugin.sendMessage(player, "social.ally-accepted", "{clan}", target.getName());
        // Broadcast to both clans
        for (Player online : plugin.getClanManager().getOnlineMembers(myClan.getId())) {
            if (!online.equals(player)) {
                plugin.sendMessage(online, "social.ally-formed-broadcast",
                        "{clan}", target.getName());
            }
        }
        for (Player online : plugin.getClanManager().getOnlineMembers(target.getId())) {
            plugin.sendMessage(online, "social.ally-formed-broadcast",
                    "{clan}", myClan.getName());
        }
    }

    private void notifyTargetClan(Clan target, Clan proposer) {
        for (Player online : plugin.getClanManager().getOnlineMembers(target.getId())) {
            if (plugin.getClanManager().hasRolePermission(online.getUniqueId(), "can-ally")) {
                plugin.sendMessage(online, "social.ally-proposal-received",
                        "{clan}", proposer.getName());
            }
        }
    }

    private Clan resolveClan(Player player, String query) {
        Clan clan = plugin.getClanManager().getClanByName(query);
        if (clan == null) clan = plugin.getClanManager().getClanByTag(query);
        if (clan == null) plugin.sendMessage(player, "clan.not-found", "{clan}", query);
        return clan;
    }
}
