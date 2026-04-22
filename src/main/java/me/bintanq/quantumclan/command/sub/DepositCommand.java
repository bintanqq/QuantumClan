package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DepositCommand implements SubCommand {

    private final QuantumClan plugin;

    public DepositCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (!plugin.checkPerm(player, "quantumclan.clan.deposit")) return;
        if (args.length == 0) {
            plugin.sendMessage(player, "clan.deposit-usage");
            return;
        }

        Clan clan = plugin.getPlayerClan(player);
        if (clan == null) return;
        if (!plugin.checkRole(player, "can-deposit")) return;

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "error.invalid-number");
            return;
        }

        if (amount <= 0) {
            plugin.sendMessage(player, "error.invalid-amount", "{value}", "1");
            return;
        }

        plugin.getEconomyProvider().withdraw(player, amount).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        plugin.sendMessage(player, "error.not-enough-balance",
                                "{value}", plugin.getEconomyProvider().format(amount));
                        return;
                    }

                    plugin.getClanManager().depositMoney(player.getUniqueId(), amount);

                    // Calculate contribution points using configurable unit
                    // e.g. deposit-per-unit=1, deposit-amount-unit=100 → 1 pt per 100 deposited
                    int unit = plugin.getConfigManager().getContribDepositAmountUnit();
                    int ptsPerUnit = plugin.getConfigManager().getContribDepositPerUnit();
                    int pts = 0;
                    if (unit > 0 && ptsPerUnit > 0) {
                        pts = (int) (amount / unit) * ptsPerUnit;
                    }

                    if (pts > 0) {
                        plugin.getClanManager().addContributionPoints(player.getUniqueId(), pts);
                        plugin.getContributionDAO().logContribution(
                                player.getUniqueId(), clan.getId(), pts, "deposit");
                        plugin.sendMessage(player, "clan.deposit-contribution",
                                "{value}", String.valueOf(pts));
                    }

                    plugin.sendMessage(player, "clan.deposit-success",
                            "{value}", plugin.getEconomyProvider().format(amount));
                }));
    }
}
