package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;
import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;

import java.util.*;

public final class WLRefinement {
    private WLRefinement(){}
    // Compatibility default rounds for tests
    public static final int DEFAULT_K = 2;
    static final boolean DEBUG = Boolean.getBoolean("mapper.debug");

    // Lightweight thread dump for watchdog diagnostics (no external deps)
    public static void dumpAllStacks(String reason){
        try {
            System.out.println(reason);
            for (java.util.Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                Thread t = e.getKey();
                System.out.println("THREAD " + t.getName() + " id=" + t.getId() + " state=" + t.getState());
                StackTraceElement[] st = e.getValue();
                if (st != null) {
                    for (StackTraceElement ste : st) System.out.println("  at " + ste.toString());
                }
            }
            System.out.flush();
        } catch (Throwable ignore) { /* best-effort */ }
    }

    // Build a deterministic token bag: map token(long) -> count(int). We'll implement with TreeMap for ordering.
    public static SortedMap<Long,Integer> tokenBagFinal(ReducedCFG cfg, Dominators dom, Map<Integer,int[]> df, Map<Integer,int[]> tdf, int rounds){
        if(rounds<0) rounds=0; if(rounds>8) rounds=8; int[] nodes=cfg.allBlockIds(); Arrays.sort(nodes); SortedMap<Integer,Long> dfHash=new TreeMap<Integer,Long>(); SortedMap<Integer,Long> tdfHash=new TreeMap<Integer,Long>(); SortedMap<Integer,Integer> dfSize=new TreeMap<Integer,Integer>(); SortedMap<Integer,Integer> tdfSize=new TreeMap<Integer,Integer>();
        for(int b: nodes){int[] d=df.get(b); if(d==null) d=new int[0]; int[] t=tdf.get(b); if(t==null) t=new int[0]; dfSize.put(b,d.length); tdfSize.put(b,t.length); dfHash.put(b, hashSortedInts(d)); tdfHash.put(b, hashSortedInts(t)); }
        Map<Integer,int[]> preds=new HashMap<Integer,int[]>(), succs=new HashMap<Integer,int[]>(); for(int b: nodes){Block bb=cfg.block(b); preds.put(b, bb.preds()); succs.put(b, bb.succs()); }
        Set<Integer> loopHdr=new HashSet<Integer>(); for(int b: nodes){Block bb=cfg.block(b); for(int p: bb.preds()) if(dom.dominates(b,p)){ loopHdr.add(b); break; } }
        Map<Integer,Long> labels=new HashMap<Integer,Long>(); for(int b: nodes){Block bb=cfg.block(b); int[] p=bb.preds(), s=bb.succs(); long init = hashTuple(p.length, s.length, dom.domDepth(b), cfgDomChildrenCount(dom,b), dfSize.get(b), dfHash.get(b), tdfSize.get(b), tdfHash.get(b), loopHdr.contains(b)?1:0); labels.put(b, init);} for(int k=0;k<rounds;k++){ Map<Integer,Long> next=new HashMap<Integer,Long>(); for(int b: nodes){ long cur=labels.get(b); long ph=multisetHash(labels, preds.get(b)); long sh=multisetHash(labels, succs.get(b)); long lbl=hashConcat(cur,ph,sh, dfHash.get(b), tdfHash.get(b)); next.put(b,lbl);} labels=next; }
        SortedMap<Long,Integer> bag=new TreeMap<Long,Integer>(new Comparator<Long>(){public int compare(Long a,Long b){ return Long.compareUnsigned(a,b);} }); for(int b: nodes){ long t=labels.get(b); Integer c=bag.get(t); bag.put(t, c==null?1:c+1);} return bag;
    }
    public static SortedMap<Long,Integer> tokenBagFinalSorted(ReducedCFG cfg, Dominators dom, int rounds){
        // Diagnostic banner for WL phase
        if (DEBUG) {
            try {
                int[] nodes = cfg.allBlockIds();
                System.out.println("[WL] tokenBag.rounds=" + rounds + " nodes=" + (nodes==null?0:nodes.length));
                System.out.flush();
            } catch (Throwable ignore) { /* best-effort */ }
        }
        Map<Integer,int[]> df=DF.compute(cfg,dom); Map<Integer,int[]> tdf=DF.iterateToFixpoint(df); return tokenBagFinal(cfg,dom,df,tdf,rounds);
    }

    // Fastutil adapter for existing tests expecting Long2IntSortedMap
    public static it.unimi.dsi.fastutil.longs.Long2IntSortedMap tokenBagFinal(ReducedCFG cfg, Dominators dom, int rounds, boolean asFastutil){
        SortedMap<Long,Integer> bag = tokenBagFinalSorted(cfg, dom, rounds);
        it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap m = new it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap();
        for (Map.Entry<Long,Integer> e : bag.entrySet()) m.put(e.getKey().longValue(), e.getValue().intValue());
        return m;
    }

    /** Convenience overload used by CLI: compute DF/TDF internally and return fastutil map. */
    public static Long2IntSortedMap tokenBagFinal(ReducedCFG cfg, Dominators dom, int rounds){
        SortedMap<Long,Integer> bag = tokenBagFinalSorted(cfg, dom, rounds);
        Long2IntAVLTreeMap m = new Long2IntAVLTreeMap();
        for (Map.Entry<Long,Integer> e : bag.entrySet()) m.put(e.getKey().longValue(), e.getValue().intValue());
        return m;
    }

    // Build ReducedCFG/Dominators for method and serialize token bag to bytes (token:count per line, sorted)
    public static byte[] signature(org.objectweb.asm.tree.MethodNode mn, int rounds){ ReducedCFG cfg=ReducedCFG.build(mn); Dominators dom=Dominators.compute(cfg); SortedMap<Long,Integer> bag=tokenBagFinalSorted(cfg,dom,rounds); StringBuilder sb=new StringBuilder(); for(Map.Entry<Long,Integer> e: bag.entrySet()){ sb.append(Long.toUnsignedString(e.getKey())).append(':').append(e.getValue()).append('\n'); } return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public static String sha256Hex(byte[] bytes){ try{ java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256"); byte[] d=md.digest(bytes); StringBuilder hex=new StringBuilder(d.length*2); for(byte b: d) hex.append(String.format(java.util.Locale.ROOT, "%02x", b)); return hex.toString(); } catch(Exception e){ return Integer.toHexString(Arrays.hashCode(bytes)); } }

    // --- Compatibility surfaces for older tests ---
    public static Map<Integer, Long> refineLabels(List<Integer> nodeIds,
                                                  Map<Integer, List<Integer>> preds,
                                                  Map<Integer, List<Integer>> succs,
                                                  Map<Integer, Long> seedLabels,
                                                  int rounds) {
        // Initialize labels
        Map<Integer, Long> labels = new HashMap<Integer, Long>();
        for (Integer id : nodeIds) labels.put(id, seedLabels.containsKey(id) ? seedLabels.get(id) : 0L);
        int[] nodes = new int[nodeIds.size()];
        for (int i = 0; i < nodeIds.size(); i++) nodes[i] = nodeIds.get(i);
        Arrays.sort(nodes);
        for (int r = 0; r < rounds; r++) {
            Map<Integer, Long> next = new HashMap<Integer, Long>();
            for (int b : nodes) {
                long cur = labels.get(b);
                long ph = multisetHashList(labels, preds.get(b));
                long sh = multisetHashList(labels, succs.get(b));
                next.put(b, hashConcat(cur, ph, sh));
            }
            labels = next;
        }
        return labels;
    }

    public static final class MethodSignature {
        public final long hash; public final int blockCount; public final int loopCount;
        public MethodSignature(long hash, int blocks, int loops){ this.hash=hash; this.blockCount=blocks; this.loopCount=loops; }
    }

    /** Convenience: compute DF/TDF internally for CLI call sites. */
    public static MethodSignature computeSignature(ReducedCFG cfg, Dominators dom, int rounds) {
        Map<Integer,int[]> df=DF.compute(cfg,dom); Map<Integer,int[]> tdf=DF.iterateToFixpoint(df); return computeSignature(cfg, dom, df, tdf, rounds);
    }

    public static MethodSignature computeSignature(ReducedCFG cfg, Dominators dom,
                                                   Map<Integer,int[]> df, Map<Integer,int[]> tdf,
                                                   int rounds) {
        SortedMap<Long,Integer> bag = tokenBagFinal(cfg, dom, df, tdf, rounds);
        // Derive a 64-bit hash deterministically from the bag text
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long,Integer> e : bag.entrySet()) {
            sb.append(Long.toUnsignedString(e.getKey())).append(':').append(e.getValue()).append('\n');
        }
        long h = StableHash64.hashUtf8(sb.toString());
        // Loop headers count
        int loops = 0; for (ReducedCFG.Block b : cfg.blocks()) { for (int p : b.preds()) { if (dom.dominates(b.id, p)) { loops++; break; } } }
    int blocks = cfg.allBlockIds().length;
        return new MethodSignature(h, blocks, loops);
    }

    // helpers
    private static int cfgDomChildrenCount(Dominators dom,int b){ int[] ch=dom.children(b); return ch==null?0:ch.length; }
    private static long hashSortedInts(int[] a){ StringBuilder sb=new StringBuilder(); sb.append('I').append(':'); for(int v: a) sb.append(v).append(','); return StableHash64.hashUtf8(sb.toString()); }
    private static long hashTuple(int din,int dout,int ddepth,int dchild,int dfSize,long dfHash,int tdfSize,long tdfHash,int loop){ String s = "T|"+din+'|'+dout+'|'+ddepth+'|'+dchild+'|'+dfSize+'|'+Long.toUnsignedString(dfHash)+'|'+tdfSize+'|'+Long.toUnsignedString(tdfHash)+'|'+loop; return StableHash64.hashUtf8(s);}
    private static long multisetHash(Map<Integer,Long> labels,int[] neigh){ if(neigh==null||neigh.length==0) return StableHash64.hashUtf8("MS|"); long[] v=new long[neigh.length]; for(int i=0;i<neigh.length;i++) v[i]=labels.get(neigh[i]); Arrays.sort(v); StringBuilder sb=new StringBuilder(); sb.append("MS|"); for(long x: v) sb.append(Long.toUnsignedString(x)).append(','); return StableHash64.hashUtf8(sb.toString()); }
    private static long hashConcat(long... parts){ StringBuilder sb=new StringBuilder(); sb.append('C').append('|'); for(long p: parts) sb.append(Long.toUnsignedString(p)).append('|'); return StableHash64.hashUtf8(sb.toString()); }
    private static long multisetHashList(Map<Integer,Long> labels, List<Integer> neigh){ if(neigh==null||neigh.isEmpty()) return StableHash64.hashUtf8("MS|"); long[] v=new long[neigh.size()]; for(int i=0;i<neigh.size();i++) v[i]=labels.containsKey(neigh.get(i))? labels.get(neigh.get(i)) : 0L; Arrays.sort(v); StringBuilder sb=new StringBuilder(); sb.append("MS|"); for(long x: v) sb.append(Long.toUnsignedString(x)).append(','); return StableHash64.hashUtf8(sb.toString()); }
}

