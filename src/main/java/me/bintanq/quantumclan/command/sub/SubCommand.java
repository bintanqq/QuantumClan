package me.bintanq.quantumclan.command.sub;

import org.bukkit.entity.Player;

/**
 * Common interface for all plugin subcommands.
 */
public interface SubCommand {
    /**
     * Executes the subcommand.
     * @param player The player who executed the command.
     * @param args The arguments passed to the subcommand (excluding the subcommand name itself).
     */
    void execute(Player player, String[] args);
}
