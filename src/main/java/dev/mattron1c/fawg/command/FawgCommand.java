package dev.mattron1c.fawg.command;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.FlagCategory;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.query.FlagResolver;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.migrate.WorldGuardImporter;
import dev.mattron1c.fawg.region.PlayerSubject;
import dev.mattron1c.fawg.region.RegionService.Fail;
import dev.mattron1c.fawg.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** /fawg — admin tools, HUD, migration, backups, benchmarks and WorldGuard-style utilities. */
public final class FawgCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("menu", "help", "reload", "version", "import", "cleanup", "backup", "benchmark", "test", "flags", "stats",
            "god", "ungod", "heal", "slay", "stoplag", "allowfire", "stopfire", "report", "dumpflags");

    private final FAWG plugin;
    private final Text text;

    public FawgCommand(FAWG plugin) {
        this.plugin = plugin;
        this.text = plugin.text();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? (s instanceof Player ? "menu" : "help") : a[0].toLowerCase(Locale.ROOT);
        try {
            if (!sub.equals("help") && !sub.equals("version") && !s.hasPermission("fawg.admin." + sub) && !s.hasPermission("fawg.admin")) {
                if (!(sub.equals("menu") && s.hasPermission("fawg.region.menu")) && !(sub.equals("flags") && s.hasPermission("fawg.region.info"))) throw new Fail("error.no-permission");
            }
            switch (sub) {
                case "menu", "gui", "hud" -> plugin.menus().open(player(s), a.length > 1 ? a[1] : null);
                case "reload" -> { plugin.reloadAll(); text.send(s, "admin.reloaded", "count", plugin.regions().totalRegions(), "flags", Flags.REGISTRY.size()); }
                case "version", "about" -> text.send(s, "admin.version", "version", plugin.getPluginMeta().getVersion(), "flags", Flags.REGISTRY.size(),
                        "server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion(), "fawe", plugin.worldEdit() == null ? "not installed" : plugin.worldEdit().isFawe() ? "FastAsyncWorldEdit" : "WorldEdit");
                case "import" -> importCmd(s, a);
                case "cleanup" -> cleanup(s, a);
                case "backup" -> backup(s, a);
                case "benchmark", "bench" -> benchmark(s, a);
                case "test", "explain" -> test(s, a);
                case "flags" -> flags(s, a);
                case "stats", "report" -> stats(s);
                case "god", "ungod" -> god(s, a, sub.equals("god"));
                case "heal" -> heal(s, a);
                case "slay" -> slay(s, a);
                case "stoplag", "halt-activity" -> stoplag(s, a);
                case "dumpflags" -> dumpFlags(s);
                case "stopfire" -> { plugin.settings().fireDisabled = true; text.send(s, "admin.fire-stopped"); }
                case "allowfire" -> { plugin.settings().fireDisabled = false; text.send(s, "admin.fire-allowed"); }
                default -> help(s);
            }
        } catch (Fail f) {
            text.send(s, f.key, f.ph);
        }
        return true;
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        throw new Fail("error.player-only");
    }

    private void help(CommandSender s) {
        text.send(s, "help.admin-header");
        for (String line : text.raw("help.admin-lines").split("\n")) s.sendMessage(text.componentRaw(line, s));
        text.send(s, "help.admin-footer");
    }

    // ------------------------------------------------------------------------------------- migration
    private void importCmd(CommandSender s, String[] a) {
        WorldGuardImporter imp = new WorldGuardImporter(plugin);
        if (a.length < 2 || !a[1].equalsIgnoreCase("worldguard")) throw new Fail("usage.import");
        if (!imp.available()) throw new Fail("import.none-found", "path", imp.worldGuardFolder().getPath());
        String world = null;
        boolean replace = false;
        for (int i = 2; i < a.length; i++) {
            if (a[i].equalsIgnoreCase("--replace") || a[i].equalsIgnoreCase("-r")) replace = true;
            else world = a[i];
        }
        final String fw = world;
        final boolean fr = replace;
        text.send(s, "import.started", "worlds", String.join(", ", imp.worldsAvailable()));
        plugin.regions().backup("pre-import", f -> {
            WorldGuardImporter.Result r = imp.run(fw, fr);
            text.send(s, "import.done", "worlds", r.worlds(), "regions", r.regions(), "skipped", r.skipped(), "flags", r.flags(), "unknown", r.unknownFlags(), "locks", r.locks());
            for (String w : r.warnings().subList(0, Math.min(10, r.warnings().size()))) text.send(s, "import.warning", "warning", w);
            if (r.warnings().size() > 10) text.send(s, "import.more-warnings", "count", r.warnings().size() - 10);
            plugin.refreshAll();
            List<java.io.File> jars = imp.legacyJars();
            if (!jars.isEmpty()) text.send(s, "import.cleanup-hint", "jars", String.join(", ", jars.stream().map(java.io.File::getName).toList()));
        });
    }

    private void cleanup(CommandSender s, String[] a) {
        WorldGuardImporter imp = new WorldGuardImporter(plugin);
        List<java.io.File> jars = imp.legacyJars();
        if (jars.isEmpty()) { text.send(s, "cleanup.nothing"); return; }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
            text.send(s, "cleanup.confirm", "jars", String.join(", ", jars.stream().map(java.io.File::getName).toList()));
            return;
        }
        for (String line : imp.cleanup()) text.send(s, "cleanup.line", "line", line);
        text.send(s, "cleanup.restart");
    }

    private void backup(CommandSender s, String[] a) {
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "create";
        switch (op) {
            case "create" -> plugin.regions().backup(a.length > 2 ? a[2] : "manual", f -> {
                if (f == null) text.send(s, "backup.failed");
                else text.send(s, "backup.created", "file", f.getName());
            });
            case "list" -> {
                List<String> l = plugin.regions().backups();
                text.send(s, "backup.header", "count", l.size());
                for (String n : l.subList(0, Math.min(15, l.size()))) text.send(s, "backup.line", "file", n);
            }
            case "restore" -> {
                if (a.length < 3) throw new Fail("usage.backup");
                plugin.regions().backup("pre-restore", f -> {
                    if (plugin.regions().restore(a[2])) { plugin.refreshAll(); text.send(s, "backup.restored", "file", a[2]); }
                    else text.send(s, "backup.restore-failed", "file", a[2]);
                });
            }
            default -> throw new Fail("usage.backup");
        }
    }

    // ------------------------------------------------------------------------------------- benchmark
    private void benchmark(CommandSender s, String[] a) {
        int n = 200_000;
        if (a.length > 1) try { n = Math.max(1000, Math.min(5_000_000, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
        World w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        RegionWorld rw = plugin.regions().world(w);
        List<Region> rs = new ArrayList<>(rw.regions());
        Random rnd = new Random(42);
        int samples = 4096;
        Location[] pts = new Location[samples];
        for (int i = 0; i < samples; i++) {
            if (!rs.isEmpty() && rnd.nextBoolean()) {
                var sh = rs.get(rnd.nextInt(rs.size())).shape();
                int x = sh.minX() + rnd.nextInt(Math.max(1, Math.min(Integer.MAX_VALUE - 1, sh.maxX() - sh.minX() + 1)));
                int z = sh.minZ() + rnd.nextInt(Math.max(1, Math.min(Integer.MAX_VALUE - 1, sh.maxZ() - sh.minZ() + 1)));
                pts[i] = new Location(w, x, Math.max(w.getMinHeight(), Math.min(w.getMaxHeight() - 1, 64)), z);
            } else {
                pts[i] = new Location(w, rnd.nextInt(20000) - 10000, 64, rnd.nextInt(20000) - 10000);
            }
        }
        FlagResolver res = plugin.protection().resolver();
        // warm up
        for (int i = 0; i < 20000; i++) res.test(plugin.regions().query(pts[i & (samples - 1)]), Flags.BUILD, null);
        long t0 = System.nanoTime();
        int allow = 0;
        for (int i = 0; i < n; i++) if (res.test(plugin.regions().query(pts[i & (samples - 1)]), Flags.BUILD, null) == StateFlag.State.ALLOW) allow++;
        double fawgNs = (System.nanoTime() - t0) / (double) n;
        text.send(s, "bench.fawg", "ns", String.format("%.1f", fawgNs), "n", String.format("%,d", n), "regions", rw.size(), "allowed", allow);
        Double wgNs = worldGuardBench(pts, n);
        if (wgNs == null) text.send(s, "bench.no-worldguard");
        else {
            double gain = (wgNs - fawgNs) / wgNs * 100.0;
            text.send(s, "bench.worldguard", "ns", String.format("%.1f", wgNs), "faster", String.format("%.0f", gain), "ratio", String.format("%.1f", wgNs / fawgNs));
        }
        plugin.getConfig().set("runtime.last-benchmark-ns", fawgNs);
    }

    /** Runs the same query through WorldGuard (if installed) via reflection. Returns ns / query or null. */
    private Double worldGuardBench(Location[] pts, int n) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return null;
        try {
            Class<?> wgc = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgc.getMethod("getInstance").invoke(null);
            Object platform = wgc.getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> ba = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adapt = ba.getMethod("adapt", Location.class);
            Class<?> sf = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object build = Class.forName("com.sk89q.worldguard.protection.flags.Flags").getField("BUILD").get(null);
            Object arr = java.lang.reflect.Array.newInstance(sf, 1);
            java.lang.reflect.Array.set(arr, 0, build);
            Method test = null;
            for (Method m : query.getClass().getMethods()) {
                if (m.getName().equals("testState") && m.getParameterCount() == 3 && m.getParameterTypes()[2].isArray()
                        && m.getParameterTypes()[1].getSimpleName().equals("RegionAssociable")) { test = m; break; }
            }
            if (test == null) return null;
            Object[] wl = new Object[pts.length];
            for (int i = 0; i < pts.length; i++) wl[i] = adapt.invoke(null, pts[i]);
            for (int i = 0; i < 20000; i++) test.invoke(query, wl[i & (pts.length - 1)], null, arr);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) test.invoke(query, wl[i & (pts.length - 1)], null, arr);
            return (System.nanoTime() - t0) / (double) n;
        } catch (Throwable t) {
            plugin.getLogger().fine("WorldGuard benchmark failed: " + t);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------- debug
    private void test(CommandSender s, String[] a) {
        if (a.length < 2) throw new Fail("usage.test");
        Flag<?> f = Flags.REGISTRY.get(a[1]);
        if (f == null) throw new Fail("error.unknown-flag", "flag", a[1], "suggestions", "-");
        Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
        if (target == null) throw new Fail("error.unknown-player", "player", a.length > 2 ? a[2] : "?");
        ApplicableSet set = plugin.regions().query(target.getLocation());
        PlayerSubject sub = PlayerSubject.of(target);
        FlagResolver res = plugin.protection().resolver();
        text.send(s, "test.header", "flag", f.name(), "player", target.getName(), "regions", set.isEmpty() ? "__global__" : String.join(", ", set.ids()));
        if (f instanceof StateFlag sf) {
            for (StateFlag c : sf.chain()) {
                StateFlag.State v = res.regionValue(set, c, sub);
                text.send(s, "test.chain", "flag", c.name(), "value", v == null ? "unset" : v.name().toLowerCase(Locale.ROOT), "gated", c.membershipGated() ? "membership" : "");
            }
            StateFlag.State result = res.test(set, sf, sub);
            boolean bypass = plugin.protection().bypasses(target);
            text.send(s, "test.result", "value", result.name().toLowerCase(Locale.ROOT), "bypass", bypass ? "yes" : "no", "membership", res.membership(set, sub).name().toLowerCase(Locale.ROOT));
        } else {
            Object v = res.value(set, f, sub);
            @SuppressWarnings("unchecked") String shown = v == null ? "unset" : ((Flag<Object>) f).format(v);
            text.send(s, "test.result", "value", shown, "bypass", "-", "membership", "-");
        }
    }

    private void flags(CommandSender s, String[] a) {
        String q = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : null;
        int page = a.length > 2 ? parse(a[2], 1) : (a.length > 1 && q.matches("\\d+") ? parse(q, 1) : 1);
        if (q != null && q.matches("\\d+")) q = null;
        if (q == null) {
            text.send(s, "flaglist.categories", "count", Flags.REGISTRY.size(), "generated", FlagFamilies.generatedCount());
            for (FlagCategory c : FlagCategory.values()) {
                int n = Flags.REGISTRY.category(c).size();
                if (n > 0) text.send(s, "flaglist.category", "emoji", c.emoji, "name", c.display, "id", c.name().toLowerCase(Locale.ROOT), "count", n);
            }
            return;
        }
        List<Flag<?>> list = new ArrayList<>();
        FlagCategory cat = null;
        for (FlagCategory c : FlagCategory.values()) if (c.name().equalsIgnoreCase(q)) cat = c;
        if (cat != null) list.addAll(Flags.REGISTRY.category(cat));
        else for (Flag<?> f : Flags.REGISTRY.all()) if (f.name().contains(q) || f.description().toLowerCase(Locale.ROOT).contains(q)) list.add(f);
        list.sort(Comparator.comparing(Flag::name));
        int per = 12, pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(1, Math.min(pages, page));
        text.send(s, "flaglist.header", "query", q, "count", list.size(), "page", page, "pages", pages);
        for (int i = (page - 1) * per; i < Math.min(list.size(), page * per); i++) {
            Flag<?> f = list.get(i);
            text.send(s, "flaglist.line", "flag", f.name(), "type", f.typeName(), "desc", f.description());
        }
        text.send(s, "flaglist.footer", "query", q, "prev", Math.max(1, page - 1), "next", Math.min(pages, page + 1));
    }

    private void stats(CommandSender s) {
        int regions = 0, buckets = 0, large = 0;
        for (RegionWorld rw : plugin.regions().worlds()) { regions += rw.size(); buckets += rw.index().bucketCount(); large += rw.index().largeCount(); }
        double last = plugin.getConfig().getDouble("runtime.last-benchmark-ns", -1);
        text.send(s, "stats.body", "regions", regions, "worlds", plugin.regions().worlds().size(), "flags", Flags.REGISTRY.size(), "generated", FlagFamilies.generatedCount(),
                "buckets", buckets, "large", large, "sessions", plugin.sessions().all().size(), "bench", last < 0 ? "run /fawg benchmark" : String.format("%.1f ns", last),
                "fawe", plugin.worldEdit() == null ? "-" : plugin.worldEdit().isFawe() ? "FAWE" : "WorldEdit", "bedrock", plugin.bedrock().available() ? "Geyser/Floodgate" : "-");
    }

    // ------------------------------------------------------------------------------------- utilities
    private Player target(CommandSender s, String[] a) {
        if (a.length > 1) {
            Player p = Bukkit.getPlayerExact(a[1]);
            if (p == null) throw new Fail("error.unknown-player", "player", a[1]);
            return p;
        }
        return player(s);
    }

    private void god(CommandSender s, String[] a, boolean on) {
        Player p = target(s, a);
        p.setInvulnerable(on);
        text.send(s, on ? "util.god-on" : "util.god-off", "player", p.getName());
    }

    private void heal(CommandSender s, String[] a) {
        Player p = target(s, a);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        text.send(s, "util.healed", "player", p.getName());
    }

    private void slay(CommandSender s, String[] a) {
        Player p = target(s, a);
        p.setHealth(0);
        text.send(s, "util.slain", "player", p.getName());
    }

    private void stoplag(CommandSender s, String[] a) {
        boolean confirm = a.length > 1 && (a[1].equalsIgnoreCase("-c") || a[1].equalsIgnoreCase("confirm"));
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                boolean junk = e instanceof Item || (e instanceof LivingEntity le && !(e instanceof Tameable t && t.isTamed()) && le.customName() == null && !le.isPersistent() && le.getRemoveWhenFarAway());
                if (!junk) continue;
                if (confirm) e.remove();
                removed++;
            }
        }
        text.send(s, confirm ? "util.stoplag-done" : "util.stoplag-preview", "count", removed);
    }

    /** Writes a Markdown reference of every registered flag (used to generate docs/FLAGS.md). */
    private void dumpFlags(CommandSender s) {
        StringBuilder sb = new StringBuilder();
        sb.append("# FAWG flag reference\n\n");
        sb.append("Generated by `/fawg dumpflags` on ").append(Bukkit.getName()).append(' ').append(Bukkit.getMinecraftVersion())
          .append(" — **").append(Flags.REGISTRY.size()).append(" flags** (").append(FlagFamilies.generatedCount()).append(" generated from the server's own mob / block / item / effect lists).\n\n");
        sb.append("Set flags with `/rg flag <region> <flag> [-g group] <value>` or in the HUD (`/rg menu`).\n\n");
        for (FlagCategory c : FlagCategory.values()) {
            List<Flag<?>> l = new ArrayList<>(Flags.REGISTRY.category(c));
            if (l.isEmpty()) continue;
            l.sort(Comparator.comparing(Flag::name));
            sb.append("## ").append(c.emoji).append(' ').append(c.display).append(" (").append(l.size()).append(")\n\n");
            sb.append("| Flag | Type | Default | Description |\n|---|---|---|---|\n");
            for (Flag<?> f : l) {
                Object d = f.defaultValue();
                @SuppressWarnings("unchecked") String def = d == null ? "—" : ((Flag<Object>) f).format(d);
                String type = f.typeName();
                if (f instanceof StateFlag sf && sf.chainGated()) type += " · membership";
                if (f instanceof StateFlag sf && sf.parents().length > 0) type += " · falls back to `" + sf.parents()[0].name() + "`";
                sb.append("| `").append(f.name()).append("` | ").append(type).append(" | ").append(def).append(" | ").append(f.description().replace("|", "\\|")).append(" |\n");
            }
            sb.append('\n');
        }
        java.io.File out = new java.io.File(plugin.getDataFolder(), "FLAGS.md");
        try {
            java.nio.file.Files.writeString(out.toPath(), sb.toString());
            plugin.getLogger().info("Wrote " + Flags.REGISTRY.size() + " flags to " + out.getPath());
            if (s instanceof Player) text.sendRaw(s, "<prefix><gray>Wrote</gray> <white>" + Flags.REGISTRY.size() + "</white> <gray>flags to</gray> <white>" + out.getName() + "</white>");
        } catch (java.io.IOException e) {
            throw new Fail("error.internal", "error", e.getMessage());
        }
    }

    private static int parse(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return RegionCommand.filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "import" -> a.length == 2 ? RegionCommand.filter(List.of("worldguard"), a[1]) : RegionCommand.filter(new ArrayList<>(new WorldGuardImporter(plugin).worldsAvailable()) {{ add("--replace"); }}, a[a.length - 1]);
            case "cleanup" -> RegionCommand.filter(List.of("confirm"), a[1]);
            case "backup" -> a.length == 2 ? RegionCommand.filter(List.of("create", "list", "restore"), a[1]) : a[1].equalsIgnoreCase("restore") ? RegionCommand.filter(plugin.regions().backups(), a[2]) : List.of();
            case "test", "explain" -> a.length == 2 ? RegionCommand.filterLimit(Flags.REGISTRY.names(), a[1], 200) : null;
            case "flags" -> a.length == 2 ? RegionCommand.filter(Arrays.stream(FlagCategory.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).toList(), a[1]) : List.of();
            case "god", "ungod", "heal", "slay" -> null;
            case "benchmark" -> RegionCommand.filter(List.of("100000", "500000", "1000000"), a[1]);
            case "stoplag" -> RegionCommand.filter(List.of("-c"), a[1]);
            case "menu" -> null;
            default -> List.of();
        };
    }
}
