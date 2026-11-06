package dev.mattron1c.fawg.core.flag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Global flag registry. Registration is thread safe; ids are dense and never reused. */
public final class FlagRegistry {
    private final Map<String, Flag<?>> byName = new ConcurrentHashMap<>();
    private final Map<String, Flag<?>> aliases = new ConcurrentHashMap<>();
    private volatile Flag<?>[] byId = new Flag<?>[0];
    private final Map<FlagCategory, List<Flag<?>>> byCategory = new EnumMap<>(FlagCategory.class);

    public synchronized <F extends Flag<?>> F register(F flag) {
        Flag<?> existing = byName.get(flag.name());
        if (existing != null) throw new IllegalStateException("Duplicate flag '" + flag.name() + "'");
        flag.id = byId.length;
        Flag<?>[] n = java.util.Arrays.copyOf(byId, byId.length + 1);
        n[flag.id] = flag;
        byId = n;
        byName.put(flag.name(), flag);
        byCategory.computeIfAbsent(flag.category(), k -> new ArrayList<>()).add(flag);
        return flag;
    }

    /** Registers only if no flag with that name exists; returns the registered instance. */
    public synchronized Flag<?> registerIfAbsent(Flag<?> flag) {
        Flag<?> e = byName.get(flag.name());
        return e != null ? e : register(flag);
    }

    public void alias(String alias, Flag<?> target) { aliases.put(alias.toLowerCase(Locale.ROOT), target); }

    public Flag<?> get(String name) {
        if (name == null) return null;
        String k = name.toLowerCase(Locale.ROOT);
        Flag<?> f = byName.get(k);
        return f != null ? f : aliases.get(k);
    }

    public Flag<?> byId(int id) {
        Flag<?>[] a = byId;
        return id >= 0 && id < a.length ? a[id] : null;
    }

    public int size() { return byId.length; }

    public List<Flag<?>> all() { return List.of(byId); }

    public synchronized List<Flag<?>> category(FlagCategory c) {
        List<Flag<?>> l = byCategory.get(c);
        return l == null ? Collections.emptyList() : List.copyOf(l);
    }

    public Collection<String> names() { return Collections.unmodifiableSet(byName.keySet()); }
}
