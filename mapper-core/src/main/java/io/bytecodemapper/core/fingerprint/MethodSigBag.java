// >>> AUTOGEN: BYTECODEMAPPER core MethodSigBag BEGIN
package io.bytecodemapper.core.fingerprint;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;

/** Multiset of WL method signatures (64-bit) with helpers to get top-N, deterministic. */
public final class MethodSigBag {
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    public void add(long sig) {
        counts.addTo(sig, 1);
    }

    public int uniqueCount() { return counts.size(); }

    /** Returns up to N entries sorted by (count desc, signature asc) for determinism. */
    public Entry[] topN(int n) {
        ObjectArrayList<Entry> list = new ObjectArrayList<Entry>(counts.size());
        for (Long2IntMap.Entry e : counts.long2IntEntrySet()) {
            list.add(new Entry(e.getLongKey(), e.getIntValue()));
        }
        list.sort(new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                int c = Integer.compare(b.count, a.count);
                return c != 0 ? c : Long.compare(a.sig, b.sig);
            }
        });
        int m = Math.min(n, list.size());
        Entry[] out = new Entry[m];
        for (int i=0;i<m;i++) out[i] = list.get(i);
        return out;
    }

    /** Returns all signatures in ascending order (deterministic) */
    public long[] allKeysSorted() {
        LongArrayList keys = new LongArrayList(counts.keySet());
        keys.sort(null);
        return keys.toLongArray();
    }

    public int get(long sig) { return counts.getOrDefault(sig, 0); }

    public static final class Entry {
        public final long sig;
        public final int count;
        public Entry(long sig, int count) { this.sig = sig; this.count = count; }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER core MethodSigBag END
