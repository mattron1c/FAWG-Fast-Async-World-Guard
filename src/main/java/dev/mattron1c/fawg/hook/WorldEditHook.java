package dev.mattron1c.fawg.hook;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.util.eventbus.EventHandler;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.flag.StateFlag;
import dev.mattron1c.fawg.core.index.ApplicableSet;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.flags.Flags;
import dev.mattron1c.fawg.protect.Protection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WorldEdit and FastAsyncWorldEdit integration. Only loaded when WorldEdit / FAWE is installed.
 *  - Protects regions from edit sessions of players who may not use WorldEdit / build there.
 *  - Blocks WorldEdit commands whose selection touches protected regions (RegionProtect style).
 *  - Imports / exports selections so /rg define works with the WorldEdit wand.
 *  - With FAWE, also registers a native mask manager (fast path, no per-block callbacks).
 */
public final class WorldEditHook {
    private final FAWG plugin;
    private final boolean fawe;

    public WorldEditHook(FAWG plugin) {
        this.plugin = plugin;
        this.fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    public boolean isFawe() { return fawe; }

    public void enable() {
        WorldEdit.getInstance().getEventBus().register(this);
        if (fawe && plugin.getConfig().getBoolean("worldedit.fawe-mask-manager", true)) {
            try {
                FaweHook.register(plugin);
                plugin.getLogger().info("Registered FAWE region mask manager (players can edit inside their own regions).");
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not register FAWE mask manager: " + t);
            }
        }
    }

    public void disable() {
        try { WorldEdit.getInstance().getEventBus().unregister(this); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------------------- edit sessions
    @Subscribe(priority = EventHandler.Priority.VERY_EARLY)
    public void onEditSession(EditSessionEvent e) {
        if (e.getStage() != EditSession.Stage.BEFORE_CHANGE) return;
        Actor actor = e.getActor();
        if (actor == null || !actor.isPlayer() || e.getWorld() == null) return;
        Player p = Bukkit.getPlayer(actor.getUniqueId());
        if (p == null) return;
        if (p.hasPermission("fawg.worldedit.bypass") || p.hasPermission("worldguardextraflagsplus.worldedit.bypass")) return;
        World w = BukkitAdapter.adapt(e.getWorld());
        if (w == null) return;
        e.setExtent(new ProtectedExtent(e.getExtent(), plugin, p, w));
    }

    /** Drops changes in regions where the player may not edit. Results are cached per chunk column. */
    public static final class ProtectedExtent extends AbstractDelegateExtent {
        private final FAWG plugin;
        private final Player player;
        private final RegionWorld world;
        private final boolean requireBuild, bypass;
        private long lastChunk = Long.MIN_VALUE;
        private int lastState; // 0 unknown, 1 allow all, 2 deny all, 3 per block
        private long denied;

        public ProtectedExtent(Extent extent, FAWG plugin, Player player, World world) {
            super(extent);
            this.plugin = plugin;
            this.player = player;
            this.world = plugin.regions().world(world);
            this.requireBuild = plugin.getConfig().getBoolean("worldedit.require-build", true);
            this.bypass = plugin.protection().bypasses(player);
        }

        private synchronized boolean allowed(int x, int y, int z) {
            int cx = x >> 4, cz = z >> 4;
            long key = ((long) cx << 32) | (cz & 0xffffffffL);
            if (key == lastChunk) {
                if (lastState == 1) return true;
                if (lastState == 2) return false;
            } else {
                lastChunk = key;
                lastState = 3;
                if (world.index().chunkEmpty(cx, cz)) lastState = test(world.query(x, y, z)) ? 1 : 2;
                else if (lastState == 3) {
                    // fully covered columns give the same answer for every block
                    Region[] rs = world.index().inChunk(cx, cz);
                    boolean full = true;
                    for (Region r : rs) if (!r.shape().coversChunk(cx, cz, world.minY(), world.maxY()) || !r.excludes().isEmpty()) { full = false; break; }
                    if (full) lastState = test(world.query(x, y, z)) ? 1 : 2;
                }
                if (lastState == 1) return true;
                if (lastState == 2) return false;
            }
            return test(world.query(x, y, z));
        }

        private boolean test(ApplicableSet set) {
            Protection pr = plugin.protection();
            if (pr.lockDenies(player, set, Flags.WORLDEDIT)) return false;
            if (bypass) return true;
            if (!pr.allowsFor(player, set, Flags.WORLDEDIT)) return false;
            return !requireBuild || pr.can(player, set, Flags.BLOCK_PLACE);
        }

        @Override
        public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 pos, B block) throws WorldEditException {
            if (!allowed(pos.x(), pos.y(), pos.z())) { denied++; return false; }
            return super.setBlock(pos, block);
        }

        @Override
        public <B extends BlockStateHolder<B>> boolean setBlock(int x, int y, int z, B block) throws WorldEditException {
            if (!allowed(x, y, z)) { denied++; return false; }
            return super.setBlock(x, y, z, block);
        }

        public long deniedCount() { return denied; }
    }

    // ------------------------------------------------------------------------------- command guard
    /** Returns true when the command must be cancelled. */
    public boolean guardCommand(Player p, String message) {
        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (!lower.startsWith("//") && !lower.startsWith("/br") && !lower.startsWith("/brush") && !lower.startsWith("/sp ") && !lower.equals("/sp")
                && !lower.startsWith("/superpickaxe") && !lower.startsWith("/worldedit:") && !lower.startsWith("/fastasyncworldedit:")) return false;
        if (p.hasPermission("fawg.worldedit.bypass") || p.hasPermission("fawg.lock.bypass")) return false;
        Protection pr = plugin.protection();
        String cmd = lower.split(" ")[0];
        if (cmd.contains(":")) cmd = "/" + cmd.substring(cmd.indexOf(':') + 1);
        ApplicableSet here = pr.at(p.getLocation());
        if (cmd.equals("//") || cmd.equals("/sp") || cmd.equals("/superpickaxe") || cmd.equals("//sp")) {
            if (!pr.can(p, here, Flags.SUPERPICKAXE)) { deny(p, cmd, here.highest()); return true; }
            return false;
        }
        if (cmd.startsWith("/br") || cmd.startsWith("//br") || cmd.equals("/brush") || cmd.equals("//brush")) {
            if (!pr.can(p, here, Flags.WORLD_EDIT_BRUSHES)) { deny(p, cmd, here.highest()); return true; }
            return false;
        }
        List<String> guarded = plugin.getConfig().getStringList("worldedit.guarded-commands");
        List<String> radius = plugin.getConfig().getStringList("worldedit.radius-commands");
        boolean isRadius = radius.contains(cmd);
        if (!guarded.contains(cmd) && !isRadius) return false;
        RegionWorld rw = plugin.regions().world(p.getWorld());
        int[] box;
        if (isRadius) {
            int r = 16;
            for (String part : lower.split(" ")) {
                try { r = Math.max(1, (int) Math.ceil(Double.parseDouble(part.split(",")[0]))); break; } catch (NumberFormatException ignored) { }
            }
            var l = p.getLocation();
            box = new int[]{l.getBlockX() - r, l.getBlockY() - r, l.getBlockZ() - r, l.getBlockX() + r, l.getBlockY() + r, l.getBlockZ() + r};
        } else {
            try {
                LocalSession s = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
                com.sk89q.worldedit.regions.Region sel = s.getSelection(s.getSelectionWorld());
                BlockVector3 a = sel.getMinimumPoint(), b = sel.getMaximumPoint();
                box = new int[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z()};
            } catch (IncompleteRegionException | NullPointerException ex) {
                return false;
            }
        }
        for (Region r : rw.index().intersecting(box[0], box[1], box[2], box[3], box[4], box[5])) {
            ApplicableSet single = new ApplicableSet(new Region[]{r}, rw.global());
            if (pr.lockDenies(p, single, Flags.WORLDEDIT_COMMANDS) || (!pr.bypasses(p) && (!pr.allowsFor(p, single, Flags.WORLDEDIT_COMMANDS) || !pr.allowsFor(p, single, Flags.WORLDEDIT)))) {
                deny(p, cmd, r);
                return true;
            }
        }
        return false;
    }

    private void deny(Player p, String cmd, Region r) {
        plugin.text().send(p, "worldedit.denied", "command", cmd, "region", r == null ? "__global__" : r.displayName());
        if (plugin.getConfig().getBoolean("spy.worldedit", true)) {
            var l = p.getLocation();
            plugin.text().notify("fawg.spy", "spy.worldedit", "player", p.getName(), "command", cmd, "region", r == null ? "__global__" : r.id(),
                    "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "world", l.getWorld().getName());
            String snd = plugin.getConfig().getString("spy.sound", "block.anvil.place");
            for (Player o : Bukkit.getOnlinePlayers()) if (o.hasPermission("fawg.spy")) {
                try { o.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(snd), net.kyori.adventure.sound.Sound.Source.MASTER, 0.6f, 1f)); } catch (Exception ignored) { }
            }
        }
        plugin.actionLog().log("WORLDEDIT-DENY " + p.getName() + " " + cmd + " " + (r == null ? "__global__" : r.id()));
    }

    // ------------------------------------------------------------------------------- selections
    /** Reads the player's WorldEdit selection as a FAWG shape, or null. */
    public Shape selection(Player p) {
        try {
            LocalSession s = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
            com.sk89q.worldedit.world.World w = s.getSelectionWorld();
            if (w == null || !w.getName().equals(p.getWorld().getName())) return null;
            com.sk89q.worldedit.regions.Region r = s.getSelection(w);
            if (r instanceof Polygonal2DRegion poly) {
                List<int[]> pts = new ArrayList<>();
                for (BlockVector2 v : poly.getPoints()) pts.add(new int[]{v.x(), v.z()});
                return Shape.Polygon.of(pts, poly.getMinimumY(), poly.getMaximumY());
            }
            if (r instanceof CylinderRegion cyl) {
                var c = cyl.getCenter();
                var rad = cyl.getRadius();
                return new Shape.Cylinder((int) Math.floor(c.x()), (int) Math.floor(c.z()), (int) Math.round(rad.x() - 0.5), (int) Math.round(rad.z() - 0.5), cyl.getMinimumY(), cyl.getMaximumY());
            }
            if (r instanceof EllipsoidRegion el) {
                var c = el.getCenter();
                var rad = el.getRadius();
                return new Shape.Ellipsoid((int) Math.floor(c.x()), (int) Math.floor(c.y()), (int) Math.floor(c.z()), (int) Math.round(rad.x() - 0.5), (int) Math.round(rad.y() - 0.5), (int) Math.round(rad.z() - 0.5));
            }
            BlockVector3 a = r.getMinimumPoint(), b = r.getMaximumPoint();
            return Shape.Cuboid.of(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
        } catch (IncompleteRegionException e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copies a FAWG shape into the player's WorldEdit selection (cuboid / polygon; others use bounds). */
    public void select(Player p, Shape shape) {
        try {
            LocalSession s = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
            com.sk89q.worldedit.world.World w = BukkitAdapter.adapt(p.getWorld());
            if (shape instanceof Shape.Polygon poly) {
                List<BlockVector2> pts = new ArrayList<>();
                for (int[] pt : poly.points()) pts.add(BlockVector2.at(pt[0], pt[1]));
                s.setRegionSelector(w, new Polygonal2DRegionSelector(w, pts, poly.minY(), poly.maxY()));
            } else {
                s.setRegionSelector(w, new CuboidRegionSelector(w, BlockVector3.at(shape.minX(), shape.minY(), shape.minZ()), BlockVector3.at(shape.maxX(), shape.maxY(), shape.maxZ())));
            }
            s.dispatchCUISelection(BukkitAdapter.adapt(p));
        } catch (Throwable ignored) { }
    }

    /** Converts a FAWG region to a WorldEdit region (FAWE masks). */
    public static com.sk89q.worldedit.regions.Region toWorldEdit(World w, Shape s) {
        com.sk89q.worldedit.world.World ww = BukkitAdapter.adapt(w);
        if (s instanceof Shape.Polygon poly) {
            List<BlockVector2> pts = new ArrayList<>();
            for (int[] pt : poly.points()) pts.add(BlockVector2.at(pt[0], pt[1]));
            return new Polygonal2DRegion(ww, pts, poly.minY(), poly.maxY());
        }
        return new CuboidRegion(ww, BlockVector3.at(s.minX(), s.minY(), s.minZ()), BlockVector3.at(s.maxX(), s.maxY(), s.maxZ()));
    }

    public static boolean isStateDeny(StateFlag.State s) { return s == StateFlag.State.DENY; }
}
