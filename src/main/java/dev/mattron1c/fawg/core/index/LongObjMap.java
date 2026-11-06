package dev.mattron1c.fawg.core.index;

import java.util.function.BiConsumer;

/** Minimal open addressing long → Object map (no boxing, cache friendly). Not thread safe for writes. */
public final class LongObjMap<V> {
    private long[] keys;
    private Object[] vals;
    private int size, mask;

    public LongObjMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1;
        keys = new long[cap];
        vals = new Object[cap];
        mask = cap - 1;
    }

    private static int mix(long k) {
        k ^= k >>> 33; k *= 0xff51afd7ed558ccdL; k ^= k >>> 33; k *= 0xc4ceb9fe1a85ec53L; k ^= k >>> 33;
        return (int) k;
    }

    @SuppressWarnings("unchecked")
    public V get(long k) {
        int i = mix(k) & mask;
        Object[] v = vals; long[] ks = keys;
        while (v[i] != null) {
            if (ks[i] == k) return (V) v[i];
            i = (i + 1) & mask;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public V put(long k, V value) {
        if (value == null) throw new IllegalArgumentException("null values are not supported");
        if ((size + 1) * 2 > keys.length) rehash(keys.length << 1);
        int i = mix(k) & mask;
        while (vals[i] != null) {
            if (keys[i] == k) { V old = (V) vals[i]; vals[i] = value; return old; }
            i = (i + 1) & mask;
        }
        keys[i] = k; vals[i] = value; size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void rehash(int cap) {
        long[] ok = keys; Object[] ov = vals;
        keys = new long[cap]; vals = new Object[cap]; mask = cap - 1; size = 0;
        for (int i = 0; i < ok.length; i++) if (ov[i] != null) put(ok[i], (V) ov[i]);
    }

    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<Long, V> c) {
        for (int i = 0; i < keys.length; i++) if (vals[i] != null) c.accept(keys[i], (V) vals[i]);
    }

    public int size() { return size; }
}
