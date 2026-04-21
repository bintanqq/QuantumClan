package me.bintanq.quantumclan.economy.impl;

import me.bintanq.quantumclan.economy.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyProvider backed by Vault.
 * Vault operations are synchronous; we wrap them in CompletableFuture
 * for API uniformity. Callers must ensure they handle the returned
 * future on the correct thread (Vault operations must run on main thread).
 */
public class VaultProvider implements EconomyProvider {

    private final Economy economy;

    public VaultProvider(Economy economy) {
        this.economy = economy;
    }

    @Override
    public String getName() {
        return "Vault (" + (economy != null ? economy.getName() : "null") + ")";
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public CompletableFuture<Double> getBalance(OfflinePlayer player) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(0.0);
            return future;
        }
        future.complete(economy.getBalance(player));
        return future;
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    public CompletableFuture<Boolean> has(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        future.complete(economy.has(player, amount));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        if (amount <= 0) {
            future.complete(false);
            return future;
        }
        if (!economy.has(player, amount)) {
            future.complete(false);
            return future;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        future.complete(response.transactionSuccess());
        return future;
    }

    @Override
    public CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        if (amount <= 0) {
            future.complete(false);
            return future;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        future.complete(response.transactionSuccess());
        return future;
    }

    @Override
    public String format(double amount) {
        if (!isAvailable()) return String.valueOf(amount);
        return economy.format(amount);
    }
}