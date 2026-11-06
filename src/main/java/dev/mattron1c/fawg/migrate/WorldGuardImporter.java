package dev.mattron1c.fawg.migrate;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.RegionStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Imports WorldGuard 6/7 YAML region files (plus WorldGuard-ExtraFlags(Plus) flags stored in them, and
 * WorldGuardRegionProtect's protected region lists) into FAWG. Works whether or not WorldGuard is still
 * installed — it only reads files.
 */
public final class WorldGuardImporter {
    public record Result(int worlds, int regions, int skipped, int flags, int unknownFlags, int locks, List<String> warnings) {}

    private static final Set<String> LEGACY_PLUGINS = Set.of("WorldGuard", "WorldGuardExtraFlags", "WorldGuardExtraFlagsPlus", "WorldGuardRegionProtect", "WGRegionEvents");

    private final FAWG plugin;

    public WorldGuardImporter(FAWG plugin) { this.plugin = plugin; }

    public File worldGuardFolder() { return new File(plugin.getDataFolder().getParentFile(), "WorldGuard"); }

    public boolean available() { return new File(worldGuardFolder(), "worlds").isDirectory(); }

    public boolean usesSql() {
        File cfg = new File(worldGuardFolder(), "config.yml");
        return cfg.exists() && YamlConfiguration.loadConfiguration(cfg).getBoolean("regions.sql.use", false);
    }

    public List<String> worldsAvailable() {
        List<String> l = new ArrayList<>();
        File[] dirs = new File(worldGuardFolder(), "worlds").listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) if (new File(d, "regions.yml").exists()) l.add(d.getName());
        return l;
    }

    /** Runs the import. onlyWorld may be null for all worlds. replace overwrites regions with the same id. */
    public Result run(String onlyWorld, boolean replace) {
        List<String> warnings = new ArrayList<>();
        int worlds = 0, regions = 0, skipped = 0, flags = 0, unknown = 0;
        if (usesSql()) warnings.add("WorldGuard is configured for MySQL storage. Run '/rg migratedb sql yaml' in WorldGuard first, then import again.");
        for (String w : worldsAvailable()) {
            if (onlyWorld != null && !onlyWorld.equalsIgnoreCase(w)) continue;
            File f = new File(new File(new File(worldGuardFolder(), "worlds"), w), "regions.yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection rs = y.getConfigurationSection("regions");
            if (rs == null) continue;
            worlds++;
            RegionWorld rw = plugin.regions().world(w);
            Map<String, Region> created = new LinkedHashMap<>();
            Map<String, String> parents = new HashMap<>();
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                String lid = id.toLowerCase(Locale.ROOT);
                try {
                    String type = s.getString("type", "cuboid").toLowerCase(Locale.ROOT);
                    Region target;
                    if (type.equals("global") || lid.equals(Region.GLOBAL_ID)) {
                        target = rw.global();
                        if (replace) { target.owners().clear(); target.members().clear(); target.flags().clear(); }
                    } else {
                        if (rw.get(lid) != null && !replace) { skipped++; continue; }
                        if (!Region.isValidId(lid)) {
                            String fixed = lid.replaceAll("[^a-z0-9_,'\\-+/]", "_");
                            warnings.add("Region '" + id + "' renamed to '" + fixed + "' (invalid characters).");
                            lid = fixed;
                        }
                        Shape shape = readShape(s, type);
                        target = new Region(lid, w, shape);
                        target.priority(s.getInt("priority", 0));
                        target.createdBy("WorldGuard import");
                        created.put(lid, target);
                        if (s.getString("parent") != null) parents.put(lid, s.getString("parent").toLowerCase(Locale.ROOT));
                    }
                    readDomain(s.getConfigurationSection("owners"), target.owners());
                    readDomain(s.getConfigurationSection("members"), target.members());
                    ConfigurationSection fs = s.getConfigurationSection("flags");
                    if (fs != null) {
                        Map<String, Object> raw = normalize(RegionStorage.toMap(fs));
                        int before = target.flags().unknown().size();
                        RegionStorage.readFlags(raw, target.flags(), Flags.REGISTRY);
                        int unk = target.flags().unknown().size() - before;
                        unknown += unk;
                        flags += raw.size() - unk;
                    }
                    if (target != rw.global()) regions++;
                } catch (Exception ex) {
                    warnings.add("Could not import '" + id + "' in " + w + ": " + ex.getMessage());
                }
            }
            for (Map.Entry<String, String> e : parents.entrySet()) {
                Region child = created.get(e.getKey());
                Region parent = created.containsKey(e.getValue()) ? created.get(e.getValue()) : rw.get(e.getValue());
                if (child != null && parent != null) {
                    try { child.parent(parent); } catch (IllegalArgumentException ex) { warnings.add(ex.getMessage()); }
                }
            }
            if (replace) for (String id : created.keySet()) rw.remove(id, false);
            rw.addAll(created.values());
            rw.markDirty();
        }
        int locks = importRegionProtect(warnings);
        plugin.regions().saveDirtyAsync();
        if (plugin.worldListener() != null) plugin.worldListener().refreshAllChunkTickets();
        return new Result(worlds, regions, skipped, flags, unknown, locks, warnings);
    }

    private Shape readShape(ConfigurationSection s, String type) {
        if (type.equals("poly2d") || type.equals("polygon")) {
            List<int[]> pts = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("points")) pts.add(new int[]{num(m.get("x")), num(m.get("z"))});
            return Shape.Polygon.of(pts, s.getInt("min-y"), s.getInt("max-y"));
        }
        ConfigurationSection a = s.getConfigurationSection("min"), b = s.getConfigurationSection("max");
        if (a == null || b == null) throw new IllegalArgumentException("missing min / max");
        return Shape.Cuboid.of(num(a.get("x")), num(a.get("y")), num(a.get("z")), num(b.get("x")), num(b.get("y")), num(b.get("z")));
    }

    private static int num(Object o) {
        if (o instanceof Number n) return (int) Math.floor(n.doubleValue());
        return o == null ? 0 : (int) Math.floor(Double.parseDouble(o.toString()));
    }

    private static void readDomain(ConfigurationSection s, dev.mattron1c.fawg.core.region.Domain d) {
        if (s == null) return;
        for (String u : s.getStringList("unique-ids")) {
            try { d.addPlayer(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
        }
        for (String n : s.getStringList("players")) d.addName(n);
        for (String g : s.getStringList("groups")) d.addGroup(g);
    }

    /** Converts ExtraFlags(Plus) map based values (effects / sounds) to FAWG's compact string format. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            String k = e.getKey();
            if ((k.equals("give-effects") || k.equals("blocked-effects") || k.equals("play-sounds")) && v instanceof Collection<?> c) {
                List<String> conv = new ArrayList<>();
                for (Object o : c) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> mm = RegionStorage.deep(m);
                        if (k.equals("play-sounds")) {
                            Object snd = mm.getOrDefault("sound", mm.get("name"));
                            Object interval = mm.getOrDefault("interval", mm.getOrDefault("delay", 10));
                            conv.add(snd + ":" + interval);
                        } else {
                            Object type = mm.getOrDefault("type", mm.getOrDefault("potion", mm.get("effect")));
                            Object amp = mm.getOrDefault("amplifier", 0);
                            Object hide = mm.getOrDefault("hide-particles", mm.getOrDefault("hideParticles", false));
                            conv.add(String.valueOf(type).toLowerCase(Locale.ROOT).replace("minecraft:", "") + ":" + amp + ":" + !Boolean.parseBoolean(String.valueOf(hide)));
                        }
                    } else conv.add(String.valueOf(o).toLowerCase(Locale.ROOT).replace("minecraft:", ""));
                }
                v = conv;
            }
            // old ExtraFlags used "godmode: true" etc. — boolean values are understood by state flags already
            out.put(k, v);
        }
        return out;
    }

    /** Reads WorldGuardRegionProtect's config and applies region-lock levels. */
    private int importRegionProtect(List<String> warnings) {
        File cfg = new File(plugin.getDataFolder().getParentFile(), "WorldGuardRegionProtect/config.yml");
        if (!cfg.exists()) return 0;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(cfg);
        int n = 0;
        n += applyLocks(y.getConfigurationSection("wgRegionProtect.regionProtect"), ProtectLevel.SERVER, warnings);
        n += applyLocks(y.getConfigurationSection("wgRegionProtect.playerRegionProtect"), ProtectLevel.PLAYER, warnings);
        n += applyLocks(y.getConfigurationSection("wgRegionProtect.regionProtectAllow"), ProtectLevel.BREAK_ALLOWED, warnings);
        n += applyLocks(y.getConfigurationSection("wgRegionProtect.regionProtectOnlyBreakAllow"), ProtectLevel.BREAK_ONLY, warnings);
        return n;
    }

    private int applyLocks(ConfigurationSection s, ProtectLevel lvl, List<String> warnings) {
        if (s == null) return 0;
        int n = 0;
        for (String world : s.getKeys(false)) {
            RegionWorld rw = plugin.regions().world(world);
            for (String id : s.getStringList(world)) {
                Region r = rw.get(id);
                if (r == null) { warnings.add("RegionProtect lists unknown region '" + id + "' in " + world); continue; }
                r.flags().set(Flags.REGION_LOCK, lvl);
                rw.markDirty();
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------------------------- cleanup
    /** Finds installed legacy WorldGuard (+ addon) jars by reading their plugin.yml. */
    public List<File> legacyJars() {
        List<File> out = new ArrayList<>();
        File[] jars = plugin.getDataFolder().getParentFile().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) return out;
        for (File j : jars) {
            try (JarFile jf = new JarFile(j)) {
                ZipEntry e = jf.getEntry("plugin.yml");
                if (e == null) e = jf.getEntry("paper-plugin.yml");
                if (e == null) continue;
                YamlConfiguration y = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(jf.getInputStream(e), java.nio.charset.StandardCharsets.UTF_8));
                if (LEGACY_PLUGINS.contains(y.getString("name", ""))) out.add(j);
            } catch (IOException ignored) { }
        }
        return out;
    }

    /** Moves legacy jars into plugins/FastAsyncWorldGuard/legacy-worldguard. Effective after a restart. */
    public List<String> cleanup() {
        List<String> report = new ArrayList<>();
        File dest = new File(plugin.getDataFolder(), "legacy-worldguard");
        if (!dest.exists() && !dest.mkdirs()) { report.add("✖ could not create " + dest); return report; }
        for (File j : legacyJars()) {
            try {
                Files.move(j.toPath(), new File(dest, j.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                report.add("✔ moved " + j.getName());
            } catch (IOException ex) {
                report.add("✖ " + j.getName() + " is locked by the OS (" + ex.getClass().getSimpleName() + ") — stop the server and delete it manually");
            }
        }
        if (report.isEmpty()) report.add("✔ no WorldGuard jars found");
        return report;
    }

    public boolean worldGuardRunning() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public static Flag<?> flag(String n) { return Flags.REGISTRY.get(n); }
}
