package dev.mattron1c.fawg.core.index;

import dev.mattron1c.fawg.core.region.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Immutable chunk-bucketed spatial index.
 *
 * Every region is registered in the 16x16 chunk columns it touches. A lookup is a single hash probe plus a
 * scan of the (usually 0–3) regions of that column. Columns that no region touches resolve to a shared empty
 * result without allocating. Columns that are fully covered by their regions return a pre-built shared
 * array, so the most common case (standing inside a big claim / spawn) does no containment math at all.
 *
 * Giant regions (more than {@code largeThreshold} chunks) are kept in a separate small list and tested by
 * bounds, preventing index blow-up for world-sized regions.
 */
public final class RegionIndex {
    private static final Region[] NONE = new Region[0];

    private static final class Bucket {
        final Region[] regions;       // sorted by priority
        final Region[] fullCover;     // non-null when every region in the bucket covers the full column
        Bucket(Region[] regions, Region[] fullCover) { this.regions = regions; this.fullCover = fullCover; }
    }

    private final LongObjMap<Bucket> buckets;
    private final Region[] large;
    private final int regionCount;
    private final int worldMinY, worldMaxY;

    private RegionIndex(LongObjMap<Bucket> buckets, Region[] large, int count, int minY, int maxY) {
        this.buckets = buckets; this.large = large; this.regionCount = count; this.worldMinY = minY; this.worldMaxY = maxY;
    }

    public static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xffffffffL); }

    public static RegionIndex build(Collection<Region> regions, int largeThreshold, int worldMinY, int worldMaxY) {
        LongObjMap<List<Region>> tmp = new LongObjMap<>(Math.max(16, regions.size() * 4));
        List<Region> large = new ArrayList<>();
        int count = 0;
        for (Region r : regions) {
            if (r.isGlobal()) continue;
            count++;
            var s = r.shape();
            int cx0 = s.minX() >> 4, cx1 = s.maxX() >> 4, cz0 = s.minZ() >> 4, cz1 = s.maxZ() >> 4;
            long chunks = (long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
            if (chunks > largeThreshold) { large.add(r); continue; }
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    long k = key(cx, cz);
                    List<Region> l = tmp.get(k);
                    if (l == null) { l = new ArrayList<>(2); tmp.put(k, l); }
                    l.add(r);
                }
            }
        }
        LongObjMap<Bucket> out = new LongObjMap<>(Math.max(16, tmp.size()));
        tmp.forEach((k, l) -> {
            Region[] arr = l.toArray(NONE);
            Arrays.sort(arr);
            int cx = (int) (k >> 32), cz = (int) (long) k;
            boolean full = true;
            for (Region r : arr) {
                if (!r.excludes().isEmpty() || !r.shape().coversChunk(cx, cz, worldMinY, worldMaxY)) { full = false; break; }
            }
            out.put(k, new Bucket(arr, full ? arr : null));
        });
        Region[] lg = large.toArray(NONE);
        Arrays.sort(lg);
        return new RegionIndex(out, lg, count, worldMinY, worldMaxY);
    }

    public int regionCount() { return regionCount; }
    public int bucketCount() { return buckets.size(); }
    public int largeCount() { return large.length; }

    /** True if no region at all touches this chunk column (fast path for chunk-level decisions). */
    public boolean chunkEmpty(int cx, int cz) {
        if (large.length != 0) {
            int x0 = cx << 4, z0 = cz << 4;
            for (Region r : large) if (r.shape().intersectsChunk(cx, cz)) return false;
        }
        return buckets.get(key(cx, cz)) == null;
    }

    /** Regions touching the chunk column (may not contain every block of it). */
    public Region[] inChunk(int cx, int cz) {
        Bucket b = buckets.get(key(cx, cz));
        if (large.length == 0) return b == null ? NONE : b.regions;
        List<Region> l = new ArrayList<>();
        if (b != null) l.addAll(Arrays.asList(b.regions));
        for (Region r : large) if (r.shape().intersectsChunk(cx, cz)) l.add(r);
        Region[] a = l.toArray(NONE);
        Arrays.sort(a);
        return a;
    }

    /** Regions containing the block, highest priority first. Never returns null. */
    public Region[] query(int x, int y, int z) {
        Bucket b = buckets.get(key(x >> 4, z >> 4));
        Region[] base;
        if (b == null) {
            base = NONE;
        } else if (b.fullCover != null && y >= worldMinY && y <= worldMaxY) {
            base = b.fullCover;
        } else {
            base = filter(b.regions, x, y, z);
        }
        if (large.length == 0) return base;
        Region[] lg = filter(large, x, y, z);
        if (lg.length == 0) return base;
        if (base.length == 0) return lg;
        Region[] m = new Region[base.length + lg.length];
        System.arraycopy(base, 0, m, 0, base.length);
        System.arraycopy(lg, 0, m, base.length, lg.length);
        Arrays.sort(m);
        return m;
    }

    private static Region[] filter(Region[] src, int x, int y, int z) {
        int n = 0;
        // first pass counts so the result array is exact-sized without a scratch buffer
        for (Region r : src) if (r.contains(x, y, z)) n++;
        if (n == 0) return NONE;
        if (n == src.length) return src;
        Region[] out = new Region[n];
        int i = 0;
        for (Region r : src) if (r.contains(x, y, z)) out[i++] = r;
        return out;
    }

    /** All regions whose bounds intersect the box (used for overlap checks / claims / FAWE masks). */
    public List<Region> intersecting(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<Region> out = new ArrayList<>();
        java.util.Set<Region> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int cx0 = minX >> 4, cx1 = maxX >> 4, cz0 = minZ >> 4, cz1 = maxZ >> 4;
        long area = (long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        if (area > buckets.size()) {
            // box is bigger than the index: scan buckets instead
            buckets.forEach((k, b) -> { for (Region r : b.regions) if (seen.add(r) && boxHit(r, minX, minY, minZ, maxX, maxY, maxZ)) out.add(r); });
        } else {
            for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
                Bucket b = buckets.get(key(cx, cz));
                if (b != null) for (Region r : b.regions) if (seen.add(r) && boxHit(r, minX, minY, minZ, maxX, maxY, maxZ)) out.add(r);
            }
        }
        for (Region r : large) if (seen.add(r) && boxHit(r, minX, minY, minZ, maxX, maxY, maxZ)) out.add(r);
        return out;
    }

    private static boolean boxHit(Region r, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        var s = r.shape();
        return s.maxX() >= minX && s.minX() <= maxX && s.maxY() >= minY && s.minY() <= maxY && s.maxZ() >= minZ && s.minZ() <= maxZ;
    }
}
