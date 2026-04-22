package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after a clan successfully upgrades to a new level.
 */
public class ClanLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ClanAPI clan;
    private final int previousLevel;
    private final int newLevel;

    public ClanLevelUpEvent(@NotNull ClanAPI clan, int previousLevel, int newLevel) {
        this.clan          = clan;
        this.previousLevel = previousLevel;
        this.newLevel      = newLevel;
    }

    /** The clan that levelled up. */
    @NotNull
    public ClanAPI getClan() {
        return clan;
    }

    /** The level before the upgrade. */
    public int getPreviousLevel() {
        return previousLevel;
    }

    /** The new level after the upgrade. */
    public int getNewLevel() {
        return newLevel;
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