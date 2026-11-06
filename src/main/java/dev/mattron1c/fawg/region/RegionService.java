package dev.mattron1c.fawg.region;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.api.event.RegionChangeEvent;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.InvalidFlagFormatException;
import dev.mattron1c.fawg.core.flag.LocationFlag;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Domain;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.region.RegionGroup;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.flags.Flags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** All region mutations go through here (commands, dialogs, chest GUI, API) so rules are enforced once. */
public final class RegionService {
    /** Thrown with a messages.yml key + placeholders. */
    public static final class Fail extends RuntimeException {
        public final String key;
        public final Object[] ph;
        public Fail(String key, Object... ph) { super(key, null, false, false); this.key = key; this.ph = ph; }
    }

    private final FAWG plugin;

    public RegionService(FAWG plugin) { this.plugin = plugin; }

    private void fire(Region r, RegionChangeEvent.Type t, CommandSender actor, String detail) {
        RegionChangeEvent e = new RegionChangeEvent(r, t, actor, detail);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled()) throw new Fail("error.cancelled");
    }

    public static PlayerSubject subject(CommandSender s) { return s instanceof Player p ? PlayerSubject.of(p) : null; }

    /** Permission check: fawg.region.&lt;perm&gt; (any region) or .own / .member variants. */
    public boolean canManage(CommandSender s, Region r, String perm) {
        if (s.hasPermission("fawg.region." + perm)) return lockOk(s, r);
        if (r != null && s instanceof Player p) {
            PlayerSubject sub = PlayerSubject.of(p);
            if (r.isOwner(sub) && s.hasPermission("fawg.region." + perm + ".own")) return lockOk(s, r);
            if (r.isMember(sub) && s.hasPermission("fawg.region." + perm + ".member")) return lockOk(s, r);
        }
        return false;
    }

    /** Server locked regions can only be modified by console / fawg.lock.bypass. */
    private boolean lockOk(CommandSender s, Region r) {
        if (r == null || !(s instanceof Player)) return true;
        ProtectLevel l = r.flags().get(Flags.REGION_LOCK);
        if (l == null || l == ProtectLevel.NONE || l == ProtectLevel.PLAYER) return true;
        return s.hasPermission("fawg.lock.bypass");
    }

    public void require(CommandSender s, Region r, String perm) {
        if (!canManage(s, r, perm)) throw new Fail("error.no-permission");
    }

    // ------------------------------------------------------------------------------------------ create
    public Region define(CommandSender actor, World w, String id, Shape shape, boolean claim, List<String> owners) {
        String lid = id.toLowerCase(Locale.ROOT);
        if (!Region.isValidId(lid) || lid.equals(Region.GLOBAL_ID)) throw new Fail("error.invalid-id", "id", id);
        RegionWorld rw = plugin.regions().world(w);
        if (rw.get(lid) != null) throw new Fail("error.exists", "id", lid);
        Region r = new Region(lid, w.getName(), shape);
        r.createdBy(actor.getName());
        if (claim) checkClaim(actor, rw, r);
        if (owners != null) for (String o : owners) addDomain(r.owners(), o);
        if (claim && actor instanceof Player p) r.owners().addPlayer(p.getUniqueId());
        if (claim) applyDefaults(r, plugin.getConfig().getConfigurationSection("claims.default-flags"));
        r.priority(claim ? plugin.getConfig().getInt("claims.default-priority", 0) : plugin.getConfig().getInt("regions.default-priority", 0));
        fire(r, RegionChangeEvent.Type.CREATE, actor, shape.describe());
        rw.add(r);
        plugin.actionLog().log("CREATE " + actor.getName() + " " + w.getName() + ":" + lid + " " + shape.describe());
        return r;
    }

    private void checkClaim(CommandSender actor, RegionWorld rw, Region r) {
        if (!(actor instanceof Player p)) return;
        if (p.hasPermission("fawg.claim.unlimited")) return;
        var c = plugin.getConfig();
        int max = c.getInt("claims.max-regions-per-player", 5);
        for (int i = 1000; i > 0; i--) if (p.hasPermission("fawg.claim.limit." + i)) { max = i; break; }
        int owned = 0;
        PlayerSubject sub = PlayerSubject.of(p);
        for (Region o : rw.regions()) if (o.owners().contains(sub)) owned++;
        if (owned >= max) throw new Fail("claim.too-many", "max", max);
        long maxVol = c.getLong("claims.max-volume", 2_000_000L);
        if (r.shape().volume() > maxVol) throw new Fail("claim.too-big", "max", String.format("%,d", maxVol), "volume", String.format("%,d", r.shape().volume()));
        var s = r.shape();
        for (Region o : rw.index().intersecting(s.minX(), s.minY(), s.minZ(), s.maxX(), s.maxY(), s.maxZ())) {
            if (!o.isOwner(sub) && c.getBoolean("claims.deny-overlap-unowned", true)) throw new Fail("claim.overlap", "region", o.id());
        }
        if (c.getBoolean("claims.only-inside-existing", false)) {
            boolean inside = false;
            for (Region o : rw.query(s.minX(), s.minY(), s.minZ())) if (o.isOwner(sub)) inside = true;
            if (!inside) throw new Fail("claim.not-inside");
        }
    }

    private void applyDefaults(Region r, ConfigurationSection sec) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            Flag<?> f = Flags.REGISTRY.get(k);
            if (f != null) try { setParsed(r, f, String.valueOf(sec.get(k))); } catch (InvalidFlagFormatException ignored) { }
        }
    }

    public void redefine(CommandSender actor, Region r, Shape shape) {
        require(actor, r, "redefine");
        fire(r, RegionChangeEvent.Type.REDEFINE, actor, shape.describe());
        r.shape(shape);
        plugin.regions().changed(r);
        plugin.actionLog().log("REDEFINE " + actor.getName() + " " + r.world() + ":" + r.id() + " " + shape.describe());
    }

    public void remove(CommandSender actor, Region r, boolean children) {
        require(actor, r, "remove");
        if (r.isGlobal()) throw new Fail("error.global-not-allowed");
        fire(r, RegionChangeEvent.Type.REMOVE, actor, null);
        plugin.regions().world(r.world()).remove(r.id(), children);
        plugin.actionLog().log("REMOVE " + actor.getName() + " " + r.world() + ":" + r.id());
    }

    public Region rename(CommandSender actor, Region r, String newId) {
        require(actor, r, "rename");
        String nid = newId.toLowerCase(Locale.ROOT);
        if (!Region.isValidId(nid) || nid.equals(Region.GLOBAL_ID)) throw new Fail("error.invalid-id", "id", newId);
        RegionWorld rw = plugin.regions().world(r.world());
        if (rw.get(nid) != null) throw new Fail("error.exists", "id", nid);
        fire(r, RegionChangeEvent.Type.RENAME, actor, nid);
        Region n = new Region(nid, r.world(), r.shape());
        n.excludes(r.excludes());
        n.priority(r.priority());
        n.owners().copyFrom(r.owners());
        n.members().copyFrom(r.members());
        n.flags().copyFrom(r.flags());
        n.createdAt(r.createdAt());
        n.createdBy(r.createdBy());
        if (r.parent() != null) n.parent(r.parent());
        for (Region c : rw.regions()) if (c.parent() == r) c.parent(n);
        rw.remove(r.id(), false);
        rw.add(n);
        return n;
    }

    // ------------------------------------------------------------------------------------------ flags
    public <T> void setParsed(Region r, Flag<T> f, String input) throws InvalidFlagFormatException {
        r.flags().set(f, f.parse(input));
    }

    /** Sets (input != null) or clears (input == null) a flag. Location flags accept "here". */
    public void setFlag(CommandSender actor, Region r, Flag<?> f, String input, RegionGroup group) {
        require(actor, r, "flag");
        if (!actor.hasPermission("fawg.flag.*") && !actor.hasPermission("fawg.flag." + f.name()) && actor.hasPermission("fawg.flag.restrict")) {
            throw new Fail("error.flag-no-permission", "flag", f.name());
        }
        if (plugin.getConfig().getStringList("flags.disabled").contains(f.name())) throw new Fail("error.flag-disabled", "flag", f.name());
        fire(r, RegionChangeEvent.Type.FLAG, actor, f.name() + "=" + input);
        if (input == null) {
            r.flags().set(f, null);
        } else {
            String in = input;
            if (f instanceof LocationFlag && in.equalsIgnoreCase("here")) {
                if (!(actor instanceof Player p)) throw new Fail("error.player-only");
                Location l = p.getLocation();
                in = l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
            }
            try {
                setParsed(r, f, in);
            } catch (InvalidFlagFormatException e) {
                throw new Fail("error.flag-format", "flag", f.name(), "reason", e.getMessage());
            }
            if (group != null) r.flags().setGroup(f, group);
        }
        r.touch();
        plugin.regions().markDirty(r);
        if (f == Flags.CHUNK_UNLOAD && plugin.worldListener() != null) {
            World w = Bukkit.getWorld(r.world());
            if (w != null) plugin.worldListener().refreshChunkTickets(w);
        }
        plugin.refreshPlayersIn(r);
        plugin.actionLog().log("FLAG " + actor.getName() + " " + r.world() + ":" + r.id() + " " + f.name() + "=" + input + (group == null ? "" : " -g " + group.id()));
    }

    public void copyFlags(CommandSender actor, Region from, Region to, boolean replace) {
        require(actor, to, "flag");
        fire(to, RegionChangeEvent.Type.FLAG, actor, "copy from " + from.id());
        if (replace) to.flags().copyFrom(from.flags());
        else for (Map.Entry<Flag<?>, Object> e : from.flags().snapshot(Flags.REGISTRY).entrySet()) {
            @SuppressWarnings("unchecked") Flag<Object> f = (Flag<Object>) e.getKey();
            if (!to.flags().has(f)) { to.flags().set(f, e.getValue()); to.flags().setGroup(f, from.flags().group(f)); }
        }
        to.touch();
        plugin.regions().markDirty(to);
        plugin.refreshPlayersIn(to);
    }

    public void clearFlags(CommandSender actor, Region r) {
        require(actor, r, "flag");
        fire(r, RegionChangeEvent.Type.FLAG, actor, "clear");
        r.flags().clear();
        r.touch();
        plugin.regions().markDirty(r);
        plugin.refreshPlayersIn(r);
    }

    /** Applies a preset from config (presets.&lt;name&gt;). Returns number of flags applied. */
    public int preset(CommandSender actor, Region r, String name) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("presets." + name.toLowerCase(Locale.ROOT));
        if (sec == null) throw new Fail("error.unknown-preset", "preset", name);
        require(actor, r, "flag");
        fire(r, RegionChangeEvent.Type.FLAG, actor, "preset " + name);
        int n = 0;
        for (String k : sec.getKeys(false)) {
            Flag<?> f = Flags.REGISTRY.get(k);
            if (f == null) continue;
            try { setParsed(r, f, String.valueOf(sec.get(k))); n++; } catch (InvalidFlagFormatException ignored) { }
        }
        r.touch();
        plugin.regions().markDirty(r);
        plugin.refreshPlayersIn(r);
        return n;
    }

    // ------------------------------------------------------------------------------------------ domains
    /** Adds "name", "uuid" or "g:group" to a domain. */
    public static String addDomain(Domain d, String entry) {
        if (entry.startsWith("g:")) { d.addGroup(entry.substring(2)); return "group " + entry.substring(2); }
        try {
            UUID u = UUID.fromString(entry);
            d.addPlayer(u);
            return entry;
        } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(entry);
        if (online != null) { d.addPlayer(online.getUniqueId()); return online.getName(); }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(entry);
        if (off != null) { d.addPlayer(off.getUniqueId()); return off.getName() == null ? entry : off.getName(); }
        d.addName(entry);
        return entry + " (name)";
    }

    public static boolean removeDomain(Domain d, String entry) {
        if (entry.startsWith("g:")) return d.removeGroup(entry.substring(2));
        try { return d.removePlayer(UUID.fromString(entry)); } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(entry);
        boolean r = false;
        if (online != null) r = d.removePlayer(online.getUniqueId());
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(entry);
        if (off != null) r |= d.removePlayer(off.getUniqueId());
        r |= d.removeName(entry);
        return r;
    }

    public List<String> addMembers(CommandSender actor, Region r, boolean owners, List<String> who) {
        require(actor, r, owners ? "addowner" : "addmember");
        fire(r, owners ? RegionChangeEvent.Type.OWNERS : RegionChangeEvent.Type.MEMBERS, actor, "add " + who);
        List<String> added = new ArrayList<>();
        for (String w : who) added.add(addDomain(owners ? r.owners() : r.members(), w));
        r.touch();
        plugin.regions().markDirty(r);
        plugin.refreshPlayersIn(r);
        return added;
    }

    public int removeMembers(CommandSender actor, Region r, boolean owners, List<String> who, boolean all) {
        require(actor, r, owners ? "removeowner" : "removemember");
        fire(r, owners ? RegionChangeEvent.Type.OWNERS : RegionChangeEvent.Type.MEMBERS, actor, "remove " + who);
        Domain d = owners ? r.owners() : r.members();
        int n;
        if (all) { n = d.size(); d.clear(); }
        else { n = 0; for (String w : who) if (removeDomain(d, w)) n++; }
        r.touch();
        plugin.regions().markDirty(r);
        plugin.refreshPlayersIn(r);
        return n;
    }

    // ------------------------------------------------------------------------------------------ misc
    public void priority(CommandSender actor, Region r, int p) {
        require(actor, r, "setpriority");
        fire(r, RegionChangeEvent.Type.PRIORITY, actor, String.valueOf(p));
        r.priority(p);
        plugin.regions().changed(r);
        plugin.refreshPlayersIn(r);
    }

    public void parent(CommandSender actor, Region r, Region parent) {
        require(actor, r, "setparent");
        if (r.isGlobal()) throw new Fail("error.global-not-allowed");
        fire(r, RegionChangeEvent.Type.PARENT, actor, parent == null ? "none" : parent.id());
        try { r.parent(parent); } catch (IllegalArgumentException e) { throw new Fail("error.circular", "region", r.id(), "parent", parent == null ? "-" : parent.id()); }
        plugin.regions().changed(r);
        plugin.refreshPlayersIn(r);
    }

    public void exclude(CommandSender actor, Region r, Shape s, boolean add) {
        require(actor, r, "redefine");
        fire(r, RegionChangeEvent.Type.REDEFINE, actor, (add ? "exclude " : "unexclude ") + s.describe());
        List<Shape> l = new ArrayList<>(r.excludes());
        if (add) l.add(s); else l.removeIf(x -> x.boundsIntersect(s));
        r.excludes(l);
        plugin.regions().changed(r);
    }

    public void clearExcludes(CommandSender actor, Region r) {
        require(actor, r, "redefine");
        r.excludes(List.of());
        plugin.regions().changed(r);
    }

    public void lock(CommandSender actor, Region r, ProtectLevel level) {
        if (actor instanceof Player && !actor.hasPermission("fawg.region.lock")) {
            if (!(level == ProtectLevel.PLAYER || level == ProtectLevel.NONE) || !canManage(actor, r, "lock")) throw new Fail("error.no-permission");
        }
        ProtectLevel cur = r.flags().get(Flags.REGION_LOCK);
        if (cur == ProtectLevel.SERVER && actor instanceof Player && !actor.hasPermission("fawg.lock.bypass")) throw new Fail("lock.server-locked", "region", r.id());
        fire(r, RegionChangeEvent.Type.FLAG, actor, "lock " + level);
        r.flags().set(Flags.REGION_LOCK, level == ProtectLevel.NONE ? null : level);
        r.touch();
        plugin.regions().markDirty(r);
    }
}
