package dev.mattron1c.fawg.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.api.event.RegionEnterEvent;
import dev.mattron1c.fawg.api.event.RegionLeaveEvent;
import dev.mattron1c.fawg.core.flag.LocationFlag;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.flag.StringFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.query.FlagResolver;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.flags.FlagFamilies;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import dev.mattron1c.fawg.session.PlayerSession;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlayerListener implements Listener {
    private final FAWG plugin;
    private final Protection pr;

    public PlayerListener(FAWG plugin) {
        this.plugin = plugin;
        this.pr = plugin.protection();
    }

    private static long blockKey(Location l) {
        return ((long) l.getBlockX() & 0x3FFFFFF) << 38 | ((long) l.getBlockZ() & 0x3FFFFFF) << 12 | ((long) l.getBlockY() & 0xFFF);
    }

    // ===================================================================================== movement core
    public enum MoveType { MOVE, TELEPORT, JOIN, RESPAWN, VEHICLE, WORLD_CHANGE }

    /**
     * Applies a position change for a player. Returns false when the move must be prevented.
     * Fast path: same block → no work at all; same region set → one index probe and an identity compare.
     */
    public boolean handleMove(Player p, Location to, MoveType type) {
        PlayerSession s = plugin.sessions().get(p);
        World w = to.getWorld();
        long key = blockKey(to);
        if (key == s.blockKey && w.getName().equals(s.world) && s.current != null) return true;
        ApplicableSet next = plugin.regions().query(to);
        ApplicableSet prev = s.current;
        boolean worldChanged = !w.getName().equals(s.world);
        if (prev != null && !worldChanged && next.sameRegions(prev)) {
            s.blockKey = key;
            s.lastValid = to.clone();
            return true;
        }
        if (prev == null || type == MoveType.JOIN) {
            s.current = next; s.world = w.getName(); s.blockKey = key; s.lastValid = to.clone();
            plugin.effects().refresh(p, s, next);
            if (type == MoveType.JOIN) for (Region r : next) entered(p, s, r, to, false);
            return true;
        }
        List<Region> left = new ArrayList<>(), entered = new ArrayList<>();
        for (Region r : prev) if (worldChanged || !next.contains(r)) left.add(r);
        for (Region r : next) if (worldChanged || !prev.contains(r)) entered.add(r);
        boolean teleport = type == MoveType.TELEPORT || type == MoveType.RESPAWN || type == MoveType.WORLD_CHANGE;

        if (!pr.bypasses(p) && type != MoveType.RESPAWN) {
            if (!left.isEmpty()) {
                Boolean override = pr.value(p, next, Flags.EXIT_OVERRIDE);
                boolean exitDenied = !pr.allowsFor(p, prev, Flags.EXIT) && (override == null || !override);
                if (!exitDenied && teleport && !pr.allowsFor(p, prev, Flags.EXIT_VIA_TELEPORT)) exitDenied = true;
                if (exitDenied) { denyMove(p, prev, Flags.EXIT_DENY_MESSAGE, "movement.exit-denied", left.get(0)); return false; }
            }
            if (!entered.isEmpty()) {
                String reason = entryBlock(p, s, next, entered);
                if (reason != null) { denyMove(p, next, Flags.ENTRY_DENY_MESSAGE, reason, entered.get(0)); return false; }
            }
        }
        for (Region r : left) {
            RegionLeaveEvent ev = new RegionLeaveEvent(p, r, to, teleport);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
        }
        for (Region r : entered) {
            RegionEnterEvent ev = new RegionEnterEvent(p, r, to, teleport);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
        }
        s.current = next; s.world = w.getName(); s.blockKey = key; s.lastValid = to.clone();
        for (Region r : left) left(p, s, r, to, type == MoveType.MOVE || type == MoveType.VEHICLE);
        for (Region r : entered) entered(p, s, r, to, type == MoveType.MOVE || type == MoveType.VEHICLE);
        if (!left.isEmpty()) plugin.effects().onLeave(s);
        plugin.effects().refresh(p, s, next);
        if (s.visualizeNearby) plugin.visualizer().nearbyChanged(p);
        return true;
    }

    /** Returns a message key when entry is blocked, null when allowed. */
    private String entryBlock(Player p, PlayerSession s, ApplicableSet next, List<Region> entered) {
        if (!pr.allowsFor(p, next, Flags.ENTRY)) return "movement.entry-denied";
        String perm = pr.value(p, next, Flags.ENTRY_PERMISSION);
        if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) return "movement.entry-permission";
        String dperm = pr.value(p, next, Flags.ENTRY_DENY_PERMISSION);
        if ((perm == null || perm.isBlank()) && dperm != null && !dperm.isBlank() && p.hasPermission(dperm)) return "movement.entry-permission";
        Integer min = pr.value(p, next, Flags.ENTRY_MIN_LEVEL), max = pr.value(p, next, Flags.ENTRY_MAX_LEVEL);
        if (min != null && p.getLevel() < min) return "movement.entry-level-low";
        if (max != null && p.getLevel() > max) return "movement.entry-level-high";
        for (Region r : entered) {
            Integer limit = FlagResolver.effective(r, Flags.PLAYER_COUNT_LIMIT, s.subject);
            if (limit != null) {
                int inside = 0;
                for (PlayerSession o : plugin.sessions().all()) if (o != s && o.current != null && o.current.contains(r)) inside++;
                if (inside >= limit) return "movement.entry-full";
            }
            Integer cd = FlagResolver.effective(r, Flags.ENTRY_COOLDOWN, s.subject);
            Long left = s.exitTimes.get(r.world() + ":" + r.id());
            if (cd != null && left != null && System.currentTimeMillis() - left < cd * 1000L) return "movement.entry-cooldown";
        }
        return null;
    }

    private void denyMove(Player p, ApplicableSet set, StringFlag custom, String key, Region r) {
        PlayerSession s = plugin.sessions().get(p);
        if (!s.cooldown("move-deny", 1200)) return;
        String msg = pr.value(p, set, custom);
        if (msg != null && !msg.isBlank()) p.sendMessage(plugin.text().userText(msg, p, "player", p.getName(), "region", r.displayName()));
        else plugin.text().send(p, key, "region", r.displayName());
        try { p.playSound(Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.MASTER, 0.5f, 0.5f)); } catch (Exception ignored) { }
    }

    private String text(Region r, PlayerSession s, StringFlag f) {
        String v = FlagResolver.effective(r, f, s.subject);
        return v == null || v.isBlank() ? null : v;
    }

    private Object[] ph(Player p, Region r) {
        return new Object[]{"player", p.getName(), "region", r.displayName(), "id", r.id(), "uuid", p.getUniqueId().toString(), "world", r.world()};
    }

    private void entered(Player p, PlayerSession s, Region r, Location to, boolean walked) {
        Object[] ph = ph(p, r);
        String g = text(r, s, Flags.GREETING);
        if (g != null) p.sendMessage(plugin.text().userText(g, p, ph));
        String gt = text(r, s, Flags.GREETING_TITLE);
        if (gt != null) showTitle(p, gt, ph);
        String ga = text(r, s, Flags.GREETING_ACTIONBAR);
        if (ga != null) p.sendActionBar(plugin.text().userText(ga, p, ph));
        String gs = text(r, s, Flags.GREETING_SOUND);
        if (gs != null) playSound(p, gs);
        Boolean n = FlagResolver.effective(r, Flags.NOTIFY_ENTER, s.subject);
        if (n != null && n) plugin.text().notify("fawg.notify", "notify.enter", ph);
        runCommands(p, r, s, Flags.COMMAND_ON_ENTRY, false);
        runCommands(p, r, s, Flags.CONSOLE_COMMAND_ON_ENTRY, true);
        if (walked) {
            LocationFlag.Loc tl = FlagResolver.effective(r, Flags.TELEPORT_ON_ENTRY, s.subject);
            if (tl != null) teleportLater(p, tl);
        }
    }

    private void left(Player p, PlayerSession s, Region r, Location to, boolean walked) {
        Object[] ph = ph(p, r);
        s.exitTimes.put(r.world() + ":" + r.id(), System.currentTimeMillis());
        String f = text(r, s, Flags.FAREWELL);
        if (f != null) p.sendMessage(plugin.text().userText(f, p, ph));
        String ft = text(r, s, Flags.FAREWELL_TITLE);
        if (ft != null) showTitle(p, ft, ph);
        String fa = text(r, s, Flags.FAREWELL_ACTIONBAR);
        if (fa != null) p.sendActionBar(plugin.text().userText(fa, p, ph));
        String fs = text(r, s, Flags.FAREWELL_SOUND);
        if (fs != null) playSound(p, fs);
        Boolean n = FlagResolver.effective(r, Flags.NOTIFY_LEAVE, s.subject);
        if (n != null && n) plugin.text().notify("fawg.notify", "notify.leave", ph);
        runCommands(p, r, s, Flags.COMMAND_ON_EXIT, false);
        runCommands(p, r, s, Flags.CONSOLE_COMMAND_ON_EXIT, true);
        if (walked) {
            LocationFlag.Loc tl = FlagResolver.effective(r, Flags.TELEPORT_ON_EXIT, s.subject);
            if (tl != null) teleportLater(p, tl);
        }
    }

    private void showTitle(Player p, String raw, Object[] ph) {
        String[] parts = raw.split("\\\\n|\n", 2);
        Component t = plugin.text().userText(parts[0], p, ph);
        Component sub = parts.length > 1 ? plugin.text().userText(parts[1], p, ph) : Component.empty();
        p.showTitle(Title.title(t, sub, Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
    }

    private void playSound(Player p, String key) {
        try { p.playSound(Sound.sound(Key.key(key.trim().toLowerCase()), Sound.Source.MASTER, 1f, 1f)); } catch (Exception ignored) { }
    }

    private void runCommands(Player p, Region r, PlayerSession s, dev.mattron1c.fawg.core.flag.StringSetFlag f, boolean console) {
        Set<String> cmds = FlagResolver.effective(r, f, s.subject);
        if (cmds == null || cmds.isEmpty()) return;
        for (String c : cmds) {
            String cmd = c.replace("%player%", p.getName()).replace("{player}", p.getName()).replace("%username%", p.getName()).replace("{username}", p.getName())
                    .replace("%uuid%", p.getUniqueId().toString()).replace("{uuid}", p.getUniqueId().toString()).replace("%region%", r.id()).replace("{region}", r.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (console) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                else if (p.isOnline()) p.performCommand(cmd);
            });
        }
    }

    private void teleportLater(Player p, LocationFlag.Loc l) {
        World w = Bukkit.getWorld(l.world());
        if (w == null) return;
        Location dest = new Location(w, l.x(), l.y(), l.z(), l.yaw(), l.pitch());
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN); });
    }

    // ===================================================================================== events
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo(), from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ() && to.getWorld() == from.getWorld()) return;
        Player p = e.getPlayer();
        if (!handleMove(p, to, MoveType.MOVE)) {
            PlayerSession s = plugin.sessions().get(p);
            Location back = s.lastValid != null && s.lastValid.getWorld() == from.getWorld() && s.lastValid.distanceSquared(from) < 64 ? s.lastValid.clone() : from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
            if (p.isGliding()) p.setGliding(false);
            p.setVelocity(from.toVector().subtract(to.toVector()).normalize().multiply(0.35).setY(0.15));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent e) {
        Location to = e.getTo(), from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;
        for (Entity passenger : e.getVehicle().getPassengers()) {
            if (passenger instanceof Player p && !handleMove(p, to, MoveType.VEHICLE)) {
                e.getVehicle().setVelocity(new org.bukkit.util.Vector());
                e.getVehicle().teleport(from);
                return;
            }
        }
        if (e.getVehicle().getPassengers().isEmpty()) {
            ApplicableSet target = pr.at(to);
            if (!target.isEmpty() && !pr.natural(target, Flags.VEHICLE_MOVE_IN) && !target.sameRegions(pr.at(from))) {
                e.getVehicle().setVelocity(new org.bukkit.util.Vector());
                e.getVehicle().teleport(from);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (!pr.bypasses(p)) {
            ApplicableSet target = pr.at(to), origin = pr.at(e.getFrom());
            StateFlag denied = null;
            String cn = cause.name();
            if (cn.equals("ENDER_PEARL") && (!pr.allowsFor(p, target, Flags.ENDERPEARL) || !pr.allowsFor(p, origin, Flags.ENDERPEARL))) denied = Flags.ENDERPEARL;
            else if ((cn.equals("CHORUS_FRUIT") || cn.equals("CONSUMABLE_EFFECT")) && (!pr.allowsFor(p, target, Flags.CHORUS_TELEPORT) || !pr.allowsFor(p, origin, Flags.CHORUS_TELEPORT))) denied = Flags.CHORUS_TELEPORT;
            else if (cn.equals("NETHER_PORTAL") && !pr.allowsFor(p, origin, Flags.NETHER_PORTALS)) denied = Flags.NETHER_PORTALS;
            else if ((cn.equals("END_PORTAL") || cn.equals("END_GATEWAY")) && !pr.allowsFor(p, origin, Flags.END_PORTALS)) denied = Flags.END_PORTALS;
            if (denied == null) {
                StateFlag cf = FlagFamilies.TELEPORT_CAUSE.get(cause);
                if (cf != null && !target.sameRegions(origin) && !pr.allowsFor(p, target, cf)) denied = cf;
                if (denied == null && !origin.isEmpty() && !target.sameRegions(origin) && cause != PlayerTeleportEvent.TeleportCause.UNKNOWN
                        && !pr.allowsFor(p, origin, Flags.TELEPORT_OUT)) denied = Flags.TELEPORT_OUT;
            }
            if (denied != null) {
                e.setCancelled(true);
                pr.deny(p, denied == Flags.TELEPORT_OUT ? origin : target, denied, to);
                return;
            }
        }
        if (!handleMove(p, to, MoveType.TELEPORT)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        Player p = e.getPlayer();
        if (pr.bypasses(p)) return;
        ApplicableSet origin = pr.at(e.getFrom());
        String cn = e.getCause().name();
        StateFlag f = cn.equals("NETHER_PORTAL") ? Flags.NETHER_PORTALS : (cn.equals("END_PORTAL") || cn.equals("END_GATEWAY")) ? Flags.END_PORTALS : null;
        if (f != null && !pr.allowsFor(p, origin, f)) { e.setCancelled(true); pr.deny(p, origin, f, e.getFrom()); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (e.getReason() != PortalCreateEvent.CreateReason.FIRE && e.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) return;
        Player p = e.getEntity() instanceof Player pl ? pl : null;
        for (var state : e.getBlocks()) {
            ApplicableSet set = pr.at(state.getLocation());
            if (p != null ? !pr.can(p, set, Flags.NETHER_PORTALS) || !pr.can(p, set, Flags.BLOCK_PLACE) : !pr.natural(set, Flags.NETHER_PORTALS)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        handleMove(e.getPlayer(), e.getPlayer().getLocation(), MoveType.WORLD_CHANGE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.bedrock().forget(p.getUniqueId());
        PlayerSession s = plugin.sessions().get(p);
        plugin.ghost().restoreOnJoin(p);
        ApplicableSet set = pr.at(p.getLocation());
        LocationFlag.Loc jl = pr.regionValue(p, set, Flags.JOIN_LOCATION);
        if (jl != null && Bukkit.getWorld(jl.world()) != null) {
            Location dest = new Location(Bukkit.getWorld(jl.world()), jl.x(), jl.y(), jl.z(), jl.yaw(), jl.pitch());
            p.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        handleMove(p, p.getLocation(), MoveType.JOIN);
        s.joinedAt = System.currentTimeMillis();
        if (p.hasPermission("fawg.admin")) plugin.adminJoinNotices(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerSession s = plugin.sessions().peek(p.getUniqueId());
        if (s != null) plugin.effects().cleanup(p, s);
        plugin.visualizer().stop(p);
        plugin.selections().forget(p.getUniqueId());
        plugin.sessions().remove(p.getUniqueId());
        plugin.bedrock().forget(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        ApplicableSet set = pr.at(p.getLocation());
        PlayerSession s = plugin.sessions().get(p);
        s.timers.put("death-x", (long) p.getLocation().getBlockX());
        s.cooldowns.put("death-loc", System.currentTimeMillis());
        plugin.sessions().get(p).lastValid = p.getLocation().clone();
        Boolean keepInv = pr.value(p, set, Flags.KEEP_INVENTORY);
        if (keepInv != null && keepInv) { e.setKeepInventory(true); e.getDrops().clear(); }
        Boolean keepExp = pr.value(p, set, Flags.KEEP_EXP);
        if (keepExp != null && keepExp) { e.setKeepLevel(true); e.setDroppedExp(0); }
        if (!pr.allowsFor(p, set, Flags.EXP_DROPS)) e.setDroppedExp(0);
        deathSets.put(p.getUniqueId(), set);
    }

    private final java.util.Map<java.util.UUID, ApplicableSet> deathSets = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = deathSets.remove(p.getUniqueId());
        if (set != null) {
            LocationFlag.Loc l = pr.value(p, set, Flags.RESPAWN_LOCATION);
            if (l == null) l = pr.value(p, set, Flags.SPAWN_LOC);
            if (l != null && Bukkit.getWorld(l.world()) != null) {
                e.setRespawnLocation(new Location(Bukkit.getWorld(l.world()), l.x(), l.y(), l.z(), l.yaw(), l.pitch()));
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) handleMove(p, p.getLocation(), MoveType.RESPAWN); });
    }

    // ===================================================================================== commands / chat
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        if (plugin.worldEdit() != null && plugin.worldEdit().guardCommand(p, msg)) { e.setCancelled(true); return; }
        if (pr.bypasses(p) || p.hasPermission("fawg.bypass.commands")) return;
        ApplicableSet set = pr.at(p.getLocation());
        String base = msg.split(" ")[0].toLowerCase();
        String noNs = base.contains(":") ? "/" + base.substring(base.indexOf(':') + 1) : base;
        String lower = msg.toLowerCase();
        Set<String> blocked = pr.value(p, set, Flags.BLOCKED_CMDS);
        if (blocked != null) for (String b : blocked) {
            if (b.equals("/*") || base.equals(b) || noNs.equals(b) || lower.startsWith(b + " ")) {
                e.setCancelled(true);
                plugin.text().send(p, "protection.command-blocked", "command", base);
                return;
            }
        }
        Set<String> allowed = pr.value(p, set, Flags.ALLOWED_CMDS);
        if (allowed != null && !allowed.isEmpty()) {
            boolean ok = false;
            for (String a : allowed) if (base.equals(a) || noNs.equals(a) || lower.startsWith(a + " ")) { ok = true; break; }
            if (!ok) { e.setCancelled(true); plugin.text().send(p, "protection.command-blocked", "command", base); }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.bypasses(p)) {
            if (!pr.allowsFor(p, set, Flags.SEND_CHAT)) {
                e.setCancelled(true);
                plugin.text().send(p, "protection.chat-blocked");
                return;
            }
            Integer cd = pr.value(p, set, Flags.CHAT_COOLDOWN);
            if (cd != null && cd > 0) {
                PlayerSession s = plugin.sessions().get(p);
                long now = System.currentTimeMillis();
                if (now - s.lastChat < cd * 1000L) {
                    e.setCancelled(true);
                    plugin.text().send(p, "protection.chat-cooldown", "seconds", Math.max(1, (cd * 1000L - (now - s.lastChat)) / 1000));
                    return;
                }
                s.lastChat = now;
            }
        }
        e.viewers().removeIf(a -> a instanceof Player v && !v.equals(p) && !pr.allowsFor(v, pr.at(v.getLocation()), Flags.RECEIVE_CHAT));
        String prefix = pr.value(p, set, Flags.CHAT_PREFIX), suffix = pr.value(p, set, Flags.CHAT_SUFFIX);
        if ((prefix != null && !prefix.isEmpty()) || (suffix != null && !suffix.isEmpty())) {
            Component pc = prefix == null ? Component.empty() : plugin.text().userText(prefix, p, "player", p.getName());
            Component sc = suffix == null ? Component.empty() : plugin.text().userText(suffix, p, "player", p.getName());
            ChatRenderer old = e.renderer();
            e.renderer((source, displayName, message, viewer) -> pc.append(old.render(source, displayName, message, viewer)).append(sc));
        }
    }

    // ===================================================================================== player state
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent e) {
        if (!e.isFlying()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("fawg.fly.bypass") || pr.bypasses(p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        if (pr.regionValue(p, set, Flags.FLY) == StateFlag.State.DENY) { e.setCancelled(true); pr.deny(p, set, Flags.FLY, null); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!e.isGliding() || !(e.getEntity() instanceof Player p) || pr.bypasses(p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        Flags.GlideMode g = pr.regionValue(p, set, Flags.GLIDE);
        if (g == Flags.GlideMode.DENY || g == Flags.GlideMode.FORCE) { e.setCancelled(true); pr.deny(p, set, Flags.ELYTRA_BOOST, null); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (!e.isSprinting()) return;
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.can(p, set, Flags.SPRINT)) { e.setCancelled(true); p.setSprinting(false); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwim(EntityToggleSwimEvent e) {
        if (!e.isSwimming() || !(e.getEntity() instanceof Player p)) return;
        if (!pr.can(p, pr.at(p.getLocation()), Flags.SWIM)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!pr.can(p, pr.at(p.getLocation()), Flags.JUMP)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent e) {
        Player p = e.getPlayer();
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.can(p, set, Flags.ELYTRA_BOOST) || !pr.can(p, set, FlagFamilies.useItem(org.bukkit.Material.FIREWORK_ROCKET).length > 0 ? FlagFamilies.useItem(org.bukkit.Material.FIREWORK_ROCKET)[0] : Flags.ELYTRA_BOOST)) {
            e.setCancelled(true);
            e.setShouldConsume(false);
            pr.deny(p, set, Flags.ELYTRA_BOOST, null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ApplicableSet set = pr.at(p.getLocation());
        if (!pr.allowsFor(p, set, Flags.HUNGER)) { e.setCancelled(true); return; }
        if (e.getFoodLevel() < p.getFoodLevel() && !pr.allowsFor(p, set, Flags.HUNGER_DRAIN)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED && e.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN) return;
        if (!pr.allowsFor(p, pr.at(p.getLocation()), Flags.HEALTH_REGEN)) e.setCancelled(true);
    }
}
