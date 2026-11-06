package dev.mattron1c.fawg.visual;

import dev.mattron1c.fawg.FAWG;
import dev.mattron1c.fawg.core.index.RegionWorld;
import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.shape.Shape;
import dev.mattron1c.fawg.select.Selection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Red particle outlines for regions and selections. Only edges near the player are drawn and the number
 * of particles per tick is capped, so outlining a 10k x 10k region costs the same as a small one.
 * Uses plain dust particles, which also render for Bedrock players through Geyser.
 */
public final class Visualizer {
    private record Item(String world, Shape shape, Color color, long expires, String label) {}

    private static final class View {
        final List<Item> items = new ArrayList<>();
        boolean selection;
        boolean nearby;
    }

    private final FAWG plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private BukkitTask task;
    private Color regionColor, selectionColor, childColor;
    private int maxParticles, range;
    private float size;

    public Visualizer(FAWG plugin) { this.plugin = plugin; }

    public void start() {
        var c = plugin.getConfig();
        regionColor = color(c.getString("visualizer.region-color", "#ff2a2a"));
        selectionColor = color(c.getString("visualizer.selection-color", "#ff8080"));
        childColor = color(c.getString("visualizer.nearby-color", "#b30000"));
        maxParticles = c.getInt("visualizer.max-particles", 700);
        range = c.getInt("visualizer.range", 48);
        size = (float) c.getDouble("visualizer.particle-size", 1.1);
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, Math.max(2, c.getInt("visualizer.interval-ticks", 10)));
    }

    public void shutdown() { if (task != null) task.cancel(); views.clear(); }

    private static Color color(String hex) {
        try { return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)); } catch (Exception e) { return Color.RED; }
    }

    private View view(Player p) { return views.computeIfAbsent(p.getUniqueId(), k -> new View()); }

    public void showRegion(Player p, Region r, int seconds) {
        View v = view(p);
        v.items.removeIf(i -> i.label != null && i.label.equals(r.id()));
        v.items.add(new Item(r.world(), r.shape(), regionColor, System.currentTimeMillis() + seconds * 1000L, r.id()));
        for (Shape ex : r.excludes()) v.items.add(new Item(r.world(), ex, Color.fromRGB(0x550000), System.currentTimeMillis() + seconds * 1000L, r.id()));
    }

    public void showSelection(Player p) { view(p).selection = true; }

    public void hideSelection(Player p) { View v = views.get(p.getUniqueId()); if (v != null) v.selection = false; }

    public boolean toggleNearby(Player p) {
        View v = view(p);
        v.nearby = !v.nearby;
        plugin.sessions().get(p).visualizeNearby = v.nearby;
        return v.nearby;
    }

    public void nearbyChanged(Player p) { /* nearby view is recomputed each tick */ }

    public void stop(Player p) { views.remove(p.getUniqueId()); }

    public void clear(Player p) {
        View v = views.get(p.getUniqueId());
        if (v != null) { v.items.clear(); v.selection = false; v.nearby = false; }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            View v = views.get(p.getUniqueId());
            if (v == null) continue;
            v.items.removeIf(i -> i.expires < now);
            int budget = maxParticles;
            String w = p.getWorld().getName();
            if (v.selection) {
                Selection s = plugin.selections().peek(p);
                if (s != null && w.equals(s.world)) {
                    if (s.complete()) budget -= draw(p, s.toShape(), selectionColor, budget);
                    else budget -= drawPoints(p, s, budget);
                }
            }
            for (Item i : v.items) {
                if (budget <= 0) break;
                if (w.equals(i.world)) budget -= draw(p, i.shape, i.color, budget);
            }
            if (v.nearby && budget > 0) {
                RegionWorld rw = plugin.regions().world(p.getWorld());
                Location l = p.getLocation();
                for (Region r : rw.index().intersecting(l.getBlockX() - range, l.getBlockY() - range, l.getBlockZ() - range, l.getBlockX() + range, l.getBlockY() + range, l.getBlockZ() + range)) {
                    if (budget <= 0) break;
                    budget -= draw(p, r.shape(), childColor, budget);
                }
            }
            if (v.items.isEmpty() && !v.selection && !v.nearby) views.remove(p.getUniqueId());
        }
    }

    private int drawPoints(Player p, Selection s, int budget) {
        int used = 0;
        Particle.DustOptions d = new Particle.DustOptions(selectionColor, size * 1.4f);
        if (s.pos1 != null) { spawn(p, s.pos1[0] + 0.5, s.pos1[1] + 0.5, s.pos1[2] + 0.5, d); used++; }
        if (s.pos2 != null) { spawn(p, s.pos2[0] + 0.5, s.pos2[1] + 0.5, s.pos2[2] + 0.5, d); used++; }
        for (int[] pt : s.points) { for (int dy = 0; dy < 3; dy++) spawn(p, pt[0] + 0.5, p.getLocation().getBlockY() + dy, pt[1] + 0.5, d); used += 3; }
        return used;
    }

    private void spawn(Player p, double x, double y, double z, Particle.DustOptions d) {
        p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, d);
    }

    /** Draws the outline of a shape around the player. Returns particles used. */
    private int draw(Player p, Shape s, Color c, int budget) {
        Location l = p.getLocation();
        double px = l.getX(), py = l.getY(), pz = l.getZ();
        Particle.DustOptions d = new Particle.DustOptions(c, size);
        int[] used = {0};
        double r2 = (double) range * range;
        // vertical levels: real top / bottom when near, plus rings around the player's height for tall shapes
        List<Double> levels = new ArrayList<>();
        int minY = Math.max(s.minY(), p.getWorld().getMinHeight()), maxY = Math.min(s.maxY() + 1, p.getWorld().getMaxHeight());
        if (Math.abs(minY - py) < range) levels.add((double) minY);
        if (Math.abs(maxY - py) < range) levels.add((double) maxY);
        for (int dy = -2; dy <= 4; dy += 3) {
            double y = Math.floor(py) + dy;
            if (y > minY + 1 && y < maxY - 1) levels.add(y);
        }
        java.util.function.DoublePredicate near = v -> true;
        if (s instanceof Shape.Cuboid || s instanceof Shape.Global) {
            if (s instanceof Shape.Global) return 0;
            double x0 = s.minX(), x1 = s.maxX() + 1, z0 = s.minZ(), z1 = s.maxZ() + 1;
            double[][] edges = {{x0, z0, x1, z0}, {x1, z0, x1, z1}, {x1, z1, x0, z1}, {x0, z1, x0, z0}};
            for (double y : levels) for (double[] e : edges) line(p, e[0], y, e[1], e[2], y, e[3], d, used, budget, px, py, pz, r2);
            double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
            for (double[] k : corners) line(p, k[0], Math.max(minY, py - range), k[1], k[0], Math.min(maxY, py + range), k[1], d, used, budget, px, py, pz, r2);
        } else if (s instanceof Shape.Polygon poly) {
            int[] xs = poly.xs(), zs = poly.zs();
            for (double y : levels) for (int i = 0; i < xs.length; i++) {
                int j = (i + 1) % xs.length;
                line(p, xs[i] + 0.5, y, zs[i] + 0.5, xs[j] + 0.5, y, zs[j] + 0.5, d, used, budget, px, py, pz, r2);
            }
            for (int i = 0; i < xs.length; i++) line(p, xs[i] + 0.5, Math.max(minY, py - range), zs[i] + 0.5, xs[i] + 0.5, Math.min(maxY, py + range), zs[i] + 0.5, d, used, budget, px, py, pz, r2);
        } else if (s instanceof Shape.Cylinder cy) {
            for (double y : levels) ellipse(p, cy.cx() + 0.5, y, cy.cz() + 0.5, cy.rx() + 0.5, cy.rz() + 0.5, d, used, budget, px, py, pz, r2);
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                double x = cy.cx() + 0.5 + Math.cos(ang) * (cy.rx() + 0.5), z = cy.cz() + 0.5 + Math.sin(ang) * (cy.rz() + 0.5);
                line(p, x, Math.max(minY, py - range), z, x, Math.min(maxY, py + range), z, d, used, budget, px, py, pz, r2);
            }
        } else if (s instanceof Shape.Ellipsoid el) {
            double cx = el.cx() + 0.5, cy = el.cy() + 0.5, cz = el.cz() + 0.5;
            int steps = (int) Math.min(240, Math.max(24, (el.rx() + el.ry() + el.rz()) * 4));
            for (int i = 0; i < steps && used[0] < budget; i++) {
                double t = 2 * Math.PI * i / steps;
                point(p, cx + Math.cos(t) * (el.rx() + 0.5), cy, cz + Math.sin(t) * (el.rz() + 0.5), d, used, px, py, pz, r2);
                point(p, cx + Math.cos(t) * (el.rx() + 0.5), cy + Math.sin(t) * (el.ry() + 0.5), cz, d, used, px, py, pz, r2);
                point(p, cx, cy + Math.cos(t) * (el.ry() + 0.5), cz + Math.sin(t) * (el.rz() + 0.5), d, used, px, py, pz, r2);
            }
        }
        return used[0];
    }

    private void ellipse(Player p, double cx, double y, double cz, double rx, double rz, Particle.DustOptions d, int[] used, int budget, double px, double py, double pz, double r2) {
        int steps = (int) Math.min(360, Math.max(16, (rx + rz) * 3));
        for (int i = 0; i < steps && used[0] < budget; i++) {
            double t = 2 * Math.PI * i / steps;
            point(p, cx + Math.cos(t) * rx, y, cz + Math.sin(t) * rz, d, used, px, py, pz, r2);
        }
    }

    private void line(Player p, double x0, double y0, double z0, double x1, double y1, double z1, Particle.DustOptions d, int[] used, int budget, double px, double py, double pz, double r2) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01) { point(p, x0, y0, z0, d, used, px, py, pz, r2); return; }
        // only walk the part of the line that is within range: clamp by projecting the player onto the line
        double t0 = 0, t1 = len;
        double ux = dx / len, uy = dy / len, uz = dz / len;
        double proj = (px - x0) * ux + (py - y0) * uy + (pz - z0) * uz;
        double reach = Math.sqrt(r2);
        t0 = Math.max(0, proj - reach);
        t1 = Math.min(len, proj + reach);
        double step = 0.5;
        for (double t = t0; t <= t1 && used[0] < budget; t += step) point(p, x0 + ux * t, y0 + uy * t, z0 + uz * t, d, used, px, py, pz, r2);
    }

    private void point(Player p, double x, double y, double z, Particle.DustOptions d, int[] used, double px, double py, double pz, double r2) {
        double ddx = x - px, ddy = y - py, ddz = z - pz;
        if (ddx * ddx + ddy * ddy + ddz * ddz > r2) return;
        p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, d);
        used[0]++;
    }
}
