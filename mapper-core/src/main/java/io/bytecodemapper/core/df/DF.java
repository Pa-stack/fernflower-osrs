package io.bytecodemapper.core.df;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Dominance Frontier + Iterated DF (deterministic, Cytron-style). */
public final class DF {
    private DF(){}

    // >>> CODEGEN: DF DEBUG + LIMITS BEGIN
    private static final boolean DEBUG = Boolean.getBoolean("mapper.debug");
    private static int maxSteps(int n){
        try { return Math.max(8, Integer.getInteger("mapper.df.max.steps", n * 8)); }
        catch (SecurityException e){ return Math.max(8, n * 8); }
    }
    private static void dbg(String s){ if (DEBUG) System.out.println(s); }
    // <<< CODEGEN: DF DEBUG + LIMITS END

    /** Compute the (local) dominance frontier DF(b) for each block b. */
    public static Map<Integer,int[]> compute(ReducedCFG cfg, Dominators dom){
        // >>> CODEGEN: DF Cytron runner walk (deterministic) BEGIN
        final int[] nodes = cfg.allBlockIds();
        Arrays.sort(nodes); // deterministic order
        final int n = nodes.length;
        final Map<Integer,Integer> idxOf = new HashMap<Integer,Integer>();
        for (int i = 0; i < n; i++) idxOf.put(nodes[i], Integer.valueOf(i));

        // succ in index space; also map blockId->index helpers
        final int[][] succ = new int[n][];
        for (int i = 0; i < n; i++) {
            final io.bytecodemapper.core.cfg.ReducedCFG.Block b = cfg.block(nodes[i]);
            final int[] sIds = b.succs();
            final int[] sIdx = new int[sIds.length];
            for (int k = 0; k < sIds.length; k++) {
                Integer at = idxOf.get(sIds[k]);
                sIdx[k] = (at == null ? -1 : at.intValue());
            }
            succ[i] = sIdx;
        }

        // idom[] in index space using Dominators API (id of idom -> index)
        final int[] idomIdx = new int[n];
        Arrays.fill(idomIdx, -1);
        for (int i = 0; i < n; i++) {
            final int bId = nodes[i];
            final int iDomId = dom.idom(bId); // entry -> itself; others -> idom id
            final Integer j = idxOf.get(iDomId);
            idomIdx[i] = (j == null ? -1 : j.intValue());
        }

        // DF buckets
        @SuppressWarnings("unchecked")
        final ArrayList<Integer>[] dfTmp = new ArrayList[n];
        for (int i = 0; i < n; i++) dfTmp[i] = new ArrayList<Integer>(2);

        final int CAP = maxSteps(n);
        for (int b = 0; b < n; b++) {
            final int[] outs = succ[b];
            for (int s : outs) {
                if (s < 0) continue; // unmapped/disconnected
                final int stop = idomIdx[s];
                int r = b, steps = 0;
                // Classic Cytron runner: add s to DF[r] until r meets idom[s]
                while (r != stop && steps++ < CAP) {
                    // record in DF(r) before advancing
                    dfTmp[r].add(Integer.valueOf(s));
                    // advance; guard against broken chains
                    final int next = idomIdx[r];
                    if (next == -1 || next == r) break;
                    r = next;
                }
                if (steps >= CAP) dbg("[DF] runner capped: b=" + nodes[b] + " s=" + nodes[s] + " cap=" + CAP);
            }
        }

        // Dedup & sort sets (as block ids), then return Map<blockId,int[]>
        final Map<Integer,int[]> out = new LinkedHashMap<Integer,int[]>();
        for (int i = 0; i < n; i++) {
            final ArrayList<Integer> bag = dfTmp[i];
            if (!bag.isEmpty()) {
                // stable dedup on indices
                Collections.sort(bag);
                int last = -2;
                final ArrayList<Integer> uniq = new ArrayList<Integer>(bag.size());
                for (int v : bag) {
                    if (v != last) { uniq.add(Integer.valueOf(v)); last = v; }
                }
                // map to block ids
                final int[] arr = new int[uniq.size()];
                for (int k = 0; k < arr.length; k++) arr[k] = nodes[uniq.get(k).intValue()];
                out.put(Integer.valueOf(nodes[i]), arr);
            } else {
                out.put(Integer.valueOf(nodes[i]), new int[0]);
            }
        }
        return out;
        // <<< CODEGEN: DF Cytron runner walk END
    }

    /** Iterated DF to fixpoint (IDF) with deterministic order. */
    public static Map<Integer,int[]> iterateToFixpoint(Map<Integer,int[]> df){
        // >>> CODEGEN: IDF fixpoint (monotone union, deterministic) BEGIN
        final ArrayList<Integer> keys = new ArrayList<Integer>(df.keySet());
        Collections.sort(keys);
        final Map<Integer,SortedSet<Integer>> cur = new LinkedHashMap<Integer,SortedSet<Integer>>();
        for (int k : keys) {
            final SortedSet<Integer> s = new TreeSet<Integer>();
            final int[] vs = df.get(k);
            if (vs != null) for (int v : vs) s.add(Integer.valueOf(v));
            cur.put(Integer.valueOf(k), s);
        }
        boolean changed;
        int guard = 0, CAP = Math.max(4, keys.size() * 4);
        do {
            changed = false;
            for (int k : keys) {
                final SortedSet<Integer> acc = cur.get(k);
                final int before = acc.size();
                // union DF of each member currently in IDF(k)
                final Integer[] snap = acc.toArray(new Integer[0]);
                for (int v : snap) {
                    final int[] add = df.getOrDefault(v, new int[0]);
                    for (int u : add) acc.add(Integer.valueOf(u));
                }
                if (acc.size() != before) changed = true;
            }
            guard++;
        } while (changed && guard < CAP);
        if (guard >= CAP) dbg("[DF] IDF fixpoint capped at " + CAP);
        final Map<Integer,int[]> out = new LinkedHashMap<Integer,int[]>();
        for (int k : keys) {
            final SortedSet<Integer> s = cur.get(k);
            final int[] arr = new int[s.size()];
            int i = 0; for (int v : s) arr[i++] = v;
            out.put(Integer.valueOf(k), arr);
        }
        return out;
        // <<< CODEGEN: IDF fixpoint END
    }

    // >>> CODEGEN: DF TEST HOOK BEGIN
    // Package-private test hook to drive DF with a synthetic idom function.
    // Lets tests simulate idom self-loops / -1 chains without touching Dominators.
    static Map<Integer, int[]> computeWithIdomFunc(ReducedCFG cfg, IntUnaryOperator idomFn) {
        int[] nodes = cfg.allBlockIds();
        Map<Integer, int[]> out = new LinkedHashMap<Integer, int[]>();
        if (nodes.length == 0) return out;

        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntOpenHashSet> df =
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntOpenHashSet>();
        for (int b : nodes) df.put(b, new it.unimi.dsi.fastutil.ints.IntOpenHashSet());

        for (int n : nodes) {
            int idomOfN = idomFn.applyAsInt(n);
            int[] preds = cfg.block(n).preds();
            for (int p : preds) {
                int runner = p;
                int steps = 0; final int MAX_STEPS = nodes.length + 4;
                while (runner != -1 && runner != idomOfN) {
                    df.get(runner).add(n);
                    int next = idomFn.applyAsInt(runner);
                    if (next == runner || next == -1) {
                        if (DEBUG) System.out.println("[DF.test] break runner=" + runner + " idom(n)=" + idomOfN + " next=" + next);
                        break;
                    }
                    runner = next;
                    if (++steps > MAX_STEPS) {
                        if (DEBUG) System.out.println("[DF.test] guard trip n=" + n + " p=" + p);
                        break;
                    }
                }
            }
        }
        Arrays.sort(nodes);
        for (int b : nodes) out.put(b, toSorted(workToSet(df.get(b))));
        return out;
    }

    private static Set<Integer> workToSet(it.unimi.dsi.fastutil.ints.IntOpenHashSet s) {
        Set<Integer> out = new HashSet<Integer>();
        for (it.unimi.dsi.fastutil.ints.IntIterator it = s.iterator(); it.hasNext(); ) out.add(Integer.valueOf(it.nextInt()));
        return out;
    }
    // <<< CODEGEN: DF TEST HOOK END

    private static int[] toSorted(Set<Integer> s){ if(s==null||s.isEmpty()) return new int[0]; int[] a=new int[s.size()]; int i=0; for(Integer v: s) a[i++]=v.intValue(); Arrays.sort(a); return a; }
}
