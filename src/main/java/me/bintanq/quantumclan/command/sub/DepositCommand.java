package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DepositCommand {
    private final QuantumClan plugin;
    public DepositCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.clan.deposit")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan deposit <amount>"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-deposit")) {
            plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole()); return;
        }
        long amount;
        try { amount = Long.parseLong(args[0]); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
        if (amount <= 0) { plugin.sendMessage(player, "error.invalid-amount", "{value}", "1"); return; }
        plugin.getEconomyProvider().withdraw(player, amount).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) { plugin.sendMessage(player, "error.not-enough-balance", "{value}", plugin.getEconomyProvider().format(amount)); return; }
                    plugin.getClanManager().depositMoney(player.getUniqueId(), amount);
                    // Contribution points
                    int ptsPerK = plugin.getConfigManager().getContribDepositPerThousand();
                    int pts = (int) (amount / 1000) * ptsPerK;
                    if (pts > 0) {
                        plugin.getClanManager().addContributionPoints(player.getUniqueId(), pts);
                        plugin.getContributionDAO().logContribution(player.getUniqueId(), clan.getId(), pts, "deposit");
                    }
                    plugin.sendMessage(player, "clan.deposit-success", "{value}", plugin.getEconomyProvider().format(amount));
                }));
    }
}
