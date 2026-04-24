package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.manager.SocialManager;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Handles /qclan rival <declare|revoke|list>
 */
public class RivalCommand implements SubCommand {

    private final QuantumClan plugin;

    public RivalCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "error.player-only");
            return;
        }
        if (!plugin.checkPerm(player, "quantumclan.rival")) return;

        Clan myClan = plugin.getPlayerClan(player);
        if (myClan == null) return;

        if (args.length == 0) {
            plugin.sendMessage(player, "social.rival-usage");
            return;
        }

        String sub = args[0].toLowerCase();
        SocialManager social = plugin.getSocialManager();

        switch (sub) {
            case "declare" -> {
                if (!plugin.checkRole(player, "can-rival")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.rival-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (target.getId().equals(myClan.getId())) {
                    plugin.sendMessage(player, "social.rival-self");
                    return;
                }
                if (social.isRival(myClan.getId(), target.getId())) {
                    plugin.sendMessage(player, "social.rival-already", "{clan}", target.getName());
                    return;
                }
                if (social.getRivalryCount(myClan.getId()) >= social.getMaxRivalries()) {
                    plugin.sendMessage(player, "social.rival-max-reached");
                    return;
                }
                social.declareRivalry(myClan.getId(), target.getId());
                plugin.sendMessage(player, "social.rival-declared", "{clan}", target.getName());
                // Notify the target clan
                for (Player online : plugin.getClanManager().getOnlineMembers(target.getId())) {
                    plugin.sendMessage(online, "social.rival-declared-notify",
                            "{clan}", myClan.getName());
                }
            }
            case "revoke" -> {
                if (!plugin.checkRole(player, "can-rival")) return;
                if (args.length < 2) { plugin.sendMessage(player, "social.rival-usage"); return; }
                String targetName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                Clan target = resolveClan(player, targetName);
                if (target == null) return;
                if (!social.isRival(myClan.getId(), target.getId())) {
                    plugin.sendMessage(player, "social.rival-not-rival", "{clan}", target.getName());
                    return;
                }
                social.revokeRivalry(myClan.getId(), target.getId());
                plugin.sendMessage(player, "social.rival-revoked", "{clan}", target.getName());
            }
            case "list" -> {
                Set<String> rivals = social.getRivals(myClan.getId());
                if (rivals.isEmpty()) {
                    plugin.sendMessage(player, "social.rival-list-empty");
                    return;
                }
                plugin.sendRaw(player, plugin.getMessagesManager().get("social.rival-list-header"));
                for (String rivalId : rivals) {
                    Clan rivalClan = plugin.getClanManager().getClanById(rivalId);
                    if (rivalClan != null) {
                        plugin.sendRaw(player, plugin.getMessagesManager().get("social.rival-list-entry",
                                "{clan}", rivalClan.getName(), "{tag}", rivalClan.getFormattedTag()));
                    }
                }
            }
            default -> plugin.sendMessage(player, "social.rival-usage");
        }
    }

    private Clan resolveClan(Player player, String query) {
        Clan clan = plugin.getClanManager().getClanByName(query);
        if (clan == null) clan = plugin.getClanManager().getClanByTag(query);
        if (clan == null) plugin.sendMessage(player, "clan.not-found", "{clan}", query);
        return clan;
    }
}
