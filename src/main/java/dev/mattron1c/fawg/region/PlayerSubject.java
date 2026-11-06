package dev.mattron1c.fawg.region;

import dev.mattron1c.fawg.core.region.Subject;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Adapts Bukkit players to the core {@link Subject}. Group membership uses the "group.&lt;name&gt;" permission. */
public record PlayerSubject(UUID uuid, String name, Player online) implements Subject {
    public static PlayerSubject of(Player p) { return new PlayerSubject(p.getUniqueId(), p.getName(), p); }

    public static PlayerSubject of(OfflinePlayer p) {
        return new PlayerSubject(p.getUniqueId(), p.getName() == null ? p.getUniqueId().toString() : p.getName(), p.getPlayer());
    }

    @Override public boolean inGroup(String group) {
        return online != null && (online.hasPermission("group." + group) || online.hasPermission("fawg.group." + group));
    }
}
