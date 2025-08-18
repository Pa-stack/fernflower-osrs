// >>> AUTOGEN: BYTECODEMAPPER Dominators BEGIN
package io.bytecodemapper.core.dom;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/** Cooper–Harvey–Kennedy dominators over ReducedCFG (deterministic, cycle-safe). */
public final class Dominators {

    private final ReducedCFG cfg;

    // Node identity
    private final int[] nodes;              // nodes[i] = blockId
    private final Int2IntMap indexOf;       // blockId -> i

    // Orderings
    private final int[] rpo;                // list of node indices in reverse postorder

    // Dominator info
    private final int[] idomIndex;          // idom in INDEX space: idomIndex[i] = parent index; entry points to itself
    private final int[] idomIds;            // idom in BLOCK-ID space: idomIds[i] = nodes[idomIndex[i]]
    private final int[] depth;              // depth in dominator tree (entry = 0)

    private final Int2ObjectMap<IntArrayList> children = new Int2ObjectOpenHashMap<>();

    private Dominators(ReducedCFG cfg,
                       int[] nodes,
                       Int2IntMap indexOf,
                       int[] rpo,
                       int[] idomIndex,
                       int[] idomIds,
                       int[] depth) {
        this.cfg = cfg;
        this.nodes = nodes;
        this.indexOf = indexOf;
        this.rpo = rpo;
        this.idomIndex = idomIndex;
        this.idomIds = idomIds;
        this.depth = depth;
        buildChildren();
    }

    public static Dominators compute(ReducedCFG cfg) {
        // Nodes in ascending id for determinism
        final int[] nodes = cfg.allBlockIds();
        final int n = nodes.length;
        if (n == 0) {
        return new Dominators(cfg, new int[0], new Int2IntOpenHashMap(), new int[0],
            new int[0], new int[0], new int[0]);
        }

        // Map: blockId -> index
        final Int2IntOpenHashMap indexOf = new Int2IntOpenHashMap();
        indexOf.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) indexOf.put(nodes[i], i);

        // Build succs/preds as arrays of node indices
        final int[][] succIdx = new int[n][];
        final int[][] predIdx = new int[n][];
        for (int i = 0; i < n; i++) {
            Block b = cfg.block(nodes[i]);
            succIdx[i] = mapIdsToIdx(b.succs(), indexOf);
            predIdx[i] = mapIdsToIdx(b.preds(), indexOf);
        }

        // Entry = node with smallest blockId
        int entry = 0;
        for (int i = 1; i < n; i++) if (nodes[i] < nodes[entry]) entry = i;

        // Compute RPO and position table
        final int[] rpo = computeRpo(entry, succIdx, n);

        // CHK dom computation in index space
        final int[] idom = new int[n];
        Arrays.fill(idom, -1);
        idom[entry] = entry;

        boolean changed;
        do {
            changed = false;
            // process in RPO order, skip entry
            for (int i = 0; i < n; i++) {
                final int b = rpo[i];
                if (b == entry) continue;
                // pick first processed predecessor as start
                int newIDom = -1;
                for (int p : predIdx[b]) {
                    if (idom[p] != -1) { newIDom = p; break; }
                }
                if (newIDom == -1) continue; // unreachable (shouldn't happen after dropUnreachable)
                // intersect with other processed predecessors
                for (int p : predIdx[b]) {
                    if (p == newIDom || idom[p] == -1) continue;
                    newIDom = intersect(idom, rpo, p, newIDom);
                }
                if (idom[b] != newIDom) {
                    idom[b] = newIDom;
                    changed = true;
                }
            }
        } while (changed);

        // Prepare outputs
        final int[] idomIndex = idom; // index-space idoms
        final int[] idomIds = new int[n]; // blockId-space idoms
        final int[] depth = new int[n];

        for (int i = 0; i < n; i++) {
            idomIds[i] = nodes[idomIndex[i]];
        }
        // Compute depths by walking index-space idom chain
        for (int i = 0; i < n; i++) {
            int d = 0, cur = i, guard = 0;
            while (idomIndex[cur] != cur && guard++ < n + 2) {
                d++;
                cur = idomIndex[cur];
            }
            depth[i] = d;
        }

    return new Dominators(cfg, nodes, indexOf, rpo, idomIndex, idomIds, depth);
    }

    private static int[] mapIdsToIdx(int[] ids, Int2IntMap indexOf) {
        int[] out = new int[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = indexOf.get(ids[i]);
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
                iters.push(it + 1);
                if (v >= 0 && v < n && !seen[v]) { stack.push(v); iters.push(0); }
            } else {
                order.add(u);
                stack.pop();
            }
        }
        // Append disconnected nodes deterministically (should not happen after dropUnreachable)
        for (int i = 0; i < n; i++) if (!seen[i]) order.add(i);
        return order.toIntArray(); // already reverse postorder
    }

    /** CHK intersect using RPO positions (computed from rpo permutation). */
    private static int intersect(int[] idomIndex, int[] rpo, int f1, int f2) {
        int a = f1, b = f2;
        while (a != b) {
            while (rpoPos(rpo, a) < rpoPos(rpo, b)) a = idomIndex[a];
            while (rpoPos(rpo, b) < rpoPos(rpo, a)) b = idomIndex[b];
        }
        return a;
    }

    private static int rpoPos(int[] rpo, int idx) {
        for (int i = 0; i < rpo.length; i++) if (rpo[i] == idx) return i;
        return -1;
    }

    private void buildChildren() {
        for (int id : nodes) children.put(id, new IntArrayList());
        for (int i = 0; i < nodes.length; i++) {
            final int meBlock = nodes[i];
            final int parentIdx = idomIndex[i];
            if (parentIdx == i) continue; // entry
            final int parentBlock = nodes[parentIdx];
            children.get(parentBlock).add(meBlock);
        }
        for (IntArrayList ch : children.values()) {
            int[] a = ch.toIntArray();
            Arrays.sort(a);
            ch.clear();
            ch.addElements(0, a);
        }
    }

    // ---- Public API ----

    public int idom(int blockId) {
        final int i = indexOf.get(blockId);
        if (i < 0) return -1;
        return idomIds[i];
    }

    public int domDepth(int blockId) {
        final int i = indexOf.get(blockId);
        if (i < 0) return -1;
        return depth[i];
    }

    public int[] children(int blockId) {
        IntArrayList ch = children.get(blockId);
        return ch == null ? new int[0] : ch.toIntArray();
    }

    /** Cycle-safe dominance query walking the index-space idom chain. */
    public boolean dominates(int vBlock, int uBlock) {
        if (vBlock == uBlock) return true;
        final int v = indexOf.get(vBlock);
        final int u = indexOf.get(uBlock);
        if (v < 0 || u < 0) return false;

        int cur = u;
        int guard = 0;
        while (idomIndex[cur] != cur && guard++ < nodes.length + 2) {
            cur = idomIndex[cur];
            if (cur == v) return true;
        }
        return false;
    }

    public int[] nodes() { return Arrays.copyOf(nodes, nodes.length); }
    public int[] rpo() { return Arrays.copyOf(rpo, rpo.length); }
}
// <<< AUTOGEN: BYTECODEMAPPER Dominators END
