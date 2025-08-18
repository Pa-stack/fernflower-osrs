// >>> AUTOGEN: BYTECODEMAPPER WLRefinement BEGIN
package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/**
 * Deterministic Weisfeiler–Lehman refinement over ReducedCFG with DF/TDF features.
 *
 * Node initial label tuple:
 *   (degIn,degOut,domDepth,domChildren, |DF|, H(DF), |TDF|, H(TDF), loopHeader?1:0)
 *
 * Iteration k+1:
 *   L_{k+1}(n) = H( L_k(n)
 *                || multiset{ L_k(p) for p in preds(n) }
 *                || multiset{ L_k(s) for s in succs(n) }
 *                || Hset(DF(n)) || Hset(TDF(n)) )
 *
 * Method signature:
 *   H( multiset{ L_final(n) } || "B=" + blockCount || "L=" + loopCount )
 */
public final class WLRefinement {

    private WLRefinement(){}

    /** Container for method-level signature. */
    public static final class MethodSignature {
        public final long hash;
        public final int blockCount;
        public final int loopCount;

        public MethodSignature(long hash, int blockCount, int loopCount) {
            this.hash = hash;
            this.blockCount = blockCount;
            this.loopCount = loopCount;
        }

        @Override public String toString() {
            return "WLMethodSig{hash=" + Long.toUnsignedString(hash) + ", blocks=" + blockCount + ", loops=" + loopCount + "}";
        }
    }

    /** Convenience: compute DF and TDF inside, then signature. */
    public static MethodSignature computeSignature(ReducedCFG cfg, Dominators dom, int iterations) {
        Map<Integer,int[]> df  = DF.compute(cfg, dom);
        Map<Integer,int[]> tdf = DF.iterateToFixpoint(df);
        return computeSignature(cfg, dom, df, tdf, iterations);
    }

    /** Main API: deterministic WL with supplied DF and TDF maps (sorted int[]). */
    public static MethodSignature computeSignature(
            ReducedCFG cfg,
            Dominators dom,
            Map<Integer,int[]> df,
            Map<Integer,int[]> tdf,
            int iterations) {

        // Collect nodes deterministically
        int[] nodes = cfg.allBlockIds();
        Arrays.sort(nodes);

        // Compute loop headers: exists pred p of b with dominates(b, p)
        final boolean[] isLoopHeader = new boolean[nodes.length];
        final Int2BooleanOpenHashMap loopHeaderById = new Int2BooleanOpenHashMap();
        for (int i=0;i<nodes.length;i++) {
            int b = nodes[i];
            Block bb = cfg.block(b);
            boolean header = false;
            int[] predsArr = bb.preds();
            if (predsArr != null) {
                for (int p : predsArr) {
                    if (dom.dominates(b, p)) { header = true; break; }
                }
            }
            isLoopHeader[i] = header;
            loopHeaderById.put(b, header);
        }

        // Precompute DF/TDF hashes and sizes for each node
        final Int2LongOpenHashMap dfHash = new Int2LongOpenHashMap();
        final Int2LongOpenHashMap tdfHash = new Int2LongOpenHashMap();
        final Int2IntOpenHashMap dfSize = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap tdfSize = new Int2IntOpenHashMap();
        for (int b : nodes) {
            int[] d = df.get(b); if (d == null) d = new int[0];
            int[] t = tdf.get(b); if (t == null) t = new int[0];
            dfSize.put(b, d.length);
            tdfSize.put(b, t.length);
            dfHash.put(b, hashSortedInts(d));
            tdfHash.put(b, hashSortedInts(t));
        }

        // Build neighbor maps (preds/succs labels fetched dynamically each iter)
        final Int2ObjectOpenHashMap<int[]> preds = new Int2ObjectOpenHashMap<int[]>();
        final Int2ObjectOpenHashMap<int[]> succs = new Int2ObjectOpenHashMap<int[]>();
        for (int b : nodes) {
            Block bb = cfg.block(b);
            int[] p = bb.preds();
            int[] s = bb.succs();
            preds.put(b, p == null ? new int[0] : Arrays.copyOf(p, p.length));
            succs.put(b, s == null ? new int[0] : Arrays.copyOf(s, s.length));
        }

        // Initial labels
        Int2LongOpenHashMap labels = new Int2LongOpenHashMap();
        for (int b : nodes) {
            Block bb = cfg.block(b);
            int[] p = bb.preds();
            int[] s = bb.succs();
            long init = hashTuple(
                    p == null ? 0 : p.length,
                    s == null ? 0 : s.length,
                    dom.domDepth(b),
                    cfgDomChildrenCount(dom, b),
                    dfSize.get(b),
                    dfHash.get(b),
                    tdfSize.get(b),
                    tdfHash.get(b),
                    loopHeaderById.get(b) ? 1 : 0
            );
            labels.put(b, init);
        }

        // WL iterations
        for (int k = 0; k < Math.max(0, iterations); k++) {
            Int2LongOpenHashMap next = new Int2LongOpenHashMap(labels.size());
            for (int b : nodes) {
                long cur = labels.get(b);
                long predsHash = multisetHash(labels, preds.get(b));
                long succsHash = multisetHash(labels, succs.get(b));
                long lbl = hashConcat(cur, predsHash, succsHash, dfHash.get(b), tdfHash.get(b));
                next.put(b, lbl);
            }
            labels = next;
        }

        // Method signature: multiset hash of final labels + (blockCount, loopCount)
    long multiset = multisetAll(labels, nodes);
        int blockCount = nodes.length;
        int loopCount = countTrue(isLoopHeader);
        long methodSig = hashFinal(multiset, blockCount, loopCount);

        return new MethodSignature(methodSig, blockCount, loopCount);
    }

    // ---- helpers ----

    private static int cfgDomChildrenCount(Dominators dom, int b) {
        int[] ch = dom.children(b);
        return ch == null ? 0 : ch.length;
    }

    private static int countTrue(boolean[] a) {
        int c=0; for (boolean v : a) if (v) c++; return c;
    }

    private static long hashSortedInts(int[] arr) {
        // Encode as "I:<x0>,<x1>,...,"
        StringBuilder sb = new StringBuilder();
        sb.append('I').append(':');
        for (int v : arr) { sb.append(v).append(','); }
        return StableHash64.hashUtf8(sb.toString());
    }

    private static long hashTuple(int degIn, int degOut, int domDepth, int domChildren,
                                  int dfSize, long dfHash, int tdfSize, long tdfHash, int loopHdr) {
        // Encode as pipe-separated tuple; stable textual encoding
        String s = "T|" + degIn + '|' + degOut + '|' + domDepth + '|' + domChildren
                + '|' + dfSize + '|' + Long.toUnsignedString(dfHash)
                + '|' + tdfSize + '|' + Long.toUnsignedString(tdfHash)
                + '|' + loopHdr;
        return StableHash64.hashUtf8(s);
    }

    private static long multisetHash(Int2LongOpenHashMap labels, int[] neighbors) {
        if (neighbors == null || neighbors.length == 0) return StableHash64.hashUtf8("MS|");
        long[] vals = new long[neighbors.length];
        for (int i=0;i<neighbors.length;i++) {
            vals[i] = labels.get(neighbors[i]);
        }
        Arrays.sort(vals); // sorted multiset
        StringBuilder sb = new StringBuilder();
        sb.append("MS").append('|');
        for (long v : vals) {
            sb.append(Long.toUnsignedString(v)).append(',');
        }
        return StableHash64.hashUtf8(sb.toString());
    }

    private static long hashConcat(long... parts) {
        // Concatenate unsigned longs in order with a delimiter and hash
        StringBuilder sb = new StringBuilder(64 * parts.length);
        sb.append('C').append('|');
        for (long v : parts) {
            sb.append(Long.toUnsignedString(v)).append('|');
        }
        return StableHash64.hashUtf8(sb.toString());
    }

    private static long multisetAll(Int2LongOpenHashMap labels, int[] nodeIdsInOrder) {
        long[] vals = new long[nodeIdsInOrder.length];
        for (int i=0;i<nodeIdsInOrder.length;i++) vals[i] = labels.get(nodeIdsInOrder[i]);
        Arrays.sort(vals);
        StringBuilder sb = new StringBuilder();
        sb.append("MF").append('|');
        for (long v : vals) sb.append(Long.toUnsignedString(v)).append(',');
        return StableHash64.hashUtf8(sb.toString());
    }

    private static long hashFinal(long multiset, int blockCount, int loopCount) {
        String s = "FINAL|" + Long.toUnsignedString(multiset) + "|B=" + blockCount + "|L=" + loopCount;
        return StableHash64.hashUtf8(s);
    }

    // ---- generic API for tests/backward-compat ----
    /**
     * Deterministic WL relabeling over an abstract graph, for testing convenience.
     * nodes: list of node ids, preds/succs: adjacency as lists of node ids, labels: initial labels.
     * Returns a fresh map of final labels after given iterations.
     */
    public static Map<Integer, Long> refineLabels(
            java.util.List<Integer> nodes,
            java.util.Map<Integer, java.util.List<Integer>> preds,
            java.util.Map<Integer, java.util.List<Integer>> succs,
            java.util.Map<Integer, Long> labels,
            int iterations) {
        // Defensive copies and deterministic order
        int n = nodes == null ? 0 : nodes.size();
        int[] order = new int[n];
        for (int i=0;i<n;i++) order[i] = nodes.get(i).intValue();
        java.util.Arrays.sort(order);

        it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap cur = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
        for (int id : order) {
            Long v = labels.get(id);
            cur.put(id, v == null ? 0L : v.longValue());
        }

        for (int k=0;k<Math.max(0, iterations);k++) {
            it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap next = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap(cur.size());
            for (int id : order) {
                long base = cur.get(id);
                long predH = hashNeighborMultiset(cur, preds.get(id));
                long succH = hashNeighborMultiset(cur, succs.get(id));
                long lbl = hashConcat(base, predH, succH);
                next.put(id, lbl);
            }
            cur = next;
        }
        java.util.Map<Integer, Long> out = new java.util.LinkedHashMap<Integer, Long>(order.length);
        for (int id : order) out.put(id, cur.get(id));
        return out;
    }

    private static long hashNeighborMultiset(it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap labels,
                                             java.util.List<Integer> neigh) {
        if (neigh == null || neigh.isEmpty()) return StableHash64.hashUtf8("MS|");
        long[] vals = new long[neigh.size()];
        for (int i=0;i<neigh.size();i++) vals[i] = labels.get(neigh.get(i).intValue());
        java.util.Arrays.sort(vals);
        StringBuilder sb = new StringBuilder();
        sb.append("MSG").append('|');
        for (long v : vals) sb.append(Long.toUnsignedString(v)).append(',');
        return StableHash64.hashUtf8(sb.toString());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER WLRefinement END

