package me.bintanq.quantumclan.command.sub;

import org.bukkit.command.CommandSender;

/**
 * Common interface for all plugin subcommands.
 */
public interface SubCommand {
    /**
     * Executes the subcommand.
     * @param sender The sender who executed the command (Player or Console).
     * @param args The arguments passed to the subcommand (excluding the subcommand name itself).
     */
    void execute(CommandSender sender, String[] args);
}
