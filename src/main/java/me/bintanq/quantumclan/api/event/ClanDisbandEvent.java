package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a clan is disbanded and removed from the cache.
 *
 * <p>Note: the clan data snapshot is captured at the moment of disbanding.
 * By the time this event fires the clan no longer exists in the cache —
 * {@link me.bintanq.quantumclan.api.QuantumClanAPI#getClanById(String)} will
 * return {@code null} for this clan's ID.</p>
 */
public class ClanDisbandEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanAPI clan;
    private final Player disbandedBy;

    /**
     * @param clan       Snapshot of the clan at the moment of disbanding
     * @param disbandedBy The player who triggered the disband, or {@code null} for admin/console
     */
    public ClanDisbandEvent(@NotNull ClanAPI clan, @Nullable Player disbandedBy) {
        this.clan        = clan;
        this.disbandedBy = disbandedBy;
    }

    /** Snapshot of the disbanded clan. */
    @NotNull
    public ClanAPI getClan() {
        return clan;
    }

    /**
     * The player who disbanded the clan, or {@code null} if disbanded by an admin command
     * that was run from console.
     */
    @Nullable
    public Player getDisbandedBy() {
        return disbandedBy;
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