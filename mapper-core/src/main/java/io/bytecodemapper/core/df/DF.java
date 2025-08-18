// >>> AUTOGEN: BYTECODEMAPPER DF BEGIN
package io.bytecodemapper.core.df;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.dom.Dominators;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cytron DF and transitive DF (IDF/TDF) with sorted int[] sets. */
public final class DF {
    private DF() {}

    /** Compute DF(b) for each block b, returning sorted arrays. */
    public static Map<Integer, int[]> compute(ReducedCFG cfg, Dominators dom) {
        int[] nodes = cfg.allBlockIds();
        Map<Integer, int[]> out = new LinkedHashMap<Integer, int[]>();
        if (nodes.length == 0) return out;

        // Work map: blockId -> set of blockIds in its DF
        Int2ObjectOpenHashMap<IntOpenHashSet> df = new Int2ObjectOpenHashMap<IntOpenHashSet>();
        for (int b : nodes) df.put(b, new IntOpenHashSet());

    // Cytron: for each node n, walk each pred up to idom(n)
        for (int n : nodes) {
            Block bn = cfg.block(n);
            int[] preds = bn.preds();

            int idomOfN = dom.idom(n);
            for (int p : preds) {
                int runner = p;
                while (runner != -1 && runner != idomOfN) {
                    df.get(runner).add(n);
                    runner = dom.idom(runner);
                }
            }
        }

        // Deterministic output: ascending keys, sorted arrays
        Arrays.sort(nodes);
        for (int b : nodes) out.put(b, toSortedArray(df.get(b)));
        return out;
    }

    /** Iterate DF to fixpoint to produce TDF (transitive DF). */
    public static Map<Integer, int[]> iterateToFixpoint(Map<Integer, int[]> df) {
        // Copy into mutable sets
        Map<Integer, IntOpenHashSet> work = new LinkedHashMap<Integer, IntOpenHashSet>();
        int[] keys = new int[df.size()];
        int ki = 0;
        for (Map.Entry<Integer, int[]> e : df.entrySet()) {
            keys[ki++] = e.getKey().intValue();
            work.put(e.getKey(), new IntOpenHashSet(e.getValue()));
        }
        Arrays.sort(keys);

        boolean changed;
        do {
            changed = false;
            for (int b : keys) {
                IntOpenHashSet set = work.get(b);
                // For each y in current DF(b), union DF(y)
                IntOpenHashSet add = new IntOpenHashSet();
                for (int y : set) {
                    int[] dfy = df.get(y);
                    if (dfy == null) continue;
                    for (int z : dfy) add.add(z);
                }
                int before = set.size();
                set.addAll(add);
                if (set.size() != before) changed = true;
            }
        } while (changed);

        Map<Integer, int[]> out = new LinkedHashMap<Integer, int[]>();
        for (int b : keys) out.put(b, toSortedArray(work.get(b)));
        return out;
    }

    private static int[] toSortedArray(IntSet s) {
        int[] a = s == null ? new int[0] : s.toIntArray();
        Arrays.sort(a);
        return a;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER DF END
