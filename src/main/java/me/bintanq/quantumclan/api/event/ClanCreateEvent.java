package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a player successfully creates a new clan.
 *
 * <p>This is a read-only informational event — it fires <em>after</em>
 * the clan has been persisted to the database and inserted into the cache.</p>
 *
 * <h3>Example listener</h3>
 * <pre>{@code
 * @EventHandler
 * public void onClanCreate(ClanCreateEvent e) {
 *     Bukkit.broadcastMessage(e.getCreator().getName() + " created clan " + e.getClan().getName());
 * }
 * }</pre>
 */
public class ClanCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanAPI clan;
    private final Player creator;

    public ClanCreateEvent(@NotNull ClanAPI clan, @NotNull Player creator) {
        this.clan    = clan;
        this.creator = creator;
    }

    /** The newly created clan. */
    @NotNull
    public ClanAPI getClan() {
        return clan;
    }

    /** The player who created the clan (also the initial leader). */
    @NotNull
    public Player getCreator() {
        return creator;
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