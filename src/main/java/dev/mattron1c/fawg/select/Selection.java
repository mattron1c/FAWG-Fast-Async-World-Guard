package dev.mattron1c.fawg.select;

import dev.mattron1c.fawg.core.shape.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A player's in-progress selection. Supports cuboid, polygon, cylinder and sphere. */
public final class Selection {
    public enum Type {
        CUBOID, POLY, CYLINDER, SPHERE;

        public static Type parse(String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "cuboid", "cube", "box", "c" -> CUBOID;
                case "poly", "polygon", "poly2d", "p" -> POLY;
                case "cyl", "cylinder", "circle" -> CYLINDER;
                case "sphere", "ellipsoid", "ball", "s" -> SPHERE;
                default -> null;
            };
        }
    }

    public Type type = Type.CUBOID;
    public String world;
    public int[] pos1, pos2;
    public final List<int[]> points = new ArrayList<>();
    public int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    public int version;

    public void reset(Type t) {
        type = t; pos1 = null; pos2 = null; points.clear(); minY = Integer.MAX_VALUE; maxY = Integer.MIN_VALUE; version++;
    }

    public void primary(String w, int x, int y, int z) {
        if (world != null && !world.equals(w)) reset(type);
        world = w;
        switch (type) {
            case POLY -> { points.clear(); points.add(new int[]{x, z}); minY = y; maxY = y; }
            default -> pos1 = new int[]{x, y, z};
        }
        version++;
    }

    public void secondary(String w, int x, int y, int z) {
        if (world != null && !world.equals(w)) reset(type);
        world = w;
        switch (type) {
            case POLY -> { points.add(new int[]{x, z}); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
            default -> pos2 = new int[]{x, y, z};
        }
        version++;
    }

    public boolean complete() {
        return switch (type) {
            case POLY -> points.size() >= 3;
            default -> pos1 != null && pos2 != null;
        };
    }

    /** Converts to a region shape; throws IllegalStateException with a user friendly reason. */
    public Shape toShape() {
        if (!complete()) throw new IllegalStateException(type == Type.POLY ? "Select at least 3 points" : "Select two positions first");
        return switch (type) {
            case CUBOID -> Shape.Cuboid.of(pos1[0], pos1[1], pos1[2], pos2[0], pos2[1], pos2[2]);
            case POLY -> Shape.Polygon.of(points, minY, maxY);
            case CYLINDER -> {
                int rx = Math.abs(pos2[0] - pos1[0]), rz = Math.abs(pos2[2] - pos1[2]);
                int r = Math.max(rx, rz);
                yield new Shape.Cylinder(pos1[0], pos1[2], rx == 0 ? r : rx, rz == 0 ? r : rz, Math.min(pos1[1], pos2[1]), Math.max(pos1[1], pos2[1]));
            }
            case SPHERE -> {
                int r = (int) Math.round(Math.sqrt(Math.pow(pos2[0] - pos1[0], 2) + Math.pow(pos2[1] - pos1[1], 2) + Math.pow(pos2[2] - pos1[2], 2)));
                yield new Shape.Ellipsoid(pos1[0], pos1[1], pos1[2], r, r, r);
            }
        };
    }

    /** Loads a region shape back into the selection (used by /rg select). */
    public void from(String w, Shape s) {
        world = w;
        if (s instanceof Shape.Polygon p) {
            reset(Type.POLY);
            world = w;
            points.addAll(p.points());
            minY = p.minY(); maxY = p.maxY();
        } else if (s instanceof Shape.Cylinder c) {
            reset(Type.CYLINDER);
            world = w;
            pos1 = new int[]{c.cx(), c.minY(), c.cz()};
            pos2 = new int[]{c.cx() + c.rx(), c.maxY(), c.cz() + c.rz()};
        } else if (s instanceof Shape.Ellipsoid e) {
            reset(Type.SPHERE);
            world = w;
            pos1 = new int[]{e.cx(), e.cy(), e.cz()};
            pos2 = new int[]{e.cx() + e.rx(), e.cy(), e.cz()};
        } else {
            reset(Type.CUBOID);
            world = w;
            pos1 = new int[]{s.minX(), s.minY(), s.minZ()};
            pos2 = new int[]{s.maxX(), s.maxY(), s.maxZ()};
        }
        version++;
    }

    // ----------------------------------------------------------------------------------- modifiers
    public void expandVert(int min, int max) {
        if (type == Type.POLY) { minY = min; maxY = max; }
        else if (pos1 != null && pos2 != null) {
            if (pos1[1] <= pos2[1]) { pos1[1] = min; pos2[1] = max; } else { pos2[1] = min; pos1[1] = max; }
        }
        version++;
    }

    /** dx/dy/dz unit direction; positive amount grows the selection that way, negative shrinks. */
    public void expand(int amount, int dx, int dy, int dz) {
        if (type == Type.POLY) {
            if (dy > 0) maxY += amount; else if (dy < 0) minY -= amount;
            else for (int[] p : points) { /* horizontal polygon expansion is not supported */ }
            version++;
            return;
        }
        if (pos1 == null || pos2 == null) return;
        int[] hi = new int[3], lo = new int[3];
        for (int i = 0; i < 3; i++) { hi[i] = Math.max(pos1[i], pos2[i]); lo[i] = Math.min(pos1[i], pos2[i]); }
        int[] d = {dx, dy, dz};
        for (int i = 0; i < 3; i++) {
            if (d[i] > 0) hi[i] += amount;
            else if (d[i] < 0) lo[i] -= amount;
        }
        pos1 = lo; pos2 = hi;
        version++;
    }

    public void shift(int amount, int dx, int dy, int dz) {
        int ox = dx * amount, oy = dy * amount, oz = dz * amount;
        if (pos1 != null) { pos1[0] += ox; pos1[1] += oy; pos1[2] += oz; }
        if (pos2 != null) { pos2[0] += ox; pos2[1] += oy; pos2[2] += oz; }
        for (int[] p : points) { p[0] += ox; p[1] += oz; }
        if (type == Type.POLY) { minY += oy; maxY += oy; }
        version++;
    }

    public String describe() {
        if (!complete()) {
            return switch (type) {
                case POLY -> points.size() + " point(s)";
                default -> (pos1 == null ? "pos1 unset" : "pos1 " + fmt(pos1)) + ", " + (pos2 == null ? "pos2 unset" : "pos2 " + fmt(pos2));
            };
        }
        Shape s = toShape();
        return s.type() + " · " + String.format("%,d", s.volume()) + " blocks · " + fmt(new int[]{s.minX(), s.minY(), s.minZ()}) + " → " + fmt(new int[]{s.maxX(), s.maxY(), s.maxZ()});
    }

    private static String fmt(int[] v) { return v[0] + ", " + v[1] + ", " + v[2]; }
}
