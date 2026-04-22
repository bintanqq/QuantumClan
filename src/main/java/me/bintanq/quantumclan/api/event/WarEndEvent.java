package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import me.bintanq.quantumclan.api.WarSessionAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a war session ends and rewards have been distributed.
 */
public class WarEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final WarSessionAPI session;
    private final ClanAPI winner;

    /**
     * @param session The completed session (state = ENDED)
     * @param winner  The winning clan, or {@code null} if the war ended in a draw
     */
    public WarEndEvent(@NotNull WarSessionAPI session, @Nullable ClanAPI winner) {
        this.session = session;
        this.winner  = winner;
    }

    /** The completed war session. */
    @NotNull
    public WarSessionAPI getSession() { return session; }

    /**
     * The winning clan, or {@code null} if the war ended without a winner
     * (draw, no participants, or manual end with no clear winner).
     */
    @Nullable
    public ClanAPI getWinner() { return winner; }

    /** Convenience: {@code true} if there is a winner. */
    public boolean hasWinner() { return winner != null; }

    @Override @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}