package dev.mattron1c.fawg.protect;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.Flag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StateFlag.State;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.query.FlagResolver;
import dev.mattron1c.fawg.core.region.ProtectLevel;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.region.PlayerSubject;
import dev.mattron1c.fawg.session.PlayerSession;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Central protection checks used by every listener, the API and the WorldEdit / FAWE hooks.
 * Everything here is cheap: one index probe + a handful of array reads per check.
 */
public final class Protection {
    private final FAWG plugin;
    private final FlagResolver resolver = new FlagResolver();
    private volatile boolean denyActionBar = true;
    private volatile long denyCooldown = 1500;
    private volatile boolean spyAll;

    public Protection(FAWG plugin) {
        this.plugin = plugin;
        resolver.passthroughFlag(Flags.PASSTHROUGH);
    }

    public void reload() {
        var c = plugin.getConfig();
        resolver.wgStrict(c.getBoolean("flags.worldguard-strict-deny", false));
        denyActionBar = !"chat".equalsIgnoreCase(c.getString("protection.deny-message-mode", "actionbar"));
        denyCooldown = c.getLong("protection.deny-message-cooldown-ms", 1500);
        spyAll = c.getBoolean("spy.all-denials", false);
    }

    public FlagResolver resolver() { return resolver; }

    public ApplicableSet at(Location l) { return plugin.regions().query(l); }
    public ApplicableSet at(Block b) { return plugin.regions().query(b); }
    public ApplicableSet at(World w, int x, int y, int z) { return plugin.regions().query(w, x, y, z); }

    // --------------------------------------------------------------------------------------- bypass
    public boolean bypasses(Player p) {
        if (p == null) return false;
        PlayerSession s = plugin.sessions().get(p);
        if (!s.bypassInitialized) {
            s.bypassInitialized = true;
            s.bypass = plugin.getConfig().getBoolean("bypass.enabled-by-default", true);
        }
        if (!s.bypass) return false;
        return p.hasPermission("fawg.bypass") || p.hasPermission("fawg.bypass." + p.getWorld().getName());
    }

    /** Hard lock check (WorldGuardRegionProtect). Returns true when the lock denies this action. */
    public boolean lockDenies(Player p, ApplicableSet set, StateFlag flag) {
        if (set.isEmpty()) return false;
        ProtectLevel lvl = null;
        Region lockRegion = null;
        for (Region r : set.regions()) {
            ProtectLevel l = r.flags().get(Flags.REGION_LOCK);
            if (l != null && l != ProtectLevel.NONE) { lvl = l; lockRegion = r; break; }
        }
        if (lvl == null) return false;
        if (!flag.chainGated() && flag != Flags.WORLDEDIT && flag != Flags.WORLDEDIT_COMMANDS && flag != Flags.SUPERPICKAXE && flag != Flags.WORLD_EDIT_BRUSHES) return false;
        if (p != null && p.hasPermission("fawg.lock.bypass")) return false;
        boolean breaking = false;
        for (StateFlag f : flag.chain()) if (f == Flags.BLOCK_BREAK) { breaking = true; break; }
        return switch (lvl) {
            case SERVER -> true;
            case PLAYER -> p == null || !lockRegion.isOwner(PlayerSubject.of(p));
            case BREAK_ALLOWED, BREAK_ONLY -> !breaking;
            default -> false;
        };
    }

    // --------------------------------------------------------------------------------------- checks
    public boolean can(Player p, Location l, StateFlag flag) { return can(p, at(l), flag); }

    public boolean can(Player p, Block b, StateFlag flag) { return can(p, at(b), flag); }

    public boolean can(Player p, ApplicableSet set, StateFlag flag) {
        if (lockDenies(p, set, flag)) return false;
        if (bypasses(p)) return true;
        return resolver.test(set, flag, p == null ? null : plugin.sessions().get(p).subject) == State.ALLOW;
    }

    /** Flag test for a player without bypass / lock handling (damage, effects, targeting...). */
    public boolean allowsFor(Player p, ApplicableSet set, StateFlag flag) {
        return resolver.test(set, flag, p == null ? null : plugin.sessions().get(p).subject) == State.ALLOW;
    }

    /** True when the flag resolves to ALLOW for a "true means on" style state flag (invincible, godmode). */
    public boolean enabledFor(Player p, ApplicableSet set, StateFlag flag) {
        State s = resolver.regionValue(set, flag, p == null ? null : plugin.sessions().get(p).subject);
        if (s == null && set.global() != null) s = FlagResolver.effective(set.global(), flag, p == null ? null : plugin.sessions().get(p).subject);
        return s == State.ALLOW;
    }

    /** Every flag must allow. */
    public boolean canAll(Player p, ApplicableSet set, StateFlag... flags) {
        for (StateFlag f : flags) if (f != null && !can(p, set, f)) return false;
        return true;
    }

    /** Returns the first denying flag (or null when everything is allowed). */
    public StateFlag firstDenied(Player p, ApplicableSet set, StateFlag[] flags) {
        for (StateFlag f : flags) if (f != null && !can(p, set, f)) return f;
        return null;
    }

    /** Natural / non-player event (fluids, growth, explosions, spawning). */
    public boolean natural(ApplicableSet set, StateFlag flag) {
        return resolver.test(set, flag, null) == State.ALLOW;
    }

    public boolean natural(Location l, StateFlag flag) { return natural(at(l), flag); }

    public boolean natural(Block b, StateFlag flag) { return natural(at(b), flag); }

    /** True when the flag is explicitly set to the given state by a region (or global) at this point. */
    public boolean explicit(ApplicableSet set, StateFlag flag, State state) {
        State s = resolver.regionValue(set, flag, null);
        if (s == null && set.global() != null) s = FlagResolver.effective(set.global(), flag, null);
        return s == state;
    }

    /** Player-scoped value of any flag (honours region groups), with global + default fallback. */
    public <T> T value(Player p, ApplicableSet set, Flag<T> flag) {
        return resolver.value(set, flag, p == null ? null : plugin.sessions().get(p).subject);
    }

    public <T> T value(ApplicableSet set, Flag<T> flag) { return resolver.value(set, flag, null); }

    /** Value only from real regions (no global/default). */
    public <T> T regionValue(Player p, ApplicableSet set, Flag<T> flag) {
        return resolver.regionValue(set, flag, p == null ? null : plugin.sessions().get(p).subject);
    }

    public boolean isMemberOfAll(Player p, ApplicableSet set) {
        return resolver.membership(set, plugin.sessions().get(p).subject) != FlagResolver.Membership.FAIL;
    }

    /** True when every region at {@code to} also contains {@code from} (same protection domain). */
    public boolean sameDomain(ApplicableSet from, ApplicableSet to) {
        if (to.isEmpty()) return true;
        outer:
        for (Region r : to.regions()) {
            if (from.contains(r)) continue;
            for (Region f : from.regions()) if (r.isAncestorOf(f) || f.isAncestorOf(r)) continue outer;
            java.util.Set<String> dom = r.flags().get(Flags.NONPLAYER_PROTECTION_DOMAINS);
            if (dom != null) for (Region f : from.regions()) if (dom.contains(f.id())) continue outer;
            return false;
        }
        return true;
    }

    // --------------------------------------------------------------------------------------- deny
    /** Sends the deny feedback (message, spy) for a denied action. */
    public void deny(Player p, ApplicableSet set, StateFlag flag, Location where) {
        if (p == null) return;
        PlayerSession s = plugin.sessions().get(p);
        Boolean show = value(p, set, Flags.SHOW_DENY_MESSAGES);
        if ((show == null || show) && s.cooldown("deny:" + flag.name(), denyCooldown)) {
            String custom = value(p, set, Flags.DENY_MESSAGE);
            String action = flag.action();
            var comp = custom != null && !custom.isBlank()
                    ? plugin.text().userText(custom.replace("%what%", action), p, "what", action, "player", p.getName())
                    : plugin.text().component("protection.denied", p, "action", action, "flag", flag.name());
            if (denyActionBar) p.sendActionBar(comp); else p.sendMessage(comp);
            if (plugin.getConfig().getBoolean("protection.deny-sound", true)) {
                try { p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.6f); } catch (Throwable ignored) { }
            }
        }
        boolean spy = spyAll;
        if (!spy) { Boolean v = value(set, Flags.ADMIN_SPY); spy = v != null && v; }
        if (spy && s.cooldown("spy:" + flag.name(), 3000)) {
            Region top = set.highest();
            Location l = where == null ? p.getLocation() : where;
            plugin.text().notify("fawg.spy", "spy.denied", "player", p.getName(), "action", flag.action(), "flag", flag.name(),
                    "region", top == null ? "__global__" : top.id(), "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "world", l.getWorld().getName());
            plugin.actionLog().log("DENY " + p.getName() + " " + flag.name() + " " + (top == null ? "__global__" : top.id()) + " " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        }
    }

    /** Convenience: check + deny feedback in one call. */
    public boolean check(Player p, ApplicableSet set, StateFlag flag, Location where) {
        if (can(p, set, flag)) return true;
        deny(p, set, flag, where);
        return false;
    }

    public boolean checkAll(Player p, ApplicableSet set, Location where, StateFlag... flags) {
        for (StateFlag f : flags) {
            if (f != null && !can(p, set, f)) { deny(p, set, f, where); return false; }
        }
        return true;
    }
}
