package dev.mattron1c.fawg.session;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public PlayerSession get(Player p) {
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            s = new PlayerSession(p);
            sessions.put(p.getUniqueId(), s);
        }
        return s;
    }

    public PlayerSession peek(UUID id) { return sessions.get(id); }

    public PlayerSession remove(UUID id) { return sessions.remove(id); }

    public Collection<PlayerSession> all() { return sessions.values(); }
}
