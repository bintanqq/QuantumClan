package me.bintanq.quantumclan.economy.impl;

import me.bintanq.quantumclan.economy.EconomyProvider;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyProvider backed by PlayerPoints.
 * PlayerPoints uses integer points; amounts are rounded to nearest int.
 */
public class PlayerPointsProvider implements EconomyProvider {

    private final PlayerPointsAPI api;

    public PlayerPointsProvider(PlayerPointsAPI api) {
        this.api = api;
    }

    @Override
    public String getName() {
        return "PlayerPoints";
    }

    @Override
    public boolean isAvailable() {
        return api != null;
    }

    @Override
    public CompletableFuture<Double> getBalance(OfflinePlayer player) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(0.0);
            return future;
        }
        future.complete((double) api.look(player.getUniqueId()));
        return future;
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID uuid) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(0.0);
            return future;
        }
        future.complete((double) api.look(uuid));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> has(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        int points = api.look(player.getUniqueId());
        future.complete(points >= (int) Math.ceil(amount));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        int intAmount = (int) Math.ceil(amount);
        if (intAmount <= 0) {
            future.complete(false);
            return future;
        }
        int current = api.look(player.getUniqueId());
        if (current < intAmount) {
            future.complete(false);
            return future;
        }
        boolean success = api.take(player.getUniqueId(), intAmount);
        future.complete(success);
        return future;
    }

    @Override
    public CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!isAvailable()) {
            future.complete(false);
            return future;
        }
        int intAmount = (int) Math.ceil(amount);
        if (intAmount <= 0) {
            future.complete(false);
            return future;
        }
        boolean success = api.give(player.getUniqueId(), intAmount);
        future.complete(success);
        return future;
    }

    @Override
    public String format(double amount) {
        return (int) Math.ceil(amount) + " Points";
    }
}