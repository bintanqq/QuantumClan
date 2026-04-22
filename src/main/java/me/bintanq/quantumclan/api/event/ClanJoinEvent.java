package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a player joins a clan (accepts an invite).
 */
public class ClanJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanAPI clan;
    private final Player player;

    public ClanJoinEvent(@NotNull ClanAPI clan, @NotNull Player player) {
        this.clan   = clan;
        this.player = player;
    }

    /** The clan the player joined. */
    @NotNull
    public ClanAPI getClan() {
        return clan;
    }

    /** The player who joined the clan. */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}