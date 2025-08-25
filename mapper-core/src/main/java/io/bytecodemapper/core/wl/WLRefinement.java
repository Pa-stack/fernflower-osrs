package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;

import java.util.*;

public final class WLRefinement {
    private WLRefinement(){}
    // Compatibility default rounds for tests
    public static final int DEFAULT_K = 2;
    static final boolean DEBUG = Boolean.getBoolean("mapper.debug");
    // (int prop helper unused after unification)
    // --- WL block cap property (unified path) ---
    private static final String PROP_MAX_BLOCKS = "mapper.wl.max.blocks";
    private static int maxBlocks() { try { return Integer.parseInt(System.getProperty(PROP_MAX_BLOCKS, "800")); } catch(Exception e){ return 800; } }

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
        // Delegate to unified fastutil path, then convert to signed-ordered TreeMap with unsigned comparator
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap fu = computeBagFastutil(cfg, dom, rounds);
        SortedMap<Long,Integer> bag = new TreeMap<Long,Integer>(new Comparator<Long>(){ public int compare(Long a, Long b){ return Long.compareUnsigned(a,b); } });
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : fu.long2IntEntrySet()) {
            bag.put(Long.valueOf(e.getLongKey()), Integer.valueOf(e.getIntValue()));
        }
        return bag;
    }

    // Fastutil adapter for existing tests expecting Long2IntSortedMap
    public static it.unimi.dsi.fastutil.longs.Long2IntSortedMap tokenBagFinal(ReducedCFG cfg, Dominators dom, int rounds, boolean asFastutil){
        return computeBagFastutil(cfg, dom, rounds);
    }

    /** Convenience overload used by CLI: compute DF/TDF internally and return fastutil map. */
    public static Long2IntSortedMap tokenBagFinal(ReducedCFG cfg, Dominators dom, int rounds){
        return computeBagFastutil(cfg, dom, rounds);
    }

    // Unified implementation: capped lite path or full DF/TDF path; returns fastutil map.
    private static it.unimi.dsi.fastutil.longs.Long2IntSortedMap computeBagFastutil(
            io.bytecodemapper.core.cfg.ReducedCFG cfg,
            io.bytecodemapper.core.dom.Dominators dom,
            int rounds) {
        final int n = cfg.allBlockIds().length;
        final boolean lite = n > maxBlocks();
        // >>> AUTOGEN: WL DEBUG BANNER (tokenBag) BEGIN
        if (DEBUG) {
            try {
                System.out.println("[WL] tokenBag.rounds=" + rounds + " nodes=" + n + " mode=" + (lite ? "lite" : "full"));
                System.out.flush();
            } catch (Throwable ignore) { /* best-effort */ }
        }
        // <<< AUTOGEN: WL DEBUG BANNER (tokenBag) END
        if (lite) {
            // tokenBagFinalLiteSorted: initial labels + preds/succs only, fixed 1 round
            java.util.SortedMap<Long,Integer> sm = tokenBagFinalLiteSorted(cfg, dom, 1);
            it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap m = new it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap();
            for (java.util.Map.Entry<Long,Integer> e : sm.entrySet()) m.put(e.getKey().longValue(), e.getValue().intValue());
            return m;
        } else {
            // Normal full path with DF/TDF
            java.util.Map<java.lang.Integer,int[]> df = io.bytecodemapper.core.df.DF.compute(cfg, dom);
            java.util.Map<java.lang.Integer,int[]> tdf = io.bytecodemapper.core.df.DF.iterateToFixpoint(df);
            java.util.SortedMap<Long,Integer> sm = tokenBagFinal(cfg, dom, df, tdf, rounds);
            it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap m = new it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap();
            for (java.util.Map.Entry<Long,Integer> e : sm.entrySet()) m.put(e.getKey().longValue(), e.getValue().intValue());
            return m;
        }
    }

    // Lite path: no DF/TDF. SortedMap variant for core use.
    public static SortedMap<Long,Integer> tokenBagFinalLiteSorted(ReducedCFG cfg, Dominators dom, int rounds){
        int[] nodes = cfg.allBlockIds();
        Arrays.sort(nodes);
        Map<Integer,int[]> preds=new HashMap<Integer,int[]>(), succs=new HashMap<Integer,int[]>();
        for(int b: nodes){ Block bb=cfg.block(b); preds.put(b, bb.preds()); succs.put(b, bb.succs()); }
        Set<Integer> loopHdr=new HashSet<Integer>(); for(int b: nodes){ Block bb=cfg.block(b); for(int p: bb.preds()) if(dom.dominates(b,p)){ loopHdr.add(b); break; } }
        // Initial labels: degIn, degOut, domDepth, domChildren, loopHeader (no DF/TDF)
        Map<Integer,Long> labels=new HashMap<Integer,Long>();
        for(int b: nodes){ Block bb=cfg.block(b); int[] p=bb.preds(), s=bb.succs();
            long init = StableHash64.hashUtf8("LITE|"+p.length+'|'+s.length+'|'+dom.domDepth(b)+'|'+cfgDomChildrenCount(dom,b)+'|'+(loopHdr.contains(b)?1:0));
            labels.put(b, init);
        }
        for(int k=0;k<Math.max(0, rounds);k++){
            Map<Integer,Long> next=new HashMap<Integer,Long>();
            for(int b: nodes){ long cur=labels.get(b); long ph=multisetHash(labels, preds.get(b)); long sh=multisetHash(labels, succs.get(b)); long lbl=hashConcat(cur,ph,sh); next.put(b,lbl);} labels=next;
        }
        SortedMap<Long,Integer> bag=new TreeMap<Long,Integer>(new Comparator<Long>(){public int compare(Long a,Long b){ return Long.compareUnsigned(a,b);} }); for(int b: nodes){ long t=labels.get(b); Integer c=bag.get(t); bag.put(t, c==null?1:c+1);} return bag;
    }

    // Build ReducedCFG/Dominators for method and serialize token bag to bytes (token:count per line, sorted)
    public static byte[] signature(org.objectweb.asm.tree.MethodNode mn, int rounds){ ReducedCFG cfg=ReducedCFG.build(mn); Dominators dom=Dominators.compute(cfg); SortedMap<Long,Integer> bag=tokenBagFinalSorted(cfg,dom,rounds); StringBuilder sb=new StringBuilder(); for(Map.Entry<Long,Integer> e: bag.entrySet()){ sb.append(Long.toUnsignedString(e.getKey())).append(':').append(e.getValue()).append('\n'); } return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public static String sha256Hex(byte[] bytes){ try{ java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256"); byte[] d=md.digest(bytes); StringBuilder hex=new StringBuilder(d.length*2); for(byte b: d) hex.append(String.format(java.util.Locale.ROOT, "%02x", b)); return hex.toString(); } catch(Exception e){ return Integer.toHexString(Arrays.hashCode(bytes)); } }

    // --- P1: stable bag encode/decode (package-private) ---
    static byte[] encodeBagFastutil(it.unimi.dsi.fastutil.longs.Long2IntSortedMap m) throws java.io.IOException {
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(bout))) {
            // Header v1: magic "WLBG" + version(1) + endian/reserved(0)
            out.write(new byte[]{'W','L','B','G'});
            out.writeByte(1);
            out.writeByte(0);
            for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : m.long2IntEntrySet()) {
                out.writeLong(e.getLongKey());
                out.writeInt(e.getIntValue());
            }
        }
        return bout.toByteArray();
    }

    static it.unimi.dsi.fastutil.longs.Long2IntSortedMap decodeBagFastutil(byte[] bytes) throws java.io.IOException {
        it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap m = new it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap();
        if (bytes == null || bytes.length == 0) return m;
        // Peek header
        boolean v1 = bytes.length >= 6 && bytes[0]=='W' && bytes[1]=='L' && bytes[2]=='B' && bytes[3]=='G';
        int offset = 0;
    if (v1) { offset = 6; /* version=bytes[4], reserved=bytes[5] */ }
    int payloadLen = bytes.length - offset;
    // Validate structure: payload must be sequence of (long,int) pairs → 12-byte stride
    if (payloadLen % 12 != 0) throw new java.io.EOFException("truncated or corrupt WL bag payload");
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.ByteArrayInputStream(bytes, offset, bytes.length - offset)))) {
            while (true) {
                long k;
                try { k = in.readLong(); } catch (java.io.EOFException eof) { break; }
                int v = in.readInt();
                m.put(k, v);
            }
        }
        return m;
    }

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

