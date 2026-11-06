package dev.mattron1c.fawg.core.index;

import dev.mattron1c.fawg.core.region.Region;
import dev.mattron1c.fawg.core.shape.Shape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All regions of one world. Mutations rebuild an immutable {@link RegionIndex} and publish it with a single
 * volatile write, so readers on any thread (main, FAWE workers, chat threads) never block or see a half state.
 */
public final class RegionWorld {
    private final String name;
    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private final Region global;
    private volatile RegionIndex index;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile int largeThreshold = 4096;
    private volatile int minY = -64, maxY = 319;
    private volatile long indexVersion;

    public RegionWorld(String name) {
        this.name = name;
        this.global = new Region(Region.GLOBAL_ID, name, Shape.Global.INSTANCE);
        this.global.priority(Integer.MIN_VALUE);
        this.index = RegionIndex.build(Collections.emptyList(), largeThreshold, minY, maxY);
    }

    public String name() { return name; }
    public Region global() { return global; }
    public RegionIndex index() { return index; }
    public long indexVersion() { return indexVersion; }

    public void heights(int minY, int maxY) { this.minY = minY; this.maxY = maxY; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public void largeThreshold(int t) { this.largeThreshold = t; }

    public Region get(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        if (Region.GLOBAL_ID.equals(k)) return global;
        return regions.get(k);
    }

    public boolean has(String id) { return get(id) != null; }

    public Collection<Region> regions() { return Collections.unmodifiableCollection(regions.values()); }

    public List<Region> regionsIncludingGlobal() {
        List<Region> l = new ArrayList<>(regions.size() + 1);
        l.add(global);
        l.addAll(regions.values());
        return l;
    }

    public int size() { return regions.size(); }

    public void add(Region r) {
        if (r.isGlobal()) throw new IllegalArgumentException("Use global() for the global region");
        regions.put(r.id(), r);
        changed();
    }

    /** Adds many regions with a single index rebuild (imports / loading). */
    public void addAll(Collection<Region> rs) {
        for (Region r : rs) if (!r.isGlobal()) regions.put(r.id(), r);
        changed();
    }

    /** Removes a region; children are re-parented to the removed region's parent. */
    public Region remove(String id, boolean removeChildren) {
        Region r = regions.remove(id.toLowerCase(Locale.ROOT));
        if (r == null) return null;
        for (Region c : new ArrayList<>(regions.values())) {
            if (c.parent() == r) {
                if (removeChildren) remove(c.id(), true);
                else c.parent(r.parent());
            }
        }
        changed();
        return r;
    }

    public void clear() { regions.clear(); changed(); }

    /** Rebuilds the index and marks the world for saving. */
    public void changed() {
        rebuild();
        dirty.set(true);
    }

    public synchronized void rebuild() {
        index = RegionIndex.build(regions.values(), largeThreshold, minY, maxY);
        indexVersion++;
    }

    public boolean dirty() { return dirty.get(); }
    public void markDirty() { dirty.set(true); }
    public boolean clearDirty() { return dirty.getAndSet(false); }

    /** Returns the applicable set at a block. */
    public ApplicableSet query(int x, int y, int z) {
        return new ApplicableSet(index.query(x, y, z), global);
    }

    public Region[] queryRaw(int x, int y, int z) { return index.query(x, y, z); }
}
