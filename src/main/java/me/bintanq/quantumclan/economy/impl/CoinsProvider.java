package me.bintanq.quantumclan.economy.impl;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.database.dao.CoinsDAO;
import me.bintanq.quantumclan.economy.EconomyProvider;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyProvider for the built-in Coins system.
 * Coins are stored in SQLite via CoinsDAO.
 * Used as a secondary premium currency (not the primary economy provider).
 */
public class CoinsProvider implements EconomyProvider {

    private final QuantumClan plugin;
    private final CoinsDAO coinsDAO;

    public CoinsProvider(QuantumClan plugin, CoinsDAO coinsDAO) {
        this.plugin   = plugin;
        this.coinsDAO = coinsDAO;
    }

    @Override
    public String getName() {
        return "Coins (Built-in)";
    }

    @Override
    public boolean isAvailable() {
        return coinsDAO != null;
    }

    @Override
    public CompletableFuture<Double> getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID uuid) {
        if (!isAvailable()) return CompletableFuture.completedFuture(0.0);
        return coinsDAO.getCoins(uuid).thenApply(Long::doubleValue);
    }

    @Override
    public CompletableFuture<Boolean> has(OfflinePlayer player, double amount) {
        return has(player.getUniqueId(), amount);
    }

    public CompletableFuture<Boolean> has(UUID uuid, double amount) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        return coinsDAO.getCoins(uuid).thenApply(coins -> coins >= (long) Math.ceil(amount));
    }

    @Override
    public CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    public CompletableFuture<Boolean> withdraw(UUID uuid, double amount) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        long intAmount = (long) Math.ceil(amount);
        if (intAmount <= 0) return CompletableFuture.completedFuture(false);

        return coinsDAO.getCoins(uuid).thenCompose(current -> {
            if (current < intAmount) return CompletableFuture.completedFuture(false);
            return coinsDAO.setCoins(uuid, current - intAmount, "withdraw");
        });
    }

    @Override
    public CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount, "deposit");
    }

    /**
     * Public overload — deposit with a specific reason for ledger logging.
     */
    public CompletableFuture<Boolean> deposit(UUID uuid, double amount, String reason) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        long intAmount = (long) Math.ceil(amount);
        if (intAmount <= 0) return CompletableFuture.completedFuture(false);

        return coinsDAO.getCoins(uuid).thenCompose(current ->
                coinsDAO.setCoins(uuid, current + intAmount, reason));
    }

    /**
     * Admin grant — adds coins with a specific reason for ledger logging.
     */
    public CompletableFuture<Boolean> grant(UUID uuid, long amount, String reason) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        if (amount <= 0) return CompletableFuture.completedFuture(false);
        return coinsDAO.getCoins(uuid).thenCompose(current ->
                coinsDAO.setCoins(uuid, current + amount, reason));
    }

    /**
     * Direct coins balance query returning long (no double conversion).
     */
    public CompletableFuture<Long> getCoins(UUID uuid) {
        if (!isAvailable()) return CompletableFuture.completedFuture(0L);
        return coinsDAO.getCoins(uuid);
    }

    @Override
    public String format(double amount) {
        String coinsName = plugin.getConfigManager().getCoinsName();
        return (long) Math.ceil(amount) + " " + coinsName;
    }
}