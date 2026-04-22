package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import me.bintanq.quantumclan.api.WarSessionAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a war session transitions from COUNTDOWN to ACTIVE
 * and all participants have been teleported to the arena.
 */
public class WarStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final WarSessionAPI session;

    public WarStartEvent(@NotNull WarSessionAPI session) {
        this.session = session;
    }

    /** The war session that just started. */
    @NotNull
    public WarSessionAPI getSession() { return session; }

    @Override @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}