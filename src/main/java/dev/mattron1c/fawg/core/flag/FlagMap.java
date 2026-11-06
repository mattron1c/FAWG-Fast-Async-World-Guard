package dev.mattron1c.fawg.core.flag;

import dev.mattron1c.fawg.core.region.RegionGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact copy-on-write flag storage. Values are kept in a sorted id array, so a region with 10 flags
 * costs ~150 bytes no matter how many of the 1000+ flags exist. Reads are lock free, allocation free and
 * branch-light (linear scan for small maps, binary search above 12 entries). Writes publish an immutable
 * snapshot through one volatile field, so FAWE worker threads always see a consistent state.
 */
public final class FlagMap {
    private static final class Data {
        static final Data EMPTY = new Data(new int[0], new Object[0], new RegionGroup[0]);
        final int[] ids;
        final Object[] vals;
        final RegionGroup[] groups;
        Data(int[] ids, Object[] vals, RegionGroup[] groups) { this.ids = ids; this.vals = vals; this.groups = groups; }

        int indexOf(int id) {
            int[] a = ids;
            int n = a.length;
            if (n <= 12) {
                for (int i = 0; i < n; i++) { int v = a[i]; if (v == id) return i; if (v > id) return -1; }
                return -1;
            }
            int r = Arrays.binarySearch(a, id);
            return r < 0 ? -1 : r;
        }
    }

    private volatile Data data = Data.EMPTY;
    /** Values of flags that are not registered (yet) — preserved on save so nothing is ever lost. */
    private final Map<String, Object> unknown = Collections.synchronizedMap(new LinkedHashMap<>());

    public Object getRaw(int id) {
        Data d = data;
        int i = d.indexOf(id);
        return i < 0 ? null : d.vals[i];
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Flag<T> f) { return (T) getRaw(f.id()); }

    public RegionGroup group(Flag<?> f) {
        Data d = data;
        int i = d.indexOf(f.id());
        return i < 0 ? null : d.groups[i];
    }

    public boolean has(Flag<?> f) { return data.indexOf(f.id()) >= 0; }

    public synchronized <T> void set(Flag<T> f, T value) {
        int id = f.id();
        if (id < 0) throw new IllegalStateException("Flag " + f.name() + " is not registered");
        Data d = data;
        int i = d.indexOf(id);
        if (value == null) {
            if (i < 0) return;
            int n = d.ids.length - 1;
            int[] ids = new int[n]; Object[] vals = new Object[n]; RegionGroup[] gs = new RegionGroup[n];
            System.arraycopy(d.ids, 0, ids, 0, i); System.arraycopy(d.ids, i + 1, ids, i, n - i);
            System.arraycopy(d.vals, 0, vals, 0, i); System.arraycopy(d.vals, i + 1, vals, i, n - i);
            System.arraycopy(d.groups, 0, gs, 0, i); System.arraycopy(d.groups, i + 1, gs, i, n - i);
            data = new Data(ids, vals, gs);
            return;
        }
        if (i >= 0) {
            Object[] vals = d.vals.clone();
            vals[i] = value;
            data = new Data(d.ids, vals, d.groups);
            return;
        }
        int n = d.ids.length;
        int pos = 0;
        while (pos < n && d.ids[pos] < id) pos++;
        int[] ids = new int[n + 1]; Object[] vals = new Object[n + 1]; RegionGroup[] gs = new RegionGroup[n + 1];
        System.arraycopy(d.ids, 0, ids, 0, pos); System.arraycopy(d.ids, pos, ids, pos + 1, n - pos);
        System.arraycopy(d.vals, 0, vals, 0, pos); System.arraycopy(d.vals, pos, vals, pos + 1, n - pos);
        System.arraycopy(d.groups, 0, gs, 0, pos); System.arraycopy(d.groups, pos, gs, pos + 1, n - pos);
        ids[pos] = id; vals[pos] = value;
        data = new Data(ids, vals, gs);
    }

    /** Sets the region group of an already set flag. Ignored when the flag has no value. */
    public synchronized void setGroup(Flag<?> f, RegionGroup group) {
        Data d = data;
        int i = d.indexOf(f.id());
        if (i < 0) return;
        RegionGroup[] gs = d.groups.clone();
        gs[i] = group;
        data = new Data(d.ids, d.vals, gs);
    }

    public int size() { return data.ids.length; }

    public boolean isEmpty() { return data.ids.length == 0 && unknown.isEmpty(); }

    public synchronized void clear() { data = Data.EMPTY; unknown.clear(); }

    public Map<String, Object> unknown() { return unknown; }

    /** Snapshot of all set flags (registered ones) in id order. */
    public Map<Flag<?>, Object> snapshot(FlagRegistry reg) {
        Data d = data;
        Map<Flag<?>, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < d.ids.length; i++) { Flag<?> f = reg.byId(d.ids[i]); if (f != null) out.put(f, d.vals[i]); }
        return out;
    }

    public synchronized void copyFrom(FlagMap o) {
        data = o.data;
        unknown.clear();
        unknown.putAll(o.unknown);
    }
}
