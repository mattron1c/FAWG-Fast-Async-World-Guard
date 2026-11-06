package dev.mattron1c.fawg.command;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.LocationFlag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Domain;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.PlayerSubject;
import dev.mattron1c.fawg.region.RegionService;
import dev.mattron1c.fawg.region.RegionService.Fail;
import dev.mattron1c.fawg.select.Selection;
import dev.mattron1c.fawg.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** /region (/rg, /regions) — WorldGuard compatible sub commands plus FAWG extras. */
public final class RegionCommand implements TabExecutor {
    private final FAWG plugin;
    private final RegionService svc;
    private final Text text;
    private final Map<String, BiConsumer<CommandSender, String[]>> handlers = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> perms = new LinkedHashMap<>();

    public RegionCommand(FAWG plugin) {
        this.plugin = plugin;
        this.svc = plugin.service();
        this.text = plugin.text();
        reg("define", "define", this::define, "d", "create", "def");
        reg("redefine", "redefine", this::redefine, "update", "move");
        reg("claim", "claim", this::claim);
        reg("select", "select", this::select, "sel", "s");
        reg("info", "info", this::info, "i");
        reg("here", "info", this::here);
        reg("near", "info", this::near, "nearby");
        reg("list", "list", this::list, "ls");
        reg("search", "list", this::search, "find");
        reg("addowner", "addowner", (s, a) -> addMembers(s, a, true), "ao");
        reg("removeowner", "removeowner", (s, a) -> removeMembers(s, a, true), "ro", "remowner");
        reg("addmember", "addmember", (s, a) -> addMembers(s, a, false), "am", "addmem");
        reg("removemember", "removemember", (s, a) -> removeMembers(s, a, false), "rm", "remmember", "removemem", "remmem");
        reg("flag", "flag", this::flag, "f");
        reg("flags", "info", this::flags);
        reg("setpriority", "setpriority", this::priority, "priority", "pri");
        reg("setparent", "setparent", this::parent, "parent", "par");
        reg("remove", "remove", this::remove, "delete", "del", "rem");
        reg("teleport", "teleport", this::teleport, "tp");
        reg("toggle-bypass", "bypass", this::bypass, "bypass");
        reg("load", "load", this::load, "reload");
        reg("save", "save", this::save, "write");
        reg("migratedb", "migrate", this::migratedb);
        reg("migrateuuid", "migrate", this::migrateuuid);
        reg("migrateheights", "migrate", this::migrateheights);
        reg("wand", "wand", this::wand, "w");
        reg("pos1", "wand", (s, a) -> pos(s, true), "p1");
        reg("pos2", "wand", (s, a) -> pos(s, false), "p2");
        reg("seltype", "wand", this::seltype, "selection-type", "shape");
        reg("expand", "wand", (s, a) -> modify(s, a, 0), "grow");
        reg("contract", "wand", (s, a) -> modify(s, a, 1), "shrink");
        reg("shift", "wand", (s, a) -> modify(s, a, 2));
        reg("clearsel", "wand", this::clearsel, "desel", "deselect");
        reg("selinfo", "wand", this::selinfo, "size");
        reg("visualize", "visualize", this::visualize, "vis", "show", "highlight");
        reg("hide", "visualize", this::hide);
        reg("rename", "rename", this::rename);
        reg("copyflags", "flag", this::copyflags, "cf");
        reg("clearflags", "flag", this::clearflags, "resetflags");
        reg("preset", "flag", this::preset, "template");
        reg("exclude", "redefine", this::exclude, "carve");
        reg("lock", "lock", this::lock, "protect");
        reg("unlock", "lock", (s, a) -> lockLevel(s, a, ProtectLevel.NONE), "unprotect");
        reg("setteleport", "flag", (s, a) -> setLoc(s, a, Flags.TELE_LOC), "settp");
        reg("setspawn", "flag", (s, a) -> setLoc(s, a, Flags.SPAWN_LOC));
        reg("count", "list", this::count);
        reg("menu", "menu", (s, a) -> plugin.menus().open(s instanceof Player p ? p : null, a.length > 1 ? a[1] : null), "gui", "hud", "ui");
        reg("help", "", this::help, "?");
    }

    private void reg(String name, String perm, BiConsumer<CommandSender, String[]> h, String... al) {
        handlers.put(name, h);
        perms.put(name, perm);
        for (String a : al) aliases.put(a, name);
    }

    // =========================================================================================== dispatch
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && plugin.getConfig().getBoolean("commands.bare-command-opens-menu", true) && p.hasPermission("fawg.region.menu")) plugin.menus().open(p, null);
            else help(sender, args);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        sub = aliases.getOrDefault(sub, sub);
        BiConsumer<CommandSender, String[]> h = handlers.get(sub);
        if (h == null) { text.send(sender, "error.unknown-subcommand", "sub", args[0]); return true; }
        String perm = perms.get(sub);
        if (!perm.isEmpty() && !hasAny(sender, perm)) { text.send(sender, "error.no-permission"); return true; }
        try {
            h.accept(sender, args);
        } catch (Fail f) {
            text.send(sender, f.key, f.ph);
        } catch (Exception e) {
            text.send(sender, "error.internal", "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Command error", e);
        }
        return true;
    }

    /** True when the sender has the permission for any region, own or member regions. */
    private static boolean hasAny(CommandSender s, String perm) {
        return s.hasPermission("fawg.region." + perm) || s.hasPermission("fawg.region." + perm + ".own") || s.hasPermission("fawg.region." + perm + ".member");
    }

    // =========================================================================================== helpers
    private static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        throw new Fail("error.player-only");
    }

    private World world(CommandSender s, Args a) {
        String w = a.value('w');
        if (w != null) {
            World ww = Bukkit.getWorld(w);
            if (ww == null) throw new Fail("error.unknown-world", "world", w);
            return ww;
        }
        if (s instanceof Player p) return p.getWorld();
        return Bukkit.getWorlds().get(0);
    }

    private Region region(CommandSender s, World w, String id) {
        if (id == null) {
            if (!(s instanceof Player p)) throw new Fail("error.specify-region");
            ApplicableSet set = plugin.regions().query(p.getLocation());
            if (set.isEmpty()) throw new Fail("error.not-in-region");
            return set.highest();
        }
        Region r = plugin.regions().world(w).get(id);
        if (r == null) throw new Fail("error.unknown-region", "id", id);
        return r;
    }

    private Shape selectionShape(Player p) {
        Selection sel = plugin.selections().peek(p);
        boolean preferWe = plugin.getConfig().getBoolean("selection.prefer-worldedit", false);
        Shape we = plugin.worldEdit() != null ? plugin.worldEdit().selection(p) : null;
        if (sel != null && sel.complete() && p.getWorld().getName().equals(sel.world) && !(preferWe && we != null)) return sel.toShape();
        if (we != null) return we;
        if (sel != null && !sel.complete()) throw new Fail("selection.incomplete", "info", sel.describe());
        throw new Fail("selection.none");
    }

    private static String fmtShape(Shape s) {
        return s.type() + " · (" + s.minX() + ", " + s.minY() + ", " + s.minZ() + ") → (" + s.maxX() + ", " + s.maxY() + ", " + s.maxZ() + ")";
    }

    private String names(Domain d) {
        List<String> l = new ArrayList<>();
        for (UUID u : d.players()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            l.add(op.getName() == null ? u.toString().substring(0, 8) : op.getName());
        }
        l.addAll(d.names());
        for (String g : d.groups()) l.add("g:" + g);
        return l.isEmpty() ? "-" : String.join(", ", l);
    }

    // =========================================================================================== create / edit
    private void define(CommandSender s, String[] raw) {
        Player p = player(s);
        Args a = Args.parse(raw, 1, "", "gn", -1);
        if (a.size() < 1) throw new Fail("usage.define");
        if (!s.hasPermission("fawg.region.define")) throw new Fail("error.no-permission");
        Shape shape = a.has('g') ? null : selectionShape(p);
        if (a.has('g')) throw new Fail("error.global-not-allowed");
        if (!a.has('n') && plugin.getConfig().getBoolean("regions.auto-expand-vertical", false) && shape instanceof Shape.Cuboid c && c.maxY() - c.minY() < 2) {
            shape = shape.withHeight(p.getWorld().getMinHeight(), p.getWorld().getMaxHeight() - 1);
        }
        Region r = svc.define(s, p.getWorld(), a.get(0), shape, false, a.pos.subList(1, a.size()));
        text.send(s, "region.created", "id", r.id(), "shape", fmtShape(r.shape()), "volume", String.format("%,d", r.shape().volume()));
        plugin.visualizer().showRegion(p, r, 10);
    }

    private void claim(CommandSender s, String[] raw) {
        Player p = player(s);
        if (raw.length < 2) throw new Fail("usage.claim");
        Shape shape = selectionShape(p);
        if (plugin.getConfig().getBoolean("claims.auto-expand-vertical", true)) shape = shape.withHeight(p.getWorld().getMinHeight(), p.getWorld().getMaxHeight() - 1);
        Region r = svc.define(s, p.getWorld(), raw[1], shape, true, null);
        text.send(s, "region.claimed", "id", r.id(), "volume", String.format("%,d", r.shape().volume()));
        plugin.visualizer().showRegion(p, r, 10);
    }

    private void redefine(CommandSender s, String[] raw) {
        Player p = player(s);
        if (raw.length < 2) throw new Fail("usage.redefine");
        Region r = region(s, p.getWorld(), raw[1]);
        Shape shape = selectionShape(p);
        svc.redefine(s, r, shape);
        text.send(s, "region.redefined", "id", r.id(), "shape", fmtShape(shape));
        plugin.visualizer().showRegion(p, r, 10);
    }

    private void remove(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "fu", -1);
        if (a.size() < 1) throw new Fail("usage.remove");
        Region r = region(s, world(s, a), a.get(0));
        svc.remove(s, r, a.has('f'));
        text.send(s, "region.removed", "id", r.id());
    }

    private void rename(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        if (a.size() < 2) throw new Fail("usage.rename");
        Region r = region(s, world(s, a), a.get(0));
        Region n = svc.rename(s, r, a.get(1));
        text.send(s, "region.renamed", "old", r.id(), "id", n.id());
    }

    private void priority(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        if (a.size() < 2) throw new Fail("usage.priority");
        Region r = region(s, world(s, a), a.get(0));
        int p;
        try { p = Integer.parseInt(a.get(1)); } catch (NumberFormatException e) { throw new Fail("error.not-a-number", "value", a.get(1)); }
        svc.priority(s, r, p);
        text.send(s, "region.priority", "id", r.id(), "priority", p);
    }

    private void parent(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        if (a.size() < 1) throw new Fail("usage.parent");
        World w = world(s, a);
        Region r = region(s, w, a.get(0));
        Region parent = a.size() > 1 ? region(s, w, a.get(1)) : null;
        svc.parent(s, r, parent);
        if (parent == null) text.send(s, "region.parent-cleared", "id", r.id());
        else text.send(s, "region.parent", "id", r.id(), "parent", parent.id());
    }

    private void addMembers(CommandSender s, String[] raw, boolean owners) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        if (a.size() < 2) throw new Fail(owners ? "usage.addowner" : "usage.addmember");
        Region r = region(s, world(s, a), a.get(0));
        List<String> added = svc.addMembers(s, r, owners, a.pos.subList(1, a.size()));
        text.send(s, owners ? "members.owners-added" : "members.members-added", "id", r.id(), "players", String.join(", ", added));
    }

    private void removeMembers(CommandSender s, String[] raw, boolean owners) {
        Args a = Args.parse(raw, 1, "w", "a", -1);
        if (a.size() < 1 || (a.size() < 2 && !a.has('a'))) throw new Fail(owners ? "usage.removeowner" : "usage.removemember");
        Region r = region(s, world(s, a), a.get(0));
        int n = svc.removeMembers(s, r, owners, a.pos.subList(1, a.size()), a.has('a'));
        text.send(s, owners ? "members.owners-removed" : "members.members-removed", "id", r.id(), "count", n);
    }

    // =========================================================================================== flags
    private void flag(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "wgh", "e", 2);
        if (a.size() < 2) throw new Fail("usage.flag");
        Region r = region(s, world(s, a), a.get(0));
        Flag<?> f = Flags.REGISTRY.get(a.get(1));
        if (f == null) {
            List<String> close = closest(a.get(1));
            throw new Fail("error.unknown-flag", "flag", a.get(1), "suggestions", close.isEmpty() ? "-" : String.join(", ", close));
        }
        RegionGroup group = null;
        if (a.value('g') != null) {
            group = RegionGroup.parse(a.value('g'));
            if (group == null) throw new Fail("error.unknown-group", "group", a.value('g'));
        }
        String value = a.rest(2);
        if (value == null && group != null && !a.has('e')) {
            svc.require(s, r, "flag");
            r.flags().setGroup(f, group);
            plugin.regions().markDirty(r);
            text.send(s, "flag.group-set", "id", r.id(), "flag", f.name(), "group", group.id());
            return;
        }
        if (a.has('e')) value = "";
        if (value == null || (value.isEmpty() && !(f instanceof dev.mattron1c.fawg.core.flag.StringFlag || f instanceof dev.mattron1c.fawg.core.flag.StringSetFlag))) {
            svc.setFlag(s, r, f, null, null);
            text.send(s, "flag.cleared", "id", r.id(), "flag", f.name());
            return;
        }
        svc.setFlag(s, r, f, value, group);
        Object v = r.flags().get(f);
        @SuppressWarnings("unchecked") String shown = v == null ? "unset" : ((Flag<Object>) f).format(v);
        text.send(s, "flag.set", "id", r.id(), "flag", f.name(), "value", shown, "group", group == null ? "" : " (" + group.id() + ")");
    }

    private List<String> closest(String in) {
        String k = in.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String n : Flags.REGISTRY.names()) if (n.contains(k) || k.contains(n)) out.add(n);
        out.sort(Comparator.comparingInt(String::length));
        return out.subList(0, Math.min(5, out.size()));
    }

    private void flags(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "wh", "", -1);
        Region r = region(s, world(s, a), a.get(0));
        svc.require(s, r, "info");
        int page = a.size() > 1 ? parseInt(a.get(1), 1) : a.intValue('h', 1);
        List<Map.Entry<Flag<?>, Object>> set = new ArrayList<>(r.flags().snapshot(Flags.REGISTRY).entrySet());
        set.sort(Comparator.comparing(e -> e.getKey().name()));
        int per = 12, pages = Math.max(1, (set.size() + per - 1) / per);
        page = Math.max(1, Math.min(pages, page));
        text.send(s, "flags.header", "id", r.id(), "count", set.size(), "page", page, "pages", pages);
        for (int i = (page - 1) * per; i < Math.min(set.size(), page * per); i++) {
            @SuppressWarnings("unchecked") Flag<Object> f = (Flag<Object>) set.get(i).getKey();
            RegionGroup g = r.flags().group(f);
            text.send(s, "flags.line", "id", r.id(), "flag", f.name(), "value", f.format(set.get(i).getValue()), "group", g == null ? "" : " -g " + g.id(), "desc", f.description());
        }
        if (!r.flags().unknown().isEmpty()) text.send(s, "flags.unknown", "flags", String.join(", ", r.flags().unknown().keySet()));
        text.send(s, "flags.footer", "id", r.id(), "prev", Math.max(1, page - 1), "next", Math.min(pages, page + 1));
    }

    private void copyflags(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "r", -1);
        if (a.size() < 2) throw new Fail("usage.copyflags");
        World w = world(s, a);
        Region from = region(s, w, a.get(0)), to = region(s, w, a.get(1));
        svc.copyFlags(s, from, to, a.has('r'));
        text.send(s, "flag.copied", "from", from.id(), "id", to.id());
    }

    private void clearflags(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        Region r = region(s, world(s, a), a.get(0));
        svc.clearFlags(s, r);
        text.send(s, "flag.all-cleared", "id", r.id());
    }

    private void preset(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "", -1);
        if (a.size() < 2) {
            var sec = plugin.getConfig().getConfigurationSection("presets");
            throw new Fail("usage.preset", "presets", sec == null ? "-" : String.join(", ", sec.getKeys(false)));
        }
        Region r = region(s, world(s, a), a.get(0));
        int n = svc.preset(s, r, a.get(1));
        text.send(s, "flag.preset", "id", r.id(), "preset", a.get(1), "count", n);
    }

    private void setLoc(CommandSender s, String[] raw, LocationFlag f) {
        Player p = player(s);
        Region r = region(s, p.getWorld(), raw.length > 1 ? raw[1] : null);
        svc.setFlag(s, r, f, "here", null);
        text.send(s, "flag.set", "id", r.id(), "flag", f.name(), "value", "here", "group", "");
    }

    // =========================================================================================== info
    private void info(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "w", "su", -1);
        Region r = region(s, world(s, a), a.get(0));
        svc.require(s, r, "info");
        sendInfo(s, r, a.has('u'));
        if (s instanceof Player p) {
            if (a.has('s')) { plugin.selections().get(p).from(r.world(), r.shape()); plugin.visualizer().showSelection(p); }
            if (!r.isGlobal() && plugin.getConfig().getBoolean("visualizer.show-on-info", true)) plugin.visualizer().showRegion(p, r, plugin.getConfig().getInt("visualizer.info-seconds", 8));
        }
    }

    public void sendInfo(CommandSender s, Region r, boolean uuids) {
        String parents = "-";
        if (r.parent() != null) {
            List<String> chain = new ArrayList<>();
            for (Region c = r.parent(); c != null; c = c.parent()) chain.add(c.id());
            parents = String.join(" → ", chain);
        }
        ProtectLevel lock = r.flags().get(Flags.REGION_LOCK);
        text.send(s, "info.header", "id", r.id(), "name", r.displayName(), "world", r.world());
        if (!r.isGlobal()) {
            text.send(s, "info.shape", "type", r.shape().type(), "priority", r.priority(), "parent", parents);
            Shape sh = r.shape();
            text.send(s, "info.bounds", "min", sh.minX() + ", " + sh.minY() + ", " + sh.minZ(), "max", sh.maxX() + ", " + sh.maxY() + ", " + sh.maxZ(),
                    "volume", String.format("%,d", sh.volume()), "excludes", r.excludes().size());
        }
        text.send(s, "info.owners", "owners", uuids ? r.owners().players().toString() : names(r.owners()));
        text.send(s, "info.members", "members", uuids ? r.members().players().toString() : names(r.members()));
        StringBuilder fl = new StringBuilder();
        int shown = 0;
        Map<Flag<?>, Object> snap = r.flags().snapshot(Flags.REGISTRY);
        for (Map.Entry<Flag<?>, Object> e : snap.entrySet()) {
            if (shown++ >= 10) break;
            @SuppressWarnings("unchecked") Flag<Object> f = (Flag<Object>) e.getKey();
            if (fl.length() > 0) fl.append(", ");
            fl.append(f.name()).append(": ").append(f.format(e.getValue()));
        }
        if (snap.size() > 10) fl.append(" … +").append(snap.size() - 10);
        text.send(s, "info.flags", "count", snap.size(), "flags", fl.length() == 0 ? "-" : fl.toString());
        if (lock != null && lock != ProtectLevel.NONE) text.send(s, "info.lock", "level", lock.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        text.send(s, "info.meta", "created", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.createdAt())), "by", r.createdBy().isEmpty() ? "-" : r.createdBy(),
                "modified", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.modifiedAt())));
        text.send(s, "info.buttons", "id", r.id());
    }

    private void here(CommandSender s, String[] raw) {
        Player p = player(s);
        ApplicableSet set = plugin.regions().query(p.getLocation());
        if (set.isEmpty()) { text.send(s, "here.none", "world", p.getWorld().getName()); return; }
        text.send(s, "here.header", "count", set.size());
        for (Region r : set) text.send(s, "here.line", "id", r.id(), "priority", r.priority(), "owner", r.isOwner(PlayerSubject.of(p)) ? "owner" : r.isMember(PlayerSubject.of(p)) ? "member" : "-");
        StateFlag.State build = plugin.protection().resolver().test(set, Flags.BUILD, PlayerSubject.of(p));
        text.send(s, "here.build", "state", build.name().toLowerCase(Locale.ROOT));
        for (Region r : set) plugin.visualizer().showRegion(p, r, 6);
    }

    private void near(CommandSender s, String[] raw) {
        Player p = player(s);
        int radius = Math.min(512, raw.length > 1 ? parseInt(raw[1], 64) : 64);
        Location l = p.getLocation();
        List<Region> rs = plugin.regions().world(p.getWorld()).index().intersecting(l.getBlockX() - radius, l.getWorld().getMinHeight(), l.getBlockZ() - radius, l.getBlockX() + radius, l.getWorld().getMaxHeight(), l.getBlockZ() + radius);
        if (rs.isEmpty()) { text.send(s, "near.none", "radius", radius); return; }
        rs.sort(Comparator.comparingDouble(r -> dist2(l, r.shape())));
        text.send(s, "near.header", "count", rs.size(), "radius", radius);
        for (int i = 0; i < Math.min(15, rs.size()); i++) {
            Region r = rs.get(i);
            text.send(s, "near.line", "id", r.id(), "distance", (int) Math.sqrt(dist2(l, r.shape())));
            plugin.visualizer().showRegion(p, r, 10);
        }
    }

    private static double dist2(Location l, Shape s) {
        double cx = (s.minX() + s.maxX()) / 2.0, cz = (s.minZ() + s.maxZ()) / 2.0;
        return (l.getX() - cx) * (l.getX() - cx) + (l.getZ() - cz) * (l.getZ() - cz);
    }

    private void list(CommandSender s, String[] raw) {
        Args a = Args.parse(raw, 1, "pwnh", "a", -1);
        World w = world(s, a);
        RegionWorld rw = plugin.regions().world(w);
        String owner = a.value('p');
        String filter = a.value('n');
        boolean mineOnly = !s.hasPermission("fawg.region.list") && s instanceof Player;
        UUID ownerId = null;
        if (owner != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(owner);
            if (op == null) throw new Fail("error.unknown-player", "player", owner);
            ownerId = op.getUniqueId();
        } else if (mineOnly) ownerId = ((Player) s).getUniqueId();
        List<Region> out = new ArrayList<>();
        for (Region r : rw.regions()) {
            if (ownerId != null && !r.owners().contains(ownerId) && !r.members().contains(ownerId)) continue;
            if (filter != null && !r.id().contains(filter.toLowerCase(Locale.ROOT))) continue;
            out.add(r);
        }
        out.sort(Comparator.comparing(Region::id));
        sendPaged(s, out, a.size() > 0 ? parseInt(a.get(0), 1) : a.intValue('h', 1), w.getName(), ownerId);
    }

    private void sendPaged(CommandSender s, List<Region> out, int page, String world, UUID owner) {
        int per = 10, pages = Math.max(1, (out.size() + per - 1) / per);
        page = Math.max(1, Math.min(pages, page));
        text.send(s, "list.header", "count", out.size(), "world", world, "page", page, "pages", pages);
        for (int i = (page - 1) * per; i < Math.min(out.size(), page * per); i++) {
            Region r = out.get(i);
            String role = owner == null ? "" : r.owners().contains(owner) ? "owner" : "member";
            text.send(s, "list.line", "n", i + 1, "id", r.id(), "priority", r.priority(), "role", role, "flags", r.flags().size());
        }
        text.send(s, "list.footer", "prev", Math.max(1, page - 1), "next", Math.min(pages, page + 1), "world", world);
    }

    private void search(CommandSender s, String[] raw) {
        if (raw.length < 2) throw new Fail("usage.search");
        String q = raw[1].toLowerCase(Locale.ROOT);
        List<Region> out = new ArrayList<>();
        for (RegionWorld rw : plugin.regions().worlds()) for (Region r : rw.regions()) if (r.id().contains(q)) out.add(r);
        out.sort(Comparator.comparing(Region::id));
        sendPaged(s, out, raw.length > 2 ? parseInt(raw[2], 1) : 1, "*", null);
    }

    private void count(CommandSender s, String[] raw) {
        int total = 0;
        for (RegionWorld rw : plugin.regions().worlds()) {
            text.send(s, "count.line", "world", rw.name(), "count", rw.size());
            total += rw.size();
        }
        text.send(s, "count.total", "count", total);
    }

    // =========================================================================================== select / tp
    private void select(CommandSender s, String[] raw) {
        Player p = player(s);
        Region r = region(s, p.getWorld(), raw.length > 1 ? raw[1] : null);
        svc.require(s, r, "select");
        if (r.isGlobal()) throw new Fail("error.global-not-allowed");
        plugin.selections().get(p).from(r.world(), r.shape());
        if (plugin.worldEdit() != null) plugin.worldEdit().select(p, r.shape());
        plugin.visualizer().showSelection(p);
        plugin.visualizer().showRegion(p, r, plugin.getConfig().getInt("visualizer.select-seconds", 30));
        text.send(s, "selection.region", "id", r.id(), "shape", fmtShape(r.shape()));
    }

    private void teleport(CommandSender s, String[] raw) {
        Player p = player(s);
        Args a = Args.parse(raw, 1, "w", "sc", -1);
        Region r = region(s, world(s, a), a.get(0));
        svc.require(s, r, "teleport");
        World w = Bukkit.getWorld(r.world());
        if (w == null) throw new Fail("error.unknown-world", "world", r.world());
        Location dest = null;
        LocationFlag.Loc l = a.has('s') ? r.flags().get(Flags.SPAWN_LOC) : r.flags().get(Flags.TELE_LOC);
        if (l != null && !a.has('c')) dest = new Location(Bukkit.getWorld(l.world()), l.x(), l.y(), l.z(), l.yaw(), l.pitch());
        if (dest == null || dest.getWorld() == null) {
            if (!a.has('c') && !s.hasPermission("fawg.region.teleport.center")) throw new Fail("teleport.no-location", "id", r.id());
            Shape sh = r.shape();
            int x = (sh.minX() + sh.maxX()) / 2, z = (sh.minZ() + sh.maxZ()) / 2;
            dest = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND);
        String msg = r.flags().get(Flags.TELE_MESSAGE);
        if (msg != null) p.sendMessage(text.userText(msg.replace("%id%", r.id()), p, "id", r.id()));
        else text.send(s, "teleport.done", "id", r.id());
    }

    private void bypass(CommandSender s, String[] raw) {
        Player p = player(s);
        var sess = plugin.sessions().get(p);
        sess.bypassInitialized = true;
        if (raw.length > 1) sess.bypass = raw[1].equalsIgnoreCase("on") || raw[1].equalsIgnoreCase("true");
        else sess.bypass = !sess.bypass;
        text.send(s, sess.bypass ? "bypass.on" : "bypass.off");
        plugin.refreshPlayer(p);
    }

    // =========================================================================================== selection tools
    private void wand(CommandSender s, String[] raw) {
        Player p = player(s);
        p.getInventory().addItem(plugin.selections().wand());
        text.send(s, "wand.given");
    }

    private void pos(CommandSender s, boolean first) {
        Player p = player(s);
        Location l = p.getLocation();
        Selection sel = plugin.selections().get(p);
        if (first) sel.primary(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        else sel.secondary(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        text.send(s, first ? "selection.pos1" : "selection.pos2", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "info", sel.describe(), "count", sel.points.size());
        plugin.visualizer().showSelection(p);
    }

    private void seltype(CommandSender s, String[] raw) {
        Player p = player(s);
        if (raw.length < 2) throw new Fail("usage.seltype");
        Selection.Type t = Selection.Type.parse(raw[1]);
        if (t == null) throw new Fail("usage.seltype");
        plugin.selections().get(p).reset(t);
        text.send(s, "selection.type", "type", t.name().toLowerCase(Locale.ROOT));
    }

    private static int[] dir(Player p, String d) {
        return switch (d == null ? "me" : d.toLowerCase(Locale.ROOT)) {
            case "up", "u" -> new int[]{0, 1, 0};
            case "down", "d" -> new int[]{0, -1, 0};
            case "north", "n" -> new int[]{0, 0, -1};
            case "south", "s" -> new int[]{0, 0, 1};
            case "east", "e" -> new int[]{1, 0, 0};
            case "west", "w" -> new int[]{-1, 0, 0};
            default -> {
                float pitch = p.getLocation().getPitch();
                if (pitch < -50) yield new int[]{0, 1, 0};
                if (pitch > 50) yield new int[]{0, -1, 0};
                BlockFace f = p.getFacing();
                yield new int[]{f.getModX(), f.getModY(), f.getModZ()};
            }
        };
    }

    private void modify(CommandSender s, String[] raw, int mode) {
        Player p = player(s);
        Selection sel = plugin.selections().get(p);
        if (raw.length < 2) throw new Fail("usage.expand");
        if (mode == 0 && raw[1].equalsIgnoreCase("vert")) {
            sel.expandVert(p.getWorld().getMinHeight(), p.getWorld().getMaxHeight() - 1);
            text.send(s, "selection.modified", "info", sel.describe());
            plugin.visualizer().showSelection(p);
            return;
        }
        int amount = parseInt(raw[1], Integer.MIN_VALUE);
        if (amount == Integer.MIN_VALUE) throw new Fail("error.not-a-number", "value", raw[1]);
        int[] d = dir(p, raw.length > 2 ? raw[2] : null);
        switch (mode) {
            case 0 -> sel.expand(amount, d[0], d[1], d[2]);
            case 1 -> sel.expand(-amount, -d[0], -d[1], -d[2]);
            default -> sel.shift(amount, d[0], d[1], d[2]);
        }
        text.send(s, "selection.modified", "info", sel.describe());
        plugin.visualizer().showSelection(p);
    }

    private void clearsel(CommandSender s, String[] raw) {
        Player p = player(s);
        plugin.selections().get(p).reset(plugin.selections().get(p).type);
        plugin.visualizer().hideSelection(p);
        text.send(s, "selection.cleared");
    }

    private void selinfo(CommandSender s, String[] raw) {
        Player p = player(s);
        Selection sel = plugin.selections().get(p);
        text.send(s, "selection.info", "type", sel.type.name().toLowerCase(Locale.ROOT), "info", sel.describe());
        plugin.visualizer().showSelection(p);
    }

    private void visualize(CommandSender s, String[] raw) {
        Player p = player(s);
        if (raw.length < 2 || raw[1].equalsIgnoreCase("nearby")) {
            boolean on = plugin.visualizer().toggleNearby(p);
            text.send(s, on ? "visualize.nearby-on" : "visualize.nearby-off");
            return;
        }
        if (raw[1].equalsIgnoreCase("off") || raw[1].equalsIgnoreCase("clear")) { hide(s, raw); return; }
        Region r = region(s, p.getWorld(), raw[1]);
        plugin.visualizer().showRegion(p, r, plugin.getConfig().getInt("visualizer.show-seconds", 60));
        text.send(s, "visualize.region", "id", r.id());
    }

    private void hide(CommandSender s, String[] raw) {
        plugin.visualizer().clear(player(s));
        text.send(s, "visualize.cleared");
    }

    private void exclude(CommandSender s, String[] raw) {
        Player p = player(s);
        if (raw.length < 3) throw new Fail("usage.exclude");
        Region r = region(s, p.getWorld(), raw[1]);
        switch (raw[2].toLowerCase(Locale.ROOT)) {
            case "add" -> { Shape sh = selectionShape(p); svc.exclude(s, r, sh, true); text.send(s, "exclude.added", "id", r.id(), "count", r.excludes().size()); }
            case "remove" -> { Shape sh = selectionShape(p); svc.exclude(s, r, sh, false); text.send(s, "exclude.removed", "id", r.id(), "count", r.excludes().size()); }
            case "clear" -> { svc.clearExcludes(s, r); text.send(s, "exclude.cleared", "id", r.id()); }
            case "list" -> {
                text.send(s, "exclude.header", "id", r.id(), "count", r.excludes().size());
                int i = 1;
                for (Shape sh : r.excludes()) text.send(s, "exclude.line", "n", i++, "shape", fmtShape(sh));
            }
            default -> throw new Fail("usage.exclude");
        }
        plugin.visualizer().showRegion(p, r, 15);
    }

    private void lock(CommandSender s, String[] raw) {
        if (raw.length < 2) throw new Fail("usage.lock");
        ProtectLevel l = raw.length > 2 ? ProtectLevel.parse(raw[2]) : ProtectLevel.SERVER;
        if (l == null) throw new Fail("usage.lock");
        lockLevel(s, raw, l);
    }

    private void lockLevel(CommandSender s, String[] raw, ProtectLevel l) {
        if (raw.length < 2) throw new Fail("usage.lock");
        World w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        if (raw.length > 3 && Bukkit.getWorld(raw[3]) != null) w = Bukkit.getWorld(raw[3]);
        Region r = region(s, w, raw[1]);
        svc.lock(s, r, l);
        text.send(s, l == ProtectLevel.NONE ? "lock.removed" : "lock.set", "id", r.id(), "level", l.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    // =========================================================================================== storage
    private void load(CommandSender s, String[] raw) {
        plugin.regions().reload();
        plugin.refreshAll();
        text.send(s, "storage.loaded", "count", plugin.regions().totalRegions());
    }

    private void save(CommandSender s, String[] raw) {
        for (RegionWorld rw : plugin.regions().worlds()) plugin.regions().saveAsync(rw);
        text.send(s, "storage.saved");
    }

    private void migratedb(CommandSender s, String[] raw) { text.send(s, "migrate.db"); }

    private void migrateuuid(CommandSender s, String[] raw) {
        int converted = 0;
        for (RegionWorld rw : plugin.regions().worlds()) {
            for (Region r : rw.regionsIncludingGlobal()) {
                for (Domain d : new Domain[]{r.owners(), r.members()}) {
                    for (String n : new ArrayList<>(d.names())) {
                        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(n);
                        if (op != null) { d.removeName(n); d.addPlayer(op.getUniqueId()); converted++; }
                    }
                }
            }
            rw.markDirty();
        }
        text.send(s, "migrate.uuid", "count", converted);
    }

    private void migrateheights(CommandSender s, String[] raw) {
        int n = 0;
        for (RegionWorld rw : plugin.regions().worlds()) {
            World w = Bukkit.getWorld(rw.name());
            if (w == null) continue;
            for (Region r : rw.regions()) {
                Shape sh = r.shape();
                if (sh.minY() <= 0 && sh.maxY() >= 255 && (sh instanceof Shape.Cuboid || sh instanceof Shape.Polygon)) {
                    r.shape(sh.withHeight(w.getMinHeight(), w.getMaxHeight() - 1));
                    n++;
                }
            }
            rw.changed();
        }
        text.send(s, "migrate.heights", "count", n);
    }

    // =========================================================================================== help
    private void help(CommandSender s, String[] raw) {
        int page = raw.length > 1 ? parseInt(raw[1], 1) : 1;
        List<String> keys = new ArrayList<>();
        for (String k : text.raw("help.region-lines").split("\n")) keys.add(k);
        int per = 10, pages = Math.max(1, (keys.size() + per - 1) / per);
        page = Math.max(1, Math.min(pages, page));
        text.send(s, "help.header", "page", page, "pages", pages);
        for (int i = (page - 1) * per; i < Math.min(keys.size(), page * per); i++) s.sendMessage(text.componentRaw(keys.get(i), s));
        text.send(s, "help.footer", "prev", Math.max(1, page - 1), "next", Math.min(pages, page + 1));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // =========================================================================================== tab completion
    private static final List<String> REGION_FIRST = List.of("redefine", "select", "info", "addowner", "removeowner", "addmember", "removemember", "flag", "flags",
            "setpriority", "setparent", "remove", "teleport", "visualize", "rename", "copyflags", "clearflags", "preset", "exclude", "lock", "unlock", "setteleport", "setspawn", "menu");

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, String> e : perms.entrySet()) if (e.getValue().isEmpty() || hasAny(s, e.getValue())) out.add(e.getKey());
            return filter(out, args[0]);
        }
        String sub = aliases.getOrDefault(args[0].toLowerCase(Locale.ROOT), args[0].toLowerCase(Locale.ROOT));
        if (!handlers.containsKey(sub)) return List.of();
        String last = args[args.length - 1];
        String prev = args[args.length - 2];
        World w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        for (int i = 1; i < args.length - 1; i++) if (args[i].equals("-w") && Bukkit.getWorld(args[i + 1]) != null) w = Bukkit.getWorld(args[i + 1]);
        if (prev.equals("-w")) return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), last);
        if (prev.equals("-g")) return filter(List.of("all", "members", "owners", "nonmembers", "nonowners", "none"), last);
        if (prev.equals("-p")) return null; // player names
        int argi = 0;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("-") && args[i].length() == 2) continue;
            if (i > 1 && args[i - 1].startsWith("-") && args[i - 1].length() == 2 && "gwph".indexOf(args[i - 1].charAt(1)) >= 0) continue;
            argi++;
        }
        if (REGION_FIRST.contains(sub) && argi == 1) {
            List<String> ids = regionIds(s, w);
            if (sub.equals("visualize")) { ids = new ArrayList<>(ids); ids.add(0, "nearby"); ids.add(1, "off"); }
            return filter(ids, last);
        }
        switch (sub) {
            case "flag" -> {
                if (argi == 2) {
                    List<String> out = new ArrayList<>();
                    String l = last.toLowerCase(Locale.ROOT);
                    for (String n : Flags.REGISTRY.names()) if (n.startsWith(l)) out.add(n);
                    if (out.size() < 30) for (String n : Flags.REGISTRY.names()) if (!n.startsWith(l) && n.contains(l) && l.length() > 2) out.add(n);
                    out.sort(Comparator.naturalOrder());
                    if (last.isEmpty()) { out.addAll(0, List.of("-g", "-w", "-e")); }
                    return out.size() > 300 ? out.subList(0, 300) : out;
                }
                if (argi >= 3) {
                    String fname = null;
                    int seen = 0;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].startsWith("-") && args[i].length() == 2) continue;
                        if (i > 1 && args[i - 1].startsWith("-") && args[i - 1].length() == 2) continue;
                        if (++seen == 2) { fname = args[i]; break; }
                    }
                    Flag<?> f = fname == null ? null : Flags.REGISTRY.get(fname);
                    if (f == null) return List.of();
                    List<String> sug = new ArrayList<>(f.suggestions());
                    if (argi == 3) sug.addAll(List.of("-g", "-e"));
                    if (f instanceof dev.mattron1c.fawg.core.flag.StringSetFlag && last.contains(",")) {
                        String head = last.substring(0, last.lastIndexOf(',') + 1);
                        String tail = last.substring(last.lastIndexOf(',') + 1);
                        List<String> out = new ArrayList<>();
                        for (String x : sug) if (x.startsWith(tail.toLowerCase(Locale.ROOT))) out.add(head + x);
                        return out.size() > 200 ? out.subList(0, 200) : out;
                    }
                    return filterLimit(sug, last, 200);
                }
            }
            case "addowner", "addmember", "define", "claim" -> {
                if (sub.equals("define") || sub.equals("claim")) {
                    if (argi == 1) return List.of("<id>");
                }
                return null;
            }
            case "removeowner", "removemember" -> {
                Region r = plugin.regions().world(w).get(args[1]);
                if (r == null) return List.of();
                List<String> out = new ArrayList<>(List.of("-a"));
                Domain d = sub.equals("removeowner") ? r.owners() : r.members();
                for (UUID u : d.players()) { String n = Bukkit.getOfflinePlayer(u).getName(); if (n != null) out.add(n); }
                out.addAll(d.names());
                for (String g : d.groups()) out.add("g:" + g);
                return filter(out, last);
            }
            case "setparent", "copyflags" -> { if (argi == 2) return filter(regionIds(s, w), last); }
            case "preset" -> {
                if (argi == 2) {
                    var sec = plugin.getConfig().getConfigurationSection("presets");
                    return sec == null ? List.of() : filter(new ArrayList<>(sec.getKeys(false)), last);
                }
            }
            case "lock" -> { if (argi == 2) return filter(List.of("server", "player", "break-allowed", "break-only", "none"), last); }
            case "exclude" -> { if (argi == 2) return filter(List.of("add", "remove", "list", "clear"), last); }
            case "seltype" -> { if (argi == 1) return filter(List.of("cuboid", "poly", "cylinder", "sphere"), last); }
            case "expand", "contract", "shift" -> {
                if (argi == 1) return filter(sub.equals("expand") ? List.of("1", "5", "10", "vert") : List.of("1", "5", "10"), last);
                if (argi == 2) return filter(List.of("me", "up", "down", "north", "south", "east", "west"), last);
            }
            case "toggle-bypass" -> { if (argi == 1) return filter(List.of("on", "off"), last); }
            case "list" -> { if (argi >= 1) return filter(List.of("-p", "-w", "-n", "1", "2"), last); }
            case "near" -> { if (argi == 1) return filter(List.of("32", "64", "128", "256"), last); }
            case "teleport" -> { if (argi == 2) return filter(List.of("-s", "-c"), last); }
            case "remove" -> { if (argi == 2) return filter(List.of("-f", "-u"), last); }
            default -> { }
        }
        return List.of();
    }

    private List<String> regionIds(CommandSender s, World w) {
        RegionWorld rw = plugin.regions().world(w);
        List<String> ids = new ArrayList<>();
        boolean all = s.hasPermission("fawg.region.list") || !(s instanceof Player);
        PlayerSubject sub = s instanceof Player p ? PlayerSubject.of(p) : null;
        for (Region r : rw.regions()) if (all || r.isMember(sub)) ids.add(r.id());
        ids.sort(Comparator.naturalOrder());
        ids.add(Region.GLOBAL_ID);
        return ids;
    }

    static List<String> filter(Collection<String> in, String prefix) { return filterLimit(in, prefix, 500); }

    static List<String> filterLimit(Collection<String> in, String prefix, int limit) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(p)) { out.add(s); if (out.size() >= limit) break; }
        return out;
    }

    public static Component unused() { return Component.empty(); }

    static List<String> list(String... s) { return Arrays.asList(s); }
}
