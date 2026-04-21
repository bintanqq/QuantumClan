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
 *   - VaultProvider         : uses Vault Economy (primary, REQUIRED)
 *   - PlayerPointsProvider  : uses PlayerPoints integer points (optional)
 *   - GemsEconomyProvider   : uses GemsEconomy multi-currency (optional)
 *   - CoinsProvider         : built-in SQLite Coins (secondary premium currency)
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
     *
     * @param player OfflinePlayer to query
     * @return CompletableFuture resolving to the player's balance (0.0 on error)
     */
    CompletableFuture<Double> getBalance(OfflinePlayer player);

    /**
     * Gets the balance of the given player by UUID.
     *
     * @param uuid Player UUID
     * @return CompletableFuture resolving to the player's balance (0.0 on error)
     */
    CompletableFuture<Double> getBalance(UUID uuid);

    /**
     * Checks whether the player has at least the specified amount.
     *
     * @param player OfflinePlayer to check
     * @param amount Amount to check
     * @return CompletableFuture resolving to true if player has enough
     */
    CompletableFuture<Boolean> has(OfflinePlayer player, double amount);

    /**
     * Withdraws (deducts) the given amount from the player's balance.
     * Will fail (complete false) if the player does not have enough.
     *
     * @param player OfflinePlayer to withdraw from
     * @param amount Amount to withdraw (must be > 0)
     * @return CompletableFuture resolving to true on success
     */
    CompletableFuture<Boolean> withdraw(OfflinePlayer player, double amount);

    /**
     * Deposits (adds) the given amount to the player's balance.
     *
     * @param player OfflinePlayer to deposit to
     * @param amount Amount to deposit (must be > 0)
     * @return CompletableFuture resolving to true on success
     */
    CompletableFuture<Boolean> deposit(OfflinePlayer player, double amount);

    /**
     * Formats the given amount as a currency string according to
     * the provider's convention (e.g. "$1,000.00" or "1000 Points").
     *
     * @param amount Amount to format
     * @return Formatted string
     */
    String format(double amount);
}