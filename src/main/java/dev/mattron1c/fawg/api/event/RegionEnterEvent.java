package dev.mattron1c.fawg.api.event;

import dev.mattron1c.fawg.core.region.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired when a player is entering a region. Cancelling keeps the player out / in. */
public final class RegionEnterEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Region region;
    private final Location to;
    private final boolean teleport;
    private boolean cancelled;

    public RegionEnterEvent(Player who, Region region, Location to, boolean teleport) {
        super(who);
        this.region = region; this.to = to; this.teleport = teleport;
    }

    public Region getRegion() { return region; }
    public Location getTo() { return to; }
    public boolean isTeleport() { return teleport; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
