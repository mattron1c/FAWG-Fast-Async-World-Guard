package dev.mattron1c.fawg.api.event;

import dev.mattron1c.fawg.core.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before a region is created, removed, redefined, or has a flag / member / priority / parent changed. */
public final class RegionChangeEvent extends Event implements Cancellable {
    public enum Type { CREATE, REMOVE, REDEFINE, FLAG, MEMBERS, OWNERS, PRIORITY, PARENT, RENAME, IMPORT }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Region region;
    private final Type type;
    private final CommandSender actor;
    private final String detail;
    private boolean cancelled;

    public RegionChangeEvent(Region region, Type type, CommandSender actor, String detail) {
        super(!Bukkit.isPrimaryThread());
        this.region = region; this.type = type; this.actor = actor; this.detail = detail;
    }

    public Region getRegion() { return region; }
    public Type getType() { return type; }
    public CommandSender getActor() { return actor; }
    public String getDetail() { return detail; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
