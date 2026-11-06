package dev.mattron1c.fawg.core.shape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable region geometry. Every shape is integer block based, bounds are inclusive.
 * Shapes are pure data and are safe to read from any thread (FAWE async edits included).
 */
public sealed interface Shape permits Shape.Cuboid, Shape.Polygon, Shape.Cylinder, Shape.Ellipsoid, Shape.Global {

    int WORLD_MIN_Y = -2048;
    int WORLD_MAX_Y = 4095;

    boolean contains(int x, int y, int z);

    int minX();
    int minY();
    int minZ();
    int maxX();
    int maxY();
    int maxZ();

    /** Short type id used in storage and commands. */
    String type();

    /** Approximate number of blocks inside the shape. */
    long volume();

    Shape translate(int dx, int dy, int dz);

    /** Returns a copy whose vertical range is replaced. */
    Shape withHeight(int minY, int maxY);

    Map<String, Object> serialize();

    /** True when the whole 16x16 chunk column (for the given vertical range) lies inside this shape. */
    default boolean coversChunk(int cx, int cz, int worldMinY, int worldMaxY) {
        return false;
    }

    default boolean intersectsChunk(int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        return maxX() >= x0 && minX() <= x0 + 15 && maxZ() >= z0 && minZ() <= z0 + 15;
    }

    default boolean boundsIntersect(Shape o) {
        return maxX() >= o.minX() && minX() <= o.maxX()
                && maxY() >= o.minY() && minY() <= o.maxY()
                && maxZ() >= o.minZ() && minZ() <= o.maxZ();
    }

    default String describe() {
        return type() + " (" + minX() + ", " + minY() + ", " + minZ() + ") → (" + maxX() + ", " + maxY() + ", " + maxZ() + ")";
    }

    // ------------------------------------------------------------------------------------------------
    // Cuboid
    // ------------------------------------------------------------------------------------------------
    record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements Shape {
        public Cuboid {
            if (minX > maxX) { int t = minX; minX = maxX; maxX = t; }
            if (minY > maxY) { int t = minY; minY = maxY; maxY = t; }
            if (minZ > maxZ) { int t = minZ; minZ = maxZ; maxZ = t; }
        }

        public static Cuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Cuboid(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        @Override public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        @Override public String type() { return "cuboid"; }
        @Override public long volume() { return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }
        @Override public Shape translate(int dx, int dy, int dz) { return new Cuboid(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz); }
        @Override public Shape withHeight(int a, int b) { return new Cuboid(minX, a, minZ, maxX, b, maxZ); }
        @Override public boolean coversChunk(int cx, int cz, int wMin, int wMax) {
            int x0 = cx << 4, z0 = cz << 4;
            return minX <= x0 && maxX >= x0 + 15 && minZ <= z0 && maxZ >= z0 + 15 && minY <= wMin && maxY >= wMax;
        }
        @Override public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "cuboid");
            m.put("min", vec(minX, minY, minZ));
            m.put("max", vec(maxX, maxY, maxZ));
            return m;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 2D polygon extruded vertically (WorldGuard "poly2d" compatible semantics: edges are inside)
    // ------------------------------------------------------------------------------------------------
    record Polygon(int[] xs, int[] zs, int minY, int maxY, int minX, int minZ, int maxX, int maxZ) implements Shape {
        public static Polygon of(List<int[]> points, int minY, int maxY) {
            if (points.size() < 3) throw new IllegalArgumentException("A polygon needs at least 3 points");
            int n = points.size();
            int[] xs = new int[n], zs = new int[n];
            int mnx = Integer.MAX_VALUE, mnz = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mxz = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i)[0];
                zs[i] = points.get(i)[1];
                mnx = Math.min(mnx, xs[i]); mxx = Math.max(mxx, xs[i]);
                mnz = Math.min(mnz, zs[i]); mxz = Math.max(mxz, zs[i]);
            }
            return new Polygon(xs, zs, Math.min(minY, maxY), Math.max(minY, maxY), mnx, mnz, mxx, mxz);
        }

        public List<int[]> points() {
            List<int[]> l = new ArrayList<>(xs.length);
            for (int i = 0; i < xs.length; i++) l.add(new int[]{xs[i], zs[i]});
            return l;
        }

        @Override public boolean contains(int x, int y, int z) {
            if (y < minY || y > maxY || x < minX || x > maxX || z < minZ || z > maxZ) return false;
            final int n = xs.length;
            boolean inside = false;
            int px = xs[n - 1], pz = zs[n - 1];
            for (int i = 0; i < n; i++) {
                int cx = xs[i], cz = zs[i];
                if (cx == x && cz == z) return true;
                int ax, az, bx, bz;
                if (cx > px) { ax = px; az = pz; bx = cx; bz = cz; } else { ax = cx; az = cz; bx = px; bz = pz; }
                if (ax <= x && x <= bx) {
                    long cross = ((long) z - az) * (bx - ax) - ((long) bz - az) * (x - ax);
                    if (cross == 0) {
                        if ((az <= z) == (z <= bz)) return true; // exactly on an edge
                    } else if (cross < 0 && ax != x) {
                        inside = !inside;
                    }
                }
                px = cx; pz = cz;
            }
            return inside;
        }
        @Override public String type() { return "poly2d"; }
        @Override public long volume() {
            long area2 = 0;
            int n = xs.length;
            for (int i = 0, j = n - 1; i < n; j = i++) area2 += (long) xs[j] * zs[i] - (long) xs[i] * zs[j];
            return Math.max(1, Math.abs(area2) / 2) * (maxY - minY + 1);
        }
        @Override public Shape translate(int dx, int dy, int dz) {
            List<int[]> p = new ArrayList<>();
            for (int i = 0; i < xs.length; i++) p.add(new int[]{xs[i] + dx, zs[i] + dz});
            return of(p, minY + dy, maxY + dy);
        }
        @Override public Shape withHeight(int a, int b) { return of(points(), a, b); }
        @Override public boolean coversChunk(int cx, int cz, int wMin, int wMax) {
            if (minY > wMin || maxY < wMax) return false;
            int x0 = cx << 4, z0 = cz << 4;
            // cheap conservative test: all four corners + center inside and polygon is not concave across it
            return contains(x0, minY, z0) && contains(x0 + 15, minY, z0) && contains(x0, minY, z0 + 15)
                    && contains(x0 + 15, minY, z0 + 15) && noVertexInside(x0, z0) ;
        }
        private boolean noVertexInside(int x0, int z0) {
            for (int i = 0; i < xs.length; i++) if (xs[i] > x0 && xs[i] < x0 + 15 && zs[i] > z0 && zs[i] < z0 + 15) return false;
            return true;
        }
        @Override public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "poly2d");
            m.put("min-y", minY);
            m.put("max-y", maxY);
            List<Map<String, Object>> pts = new ArrayList<>();
            for (int i = 0; i < xs.length; i++) { Map<String, Object> p = new LinkedHashMap<>(); p.put("x", xs[i]); p.put("z", zs[i]); pts.add(p); }
            m.put("points", pts);
            return m;
        }
        @Override public boolean equals(Object o) {
            return o instanceof Polygon p && p.minY == minY && p.maxY == maxY && java.util.Arrays.equals(p.xs, xs) && java.util.Arrays.equals(p.zs, zs);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(xs) * 31 + java.util.Arrays.hashCode(zs) + minY * 7 + maxY; }
    }

    // ------------------------------------------------------------------------------------------------
    // Elliptic cylinder
    // ------------------------------------------------------------------------------------------------
    record Cylinder(int cx, int cz, int rx, int rz, int minY, int maxY) implements Shape {
        public Cylinder {
            rx = Math.max(0, rx); rz = Math.max(0, rz);
            if (minY > maxY) { int t = minY; minY = maxY; maxY = t; }
        }
        @Override public boolean contains(int x, int y, int z) {
            if (y < minY || y > maxY) return false;
            double dx = (x - cx) / (rx + 0.5), dz = (z - cz) / (rz + 0.5);
            return dx * dx + dz * dz <= 1.0;
        }
        @Override public int minX() { return cx - rx; }
        @Override public int maxX() { return cx + rx; }
        @Override public int minZ() { return cz - rz; }
        @Override public int maxZ() { return cz + rz; }
        @Override public String type() { return "cylinder"; }
        @Override public long volume() { return Math.max(1, Math.round(Math.PI * (rx + 0.5) * (rz + 0.5))) * (maxY - minY + 1); }
        @Override public Shape translate(int dx, int dy, int dz) { return new Cylinder(cx + dx, cz + dz, rx, rz, minY + dy, maxY + dy); }
        @Override public Shape withHeight(int a, int b) { return new Cylinder(cx, cz, rx, rz, a, b); }
        @Override public boolean coversChunk(int chx, int chz, int wMin, int wMax) {
            if (minY > wMin || maxY < wMax) return false;
            int x0 = chx << 4, z0 = chz << 4;
            return contains(x0, minY, z0) && contains(x0 + 15, minY, z0) && contains(x0, minY, z0 + 15) && contains(x0 + 15, minY, z0 + 15);
        }
        @Override public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "cylinder");
            m.put("center", vec(cx, 0, cz));
            m.put("radius-x", rx);
            m.put("radius-z", rz);
            m.put("min-y", minY);
            m.put("max-y", maxY);
            return m;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Ellipsoid / sphere
    // ------------------------------------------------------------------------------------------------
    record Ellipsoid(int cx, int cy, int cz, int rx, int ry, int rz) implements Shape {
        @Override public boolean contains(int x, int y, int z) {
            double dx = (x - cx) / (rx + 0.5), dy = (y - cy) / (ry + 0.5), dz = (z - cz) / (rz + 0.5);
            return dx * dx + dy * dy + dz * dz <= 1.0;
        }
        @Override public int minX() { return cx - rx; }
        @Override public int maxX() { return cx + rx; }
        @Override public int minY() { return cy - ry; }
        @Override public int maxY() { return cy + ry; }
        @Override public int minZ() { return cz - rz; }
        @Override public int maxZ() { return cz + rz; }
        @Override public String type() { return "sphere"; }
        @Override public long volume() { return Math.max(1, Math.round(4.0 / 3.0 * Math.PI * (rx + 0.5) * (ry + 0.5) * (rz + 0.5))); }
        @Override public Shape translate(int dx, int dy, int dz) { return new Ellipsoid(cx + dx, cy + dy, cz + dz, rx, ry, rz); }
        @Override public Shape withHeight(int a, int b) {
            int ny = Math.max(0, (Math.max(a, b) - Math.min(a, b)) / 2);
            return new Ellipsoid(cx, (a + b) / 2, cz, rx, ny, rz);
        }
        @Override public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "sphere");
            m.put("center", vec(cx, cy, cz));
            m.put("radius", vec(rx, ry, rz));
            return m;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Global: contains every point of a world
    // ------------------------------------------------------------------------------------------------
    record Global() implements Shape {
        public static final Global INSTANCE = new Global();
        @Override public boolean contains(int x, int y, int z) { return true; }
        @Override public int minX() { return -30_000_000; }
        @Override public int minY() { return WORLD_MIN_Y; }
        @Override public int minZ() { return -30_000_000; }
        @Override public int maxX() { return 30_000_000; }
        @Override public int maxY() { return WORLD_MAX_Y; }
        @Override public int maxZ() { return 30_000_000; }
        @Override public String type() { return "global"; }
        @Override public long volume() { return Long.MAX_VALUE; }
        @Override public Shape translate(int dx, int dy, int dz) { return this; }
        @Override public Shape withHeight(int a, int b) { return this; }
        @Override public boolean coversChunk(int cx, int cz, int a, int b) { return true; }
        @Override public Map<String, Object> serialize() { Map<String, Object> m = new LinkedHashMap<>(); m.put("type", "global"); return m; }
    }

    // ------------------------------------------------------------------------------------------------
    // (De)serialization
    // ------------------------------------------------------------------------------------------------
    static Map<String, Object> vec(int x, int y, int z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x); m.put("y", y); m.put("z", z);
        return m;
    }

    @SuppressWarnings("unchecked")
    static int[] readVec(Object o) {
        if (o instanceof Map<?, ?> m) {
            return new int[]{num(((Map<String, Object>) m).get("x")), num(((Map<String, Object>) m).get("y")), num(((Map<String, Object>) m).get("z"))};
        }
        throw new IllegalArgumentException("Expected vector map but got " + o);
    }

    static int num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return (int) Math.floor(n.doubleValue());
        return (int) Math.floor(Double.parseDouble(o.toString()));
    }

    @SuppressWarnings("unchecked")
    static Shape deserialize(Map<String, Object> m) {
        String type = String.valueOf(m.getOrDefault("type", "cuboid")).toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "cuboid": {
                int[] a = readVec(m.get("min")), b = readVec(m.get("max"));
                return Cuboid.of(a[0], a[1], a[2], b[0], b[1], b[2]);
            }
            case "poly2d": case "polygon": case "poly": {
                List<int[]> pts = new ArrayList<>();
                for (Object p : (List<Object>) m.get("points")) {
                    Map<String, Object> pm = (Map<String, Object>) p;
                    pts.add(new int[]{num(pm.get("x")), num(pm.get("z"))});
                }
                return Polygon.of(pts, num(m.get("min-y")), num(m.get("max-y")));
            }
            case "cylinder": case "cyl": {
                int[] c = readVec(m.get("center"));
                return new Cylinder(c[0], c[2], num(m.get("radius-x")), num(m.get("radius-z")), num(m.get("min-y")), num(m.get("max-y")));
            }
            case "sphere": case "ellipsoid": {
                int[] c = readVec(m.get("center")), r = readVec(m.get("radius"));
                return new Ellipsoid(c[0], c[1], c[2], r[0], r[1], r[2]);
            }
            case "global":
                return Global.INSTANCE;
            default:
                throw new IllegalArgumentException("Unknown shape type '" + type + "'");
        }
    }

    static Shape fromMap(Map<String, Object> m) { return deserialize(m); }
}
