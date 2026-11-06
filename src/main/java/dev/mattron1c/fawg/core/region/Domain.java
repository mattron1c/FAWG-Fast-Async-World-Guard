package dev.mattron1c.fawg.core.region;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A thread safe set of players (by UUID, plus legacy names) and permission groups. */
public final class Domain {
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<String> names = ConcurrentHashMap.newKeySet();
    private final Set<String> groups = ConcurrentHashMap.newKeySet();

    public boolean contains(Subject s) {
        if (s == null) return false;
        if (!players.isEmpty() && s.uuid() != null && players.contains(s.uuid())) return true;
        if (!names.isEmpty() && s.name() != null && (names.contains(s.name().toLowerCase(Locale.ROOT)) || names.contains("*"))) return true;
        if (!groups.isEmpty()) for (String g : groups) if (s.inGroup(g)) return true;
        return false;
    }

    public boolean contains(UUID id) { return players.contains(id); }

    public boolean addPlayer(UUID id) { return players.add(id); }
    public boolean removePlayer(UUID id) { return players.remove(id); }
    public boolean addName(String n) { return names.add(n.toLowerCase(Locale.ROOT)); }
    public boolean removeName(String n) { return names.remove(n.toLowerCase(Locale.ROOT)); }
    public boolean addGroup(String g) { return groups.add(g.toLowerCase(Locale.ROOT)); }
    public boolean removeGroup(String g) { return groups.remove(g.toLowerCase(Locale.ROOT)); }

    public Set<UUID> players() { return Collections.unmodifiableSet(players); }
    public Set<String> names() { return Collections.unmodifiableSet(names); }
    public Set<String> groups() { return Collections.unmodifiableSet(groups); }

    public int size() { return players.size() + names.size() + groups.size(); }
    public boolean isEmpty() { return players.isEmpty() && names.isEmpty() && groups.isEmpty(); }

    public void clear() { players.clear(); names.clear(); groups.clear(); }

    public void copyFrom(Domain o) {
        clear();
        players.addAll(o.players); names.addAll(o.names); groups.addAll(o.groups);
    }
}
