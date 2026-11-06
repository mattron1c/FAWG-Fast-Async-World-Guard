package dev.mattron1c.fawg.core.index;

import dev.mattron1c.fawg.core.region.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * The regions that contain a point, sorted by priority (highest first), plus the world's global region.
 * Instances are immutable and can be shared/cached freely.
 */
public final class ApplicableSet implements Iterable<Region> {
    private static final Region[] NONE = new Region[0];
    private final Region[] regions;
    private final Region global;

    public ApplicableSet(Region[] sortedRegions, Region global) {
        this.regions = sortedRegions == null ? NONE : sortedRegions;
        this.global = global;
    }

    public static ApplicableSet empty(Region global) { return new ApplicableSet(NONE, global); }

    public Region[] regions() { return regions; }
    public Region global() { return global; }
    public int size() { return regions.length; }
    public boolean isEmpty() { return regions.length == 0; }

    public Region highest() { return regions.length == 0 ? null : regions[0]; }

    public boolean contains(Region r) {
        for (Region x : regions) if (x == r) return true;
        return false;
    }

    public boolean containsId(String id) {
        for (Region x : regions) if (x.id().equals(id)) return true;
        return false;
    }

    public List<String> ids() {
        List<String> l = new ArrayList<>(regions.length);
        for (Region r : regions) l.add(r.id());
        return l;
    }

    /** True when both sets reference exactly the same regions (cheap identity compare). */
    public boolean sameRegions(ApplicableSet o) {
        if (o == this) return true;
        if (o == null || o.regions.length != regions.length) return false;
        for (int i = 0; i < regions.length; i++) if (regions[i] != o.regions[i]) return false;
        return true;
    }

    @Override public Iterator<Region> iterator() { return Arrays.asList(regions).iterator(); }

    @Override public String toString() { return ids().toString(); }
}
