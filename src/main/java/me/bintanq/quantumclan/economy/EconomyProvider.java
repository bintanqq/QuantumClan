package me.bintanq.quantumclan.economy;

import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction layer for all economy operations in QuantumClan.
 *
 * All balance-modifying methods return CompletableFuture<Boolean>
 * where true = success, false = failure (insufficient funds, provider error, etc.).
 *
 * Implementations:
 *   - VaultProvider        : uses Vault Economy (primary, REQUIRED)
 *   - PlayerPointsProvider : uses PlayerPoints integer points (optional)
 *   - CoinsProvider        : built-in SQLite Coins (secondary premium currency)
 */
public interface EconomyProvider {

    /**
     * Returns a human-readable name for this provider (e.g. "Vault", "Coins").
     */
    String getName();

    /**
     * Returns true if this provider is currently usable.
     */
    boolean isAvailable();

    /**
     * Gets the balance of the given player.
     */
    CompletableFuture<Double> getBalance(OfflinePlayer player);

    /**
     * Gets the balance of the given player by UUID.
     */
    CompletableFuture<Double> getBalance(UUID uuid);

    /**
     * Checks whether the player has at least the specified amount.
     */
    CompletableFuture<Boolean> has(OfflinePlayer player, double amount);

    /**
     * Withdraws (deducts) the given amount from the player's balance.
     * Will fail (complete false) if the player does not have enough.
     */
    CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount);

    /**
     * Deposits (adds) the given amount to the player's balance.
     */
    CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount);

    /**
     * Formats the given amount as a currency string according to
     * the provider's convention (e.g. "$1,000.00" or "1000 Points").
     */
    String format(double amount);
}