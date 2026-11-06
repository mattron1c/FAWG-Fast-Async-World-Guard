package dev.mattron1c.fawg.region;

import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.FlagMap;
import dev.mattron1c.fawg.core.flag.FlagRegistry;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Domain;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.shape.Shape;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** YAML storage. Writes are atomic (temp file + move) so a crash can never corrupt a region file. */
public final class RegionStorage {
    private final FlagRegistry flags;
    private final Logger log;

    public RegionStorage(FlagRegistry flags, Logger log) { this.flags = flags; this.log = log; }

    // ---------------------------------------------------------------------------------------- save
    /** Builds a plain map snapshot (safe to call from any thread). */
    public Map<String, Object> snapshot(RegionWorld w) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
        root.put("world", w.name());
        root.put("global", regionMap(w.global(), true));
        Map<String, Object> regions = new LinkedHashMap<>();
        List<Region> sorted = new ArrayList<>(w.regions());
        sorted.sort((a, b) -> a.id().compareTo(b.id()));
        for (Region r : sorted) regions.put(r.id(), regionMap(r, false));
        root.put("regions", regions);
        return root;
    }

    private Map<String, Object> regionMap(Region r, boolean global) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!global) {
            m.putAll(r.shape().serialize());
            m.put("priority", r.priority());
            if (r.parent() != null) m.put("parent", r.parent().id());
            if (!r.excludes().isEmpty()) {
                List<Object> ex = new ArrayList<>();
                for (Shape s : r.excludes()) ex.add(s.serialize());
                m.put("excludes", ex);
            }
            m.put("created", r.createdAt());
            if (!r.createdBy().isEmpty()) m.put("created-by", r.createdBy());
        }
        m.put("modified", r.modifiedAt());
        m.put("owners", domain(r.owners()));
        m.put("members", domain(r.members()));
        Map<String, Object> fm = new LinkedHashMap<>();
        for (Map.Entry<Flag<?>, Object> e : r.flags().snapshot(flags).entrySet()) {
            @SuppressWarnings("unchecked") Flag<Object> f = (Flag<Object>) e.getKey();
            fm.put(f.name(), f.marshal(e.getValue()));
            RegionGroup g = r.flags().group(f);
            if (g != null && g != f.defaultGroup()) fm.put(f.name() + "-group", g.id());
        }
        synchronized (r.flags().unknown()) { fm.putAll(r.flags().unknown()); }
        m.put("flags", fm);
        return m;
    }

    private static Map<String, Object> domain(Domain d) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (UUID u : d.players()) ids.add(u.toString());
        if (!ids.isEmpty()) m.put("unique-ids", ids);
        if (!d.names().isEmpty()) m.put("players", new ArrayList<>(d.names()));
        if (!d.groups().isEmpty()) m.put("groups", new ArrayList<>(d.groups()));
        return m;
    }

    /** Writes a snapshot to disk atomically. */
    public void write(File file, Map<String, Object> snapshot) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("FastAsyncWorldGuard regions for world '" + snapshot.get("world") + "'.",
                "Edit with /rg commands or the /fawg menu HUD. Manual edits: run /fawg reload afterwards."));
        for (Map.Entry<String, Object> e : snapshot.entrySet()) y.set(e.getKey(), e.getValue());
        String data = y.saveToString();
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        File tmp = new File(parent, file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------------------------------------------------------------------------------------- load
    public void load(File file, RegionWorld w) {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection g = y.getConfigurationSection("global");
        if (g != null) readCommon(g, w.global());
        ConfigurationSection rs = y.getConfigurationSection("regions");
        List<Region> loaded = new ArrayList<>();
        Map<Region, String> parents = new HashMap<>();
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    Region r = new Region(id, w.name(), Shape.fromMap(toMap(s)));
                    r.priority(s.getInt("priority", 0));
                    readCommon(s, r);
                    List<Shape> ex = new ArrayList<>();
                    for (Map<?, ?> em : s.getMapList("excludes")) ex.add(Shape.fromMap(deep(em)));
                    if (!ex.isEmpty()) r.excludes(ex);
                    r.createdAt(s.getLong("created", System.currentTimeMillis()));
                    r.createdBy(s.getString("created-by", ""));
                    String p = s.getString("parent");
                    if (p != null) parents.put(r, p);
                    r.modifiedAt(s.getLong("modified", r.createdAt()));
                    loaded.add(r);
                } catch (Exception ex) {
                    log.warning("Skipping invalid region '" + id + "' in " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
        Map<String, Region> byId = new HashMap<>();
        for (Region r : loaded) byId.put(r.id(), r);
        for (Map.Entry<Region, String> e : parents.entrySet()) {
            Region p = byId.get(e.getValue().toLowerCase(java.util.Locale.ROOT));
            if (p == null) { log.warning("Region " + e.getKey().id() + " has unknown parent " + e.getValue()); continue; }
            try { e.getKey().parent(p); } catch (IllegalArgumentException ex) { log.warning(ex.getMessage()); }
        }
        w.addAll(loaded);
        w.clearDirty();
    }

    private void readCommon(ConfigurationSection s, Region r) {
        readDomain(s.getConfigurationSection("owners"), r.owners());
        readDomain(s.getConfigurationSection("members"), r.members());
        ConfigurationSection f = s.getConfigurationSection("flags");
        if (f != null) readFlags(toMap(f), r.flags(), flags);
    }

    /** Reads flags (WorldGuard compatible "name" + "name-group" layout). Unknown flags are preserved. */
    public static void readFlags(Map<String, Object> raw, FlagMap target, FlagRegistry reg) {
        Map<String, Object> groups = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String k = e.getKey();
            if (k.endsWith("-group") && reg.get(k) == null) { groups.put(k.substring(0, k.length() - 6), e.getValue()); continue; }
            Flag<?> fl = reg.get(k);
            if (fl == null) { target.unknown().put(k, e.getValue()); continue; }
            setRaw(target, fl, e.getValue());
        }
        for (Map.Entry<String, Object> e : groups.entrySet()) {
            Flag<?> fl = reg.get(e.getKey());
            RegionGroup g = RegionGroup.parse(String.valueOf(e.getValue()));
            if (fl != null && g != null) target.setGroup(fl, g);
            else if (fl == null) target.unknown().put(e.getKey() + "-group", e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void setRaw(FlagMap m, Flag<T> f, Object raw) {
        T v = f.unmarshal(raw instanceof ConfigurationSection cs ? toMap(cs) : raw);
        if (v != null) m.set(f, v);
    }

    private static void readDomain(ConfigurationSection s, Domain d) {
        if (s == null) return;
        for (String u : s.getStringList("unique-ids")) {
            try { d.addPlayer(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
        }
        for (String n : s.getStringList("players")) d.addName(n);
        for (String g : s.getStringList("groups")) d.addGroup(g);
    }

    public static Map<String, Object> toMap(ConfigurationSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            m.put(k, v instanceof ConfigurationSection c ? toMap(c) : v);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deep(Map<?, ?> in) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : in.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> mm) v = deep(mm);
            else if (v instanceof ConfigurationSection cs) v = toMap(cs);
            m.put(String.valueOf(e.getKey()), v);
        }
        return m;
    }
}
