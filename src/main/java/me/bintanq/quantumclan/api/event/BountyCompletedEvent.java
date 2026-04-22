package me.bintanq.quantumclan.api.event;

import me.bintanq.quantumclan.api.ClanAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a bounty is successfully completed (hunter submits the head).
 */
public class BountyCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player hunter;
    private final UUID targetUuid;
    private final ClanAPI hunterClan;
    private final long amount;

    public BountyCompletedEvent(@NotNull Player hunter,
                                @NotNull UUID targetUuid,
                                @Nullable ClanAPI hunterClan,
                                long amount) {
        this.hunter     = hunter;
        this.targetUuid = targetUuid;
        this.hunterClan = hunterClan;
        this.amount     = amount;
    }

    /** The player who claimed the bounty reward. */
    @NotNull
    public Player getHunter() { return hunter; }

    /** UUID of the player whose bounty was claimed. */
    @NotNull
    public UUID getTargetUuid() { return targetUuid; }

    /** The hunter's clan, or {@code null} if the hunter is not in a clan. */
    @Nullable
    public ClanAPI getHunterClan() { return hunterClan; }

    /** The bounty reward amount that was paid out. */
    public long getAmount() { return amount; }

    @Override @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}