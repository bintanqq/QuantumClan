package me.bintanq.quantumclan.economy.impl;

import me.bintanq.quantumclan.economy.EconomyProvider;
import me.glaremasters.gemseconomy.api.GemsEconomyAPI;
import me.glaremasters.gemseconomy.currency.Currency;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyProvider backed by GemsEconomy.
 * Uses a specific named currency configured in config.yml (gemseconomy-currency).
 * Falls back gracefully if the configured currency does not exist.
 */
public class GemsEconomyProvider implements EconomyProvider {

    private final GemsEconomyAPI api;
    private final String currencyName;

    public GemsEconomyProvider(GemsEconomyAPI api, String currencyName) {
        this.api = api;
        this.currencyName = currencyName;
    }

    @Override
    public String getName() {
        return "GemsEconomy (" + currencyName + ")";
    }

    @Override
    public boolean isAvailable() {
        return api != null && getCurrency() != null;
    }

    /**
     * Resolves the configured currency. Returns null if not found.
     */
    private Currency getCurrency() {
        if (api == null) return null;
        return api.getCurrency(currencyName);
    }

    @Override
    public CompletableFuture<Double> getBalance(OfflinePlayer player) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        Currency currency = getCurrency();
        if (!isAvailable() || currency == null) {
            future.complete(0.0);
            return future;
        }
        double balance = api.getBalance(player.getUniqueId(), currency);
        future.complete(balance);
        return future;
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    @Override
    public CompletableFuture<Boolean> has(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Currency currency = getCurrency();
        if (!isAvailable() || currency == null) {
            future.complete(false);
            return future;
        }
        double balance = api.getBalance(player.getUniqueId(), currency);
        future.complete(balance >= amount);
        return future;
    }

    @Override
    public CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Currency currency = getCurrency();
        if (!isAvailable() || currency == null) {
            future.complete(false);
            return future;
        }
        if (amount <= 0) {
            future.complete(false);
            return future;
        }
        double balance = api.getBalance(player.getUniqueId(), currency);
        if (balance < amount) {
            future.complete(false);
            return future;
        }
        boolean success = api.withdrawBalance(player.getUniqueId(), amount, currency);
        future.complete(success);
        return future;
    }

    @Override
    public CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Currency currency = getCurrency();
        if (!isAvailable() || currency == null) {
            future.complete(false);
            return future;
        }
        if (amount <= 0) {
            future.complete(false);
            return future;
        }
        boolean success = api.depositBalance(player.getUniqueId(), amount, currency);
        future.complete(success);
        return future;
    }

    @Override
    public String format(double amount) {
        Currency currency = getCurrency();
        if (currency == null) return amount + " " + currencyName;
        // GemsEconomy Currency has a symbol / plural name
        return String.format("%.2f %s", amount,
                amount == 1.0 ? currency.getSingular() : currency.getPlural());
    }
}