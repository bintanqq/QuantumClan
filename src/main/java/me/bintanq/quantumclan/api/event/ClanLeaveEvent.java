package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a player leaves (or is kicked from) a clan.
 *
 * <p>This covers all removal scenarios: voluntary leave, kick by officer/leader,
 * and admin deletion. Check {@link Reason} to distinguish them.</p>
 */
public class ClanLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The reason a member left the clan. */
    public enum Reason {
        /** Player voluntarily left with {@code /qclan leave}. */
        LEAVE,
        /** Player was kicked by an officer or leader. */
        KICKED,
        /** The clan was disbanded — all members removed. */
        DISBAND,
        /** Removed via an admin command. */
        ADMIN
    }

    private final ClanAPI clan;
    private final OfflinePlayer player;
    private final Reason reason;

    public ClanLeaveEvent(@NotNull ClanAPI clan, @NotNull OfflinePlayer player,
                          @NotNull Reason reason) {
        this.clan   = clan;
        this.player = player;
        this.reason = reason;
    }

    /** The clan the player left. */
    @NotNull
    public ClanAPI getClan() {
        return clan;
    }

    /** The player who left. May be offline (e.g. kicked while offline). */
    @NotNull
    public OfflinePlayer getPlayer() {
        return player;
    }

    /** Why the player left the clan. */
    @NotNull
    public Reason getReason() {
        return reason;
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