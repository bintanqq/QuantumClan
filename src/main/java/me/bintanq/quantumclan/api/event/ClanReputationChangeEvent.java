package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a clan's reputation changes (gain or loss from any source).
 */
public class ClanReputationChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The source of the reputation change. */
    public enum Source {
        BOUNTY_COMPLETE,
        WAR_WIN,
        ADMIN
    }

    private final ClanAPI clan;
    private final int previousReputation;
    private final int newReputation;
    private final int delta;
    private final Source source;

    public ClanReputationChangeEvent(@NotNull ClanAPI clan,
                                     int previousReputation,
                                     int newReputation,
                                     @NotNull Source source) {
        this.clan               = clan;
        this.previousReputation = previousReputation;
        this.newReputation      = newReputation;
        this.delta              = newReputation - previousReputation;
        this.source             = source;
    }

    @NotNull
    public ClanAPI getClan() { return clan; }

    public int getPreviousReputation() { return previousReputation; }

    public int getNewReputation() { return newReputation; }

    /** Signed delta (positive = gained, negative = lost). */
    public int getDelta() { return delta; }

    /** What caused the reputation change. */
    @NotNull
    public Source getSource() { return source; }

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}