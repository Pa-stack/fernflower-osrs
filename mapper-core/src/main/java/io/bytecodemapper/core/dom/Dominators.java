// >>> AUTOGEN: BYTECODEMAPPER Dominators BEGIN
package io.bytecodemapper.core.dom;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/** Cooper–Harvey–Kennedy dominators over ReducedCFG (deterministic). */
public final class Dominators {

    private final ReducedCFG cfg;
    private final int[] nodes;        // ascending block ids
    private final Int2IntMap indexOf; // block id -> index in nodes
    private final int[] rpo;          // reverse postorder indices (over nodes)
    private final int[] idom;         // immediate dominator in block id space
    private final int[] depth;        // depth in dominator tree
    private final Int2ObjectMap<IntArrayList> children = new Int2ObjectOpenHashMap<>();

    private Dominators(ReducedCFG cfg, int[] nodes, Int2IntMap indexOf, int[] rpo, int[] idom, int[] depth) {
        this.cfg = cfg;
        this.nodes = nodes;
        this.indexOf = indexOf;
        this.rpo = rpo;
        this.idom = idom;
        this.depth = depth;
        buildChildren();
    }

    public static Dominators compute(ReducedCFG cfg) {
        // Nodes in ascending id for determinism
        int[] nodes = cfg.allBlockIds();
        if (nodes.length == 0) {
            return new Dominators(cfg, new int[0], new Int2IntOpenHashMap(), new int[0], new int[0], new int[0]);
        }

        // Map block id -> index
        Int2IntOpenHashMap indexOf = new Int2IntOpenHashMap();
        for (int i=0;i<nodes.length;i++) indexOf.put(nodes[i], i);

        // Build succs/preds as arrays of indices into nodes[]
        int n = nodes.length;
        int[][] succIdx = new int[n][];
        int[][] predIdx = new int[n][];
        for (int i=0;i<n;i++) {
            Block b = cfg.block(nodes[i]);
            int[] sIds = b.succs();
            int[] pIds = b.preds();
            succIdx[i] = mapIdsToIdx(sIds, indexOf);
            predIdx[i] = mapIdsToIdx(pIds, indexOf);
        }

        // Compute RPO starting from entry (smallest id)
        int entry = 0; // index in nodes[]
        for (int i=1;i<n;i++) if (nodes[i] < nodes[entry]) entry = i;
        int[] rpo = computeRpo(entry, succIdx, n);

        // CHK initialization
        int[] idom = new int[n];
        Arrays.fill(idom, -1);
        idom[entry] = entry;

        boolean changed;
        do {
            changed = false;
            // Process nodes in RPO order skipping entry
            for (int i = 0; i < n; i++) {
                int b = rpo[i];
                if (b == entry) continue;

                // pick first processed predecessor as starting point
                int newIDom = -1;
                for (int p : predIdx[b]) {
                    if (idom[p] != -1) { newIDom = p; break; }
                }
                if (newIDom == -1) continue; // unreachable (shouldn't happen)
                // For all other predecessors, intersect
                for (int p : predIdx[b]) {
                    if (p == newIDom) continue;
                    if (idom[p] != -1) {
                        newIDom = intersect(idom, rpo, p, newIDom);
                    }
                }
                if (idom[b] != newIDom) {
                    idom[b] = newIDom;
                    changed = true;
                }
            }
        } while (changed);

        // Convert idom from index space to block id space
        int[] idomIds = new int[n];
        int[] depth = new int[n];
        for (int i=0;i<n;i++) {
            idomIds[i] = (idom[i] == -1) ? -1 : nodes[idom[i]];
        }
        // Compute depths
        for (int i=0;i<n;i++) {
            int d = 0, cur = i;
            while (cur != idom[cur]) {
                d++;
                cur = idom[cur];
                if (cur < 0) break;
            }
            depth[i] = d;
        }

        return new Dominators(cfg, nodes, indexOf, rpo, idomIds, depth);
    }

    private static int[] mapIdsToIdx(int[] ids, Int2IntMap indexOf) {
        int[] out = new int[ids.length];
        for (int i=0;i<ids.length;i++) out[i] = indexOf.get(ids[i]);
        return out;
    }

    private static int[] computeRpo(int entry, int[][] succIdx, int n) {
        boolean[] seen = new boolean[n];
        IntArrayList order = new IntArrayList();
        Deque<Integer> stack = new ArrayDeque<Integer>();
        Deque<Integer> iters = new ArrayDeque<Integer>();
        stack.push(entry); iters.push(0);
        while (!stack.isEmpty()) {
            int u = stack.peek();
            int it = iters.pop();
            if (!seen[u]) { seen[u] = true; it = 0; }
            if (it < succIdx[u].length) {
                int v = succIdx[u][it];
                iters.push(it+1);
                if (!seen[v]) { stack.push(v); iters.push(0); }
            } else {
                order.add(u);
                stack.pop();
            }
        }
        // If disconnected blocks exist, append them deterministically
        for (int i=0;i<n;i++) if (!seen[i]) order.add(i);
        int[] rpo = order.toIntArray();
        // rpo is already reverse postorder due to add on pop
        return rpo;
    }

    /** Intersect function over dominator tree (by RPO numbers). */
    private static int intersect(int[] idom, int[] rpo, int f1, int f2) {
        int finger1 = f1, finger2 = f2;
        while (finger1 != finger2) {
            while (rpoIndex(rpo, finger1) < rpoIndex(rpo, finger2)) {
                finger1 = idom[finger1];
            }
            while (rpoIndex(rpo, finger2) < rpoIndex(rpo, finger1)) {
                finger2 = idom[finger2];
            }
        }
        return finger1;
    }

    private static int rpoIndex(int[] rpo, int idx) {
        // rpo is a permutation; get position by linear scan (n is small in tests, acceptable)
        for (int i=0;i<rpo.length;i++) if (rpo[i]==idx) return i;
        return -1;
    }

    private void buildChildren() {
        for (int id : nodes) children.put(id, new IntArrayList());
        for (int i=0;i<nodes.length;i++) {
            int me = nodes[i];
            int id = idomIndexOf(me);
            if (id == -1) continue;
            int parent = idom[i];
            if (parent == -1 || parent == me) continue;
            children.get(parent).add(me);
        }
        for (IntArrayList ch : children.values()) {
            int[] a = ch.toIntArray();
            Arrays.sort(a);
            ch.clear();
            ch.addElements(0, a);
        }
    }

    private int idomIndexOf(int blockId) {
        for (int i=0;i<nodes.length;i++) if (nodes[i]==blockId) return i;
        return -1;
    }

    // ---- Public API ----

    public int idom(int blockId) {
        int i = idomIndexOf(blockId);
        if (i < 0) return -1;
        return idom[i];
    }

    public int domDepth(int blockId) {
        int i = idomIndexOf(blockId);
        if (i < 0) return -1;
        return depth[i];
    }

    public int[] children(int blockId) {
        IntArrayList ch = children.get(blockId);
        return ch == null ? new int[0] : ch.toIntArray();
    }

    public boolean dominates(int v, int u) {
        // does block v dominate block u?
        if (v == u) return true;
        int cur = idom(u);
        while (cur != -1 && cur != u) {
            if (cur == v) return true;
            cur = idom(cur);
        }
        return false;
    }

    public int[] nodes() { return Arrays.copyOf(nodes, nodes.length); }
    public int[] rpo() { return Arrays.copyOf(rpo, rpo.length); }
}
// <<< AUTOGEN: BYTECODEMAPPER Dominators END
