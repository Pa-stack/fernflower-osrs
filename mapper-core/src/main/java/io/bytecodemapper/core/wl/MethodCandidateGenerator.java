package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;

import java.util.*;

public final class MethodCandidateGenerator {
    private MethodCandidateGenerator(){}
    public static final int DEFAULT_K = 25; // required literal
    public static final class Candidate { public final String newId; public final double wlScore; public Candidate(String id,double s){this.newId=id;this.wlScore=s;} }
    static final boolean DEBUG = Boolean.getBoolean("mapper.debug");
    private static final int WL_CACHE_CAP = Integer.getInteger("mapper.wl.cache.size", 2048).intValue();
    // Use primitive fastutil map to cut boxing during cosine; deterministic order via AVL tree
    private static final LinkedHashMap<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap> WL_CACHE =
            new LinkedHashMap<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap>(16, 0.75f, true){
                @Override protected boolean removeEldestEntry(Map.Entry<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap> e){ return size()>WL_CACHE_CAP; }
            };
    // Parallel LRU for squared norms of WL bags
    private static final LinkedHashMap<String, Long> WL_NORM_CACHE =
            new LinkedHashMap<String, Long>(16, 0.75f, true){
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e){ return size()>WL_CACHE_CAP; }
            };
    // Non-evicting per-run session cache to avoid LRU thrashing on large JARs
    // When enabled, we consult this first and populate it alongside WL_CACHE.
    private static Map<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap> SESSION_WL_CACHE = null;
    private static Map<String, Long> SESSION_WL_NORM = null;

    public static List<Candidate> candidatesFor(Object oldKey, List<?> newKeys, int k, Map<?, org.objectweb.asm.tree.MethodNode> nodes){
        if(k<=0) k=DEFAULT_K;
        final long phaseStart = System.nanoTime();
    final it.unimi.dsi.fastutil.longs.Long2IntSortedMap oldBag = wlBag(nodes.get(oldKey), "old#"+ fingerprintOf(oldKey));
    final String oldId = "old#"+ fingerprintOf(oldKey);
    final long n2Old = wlNorm(oldId, oldBag);
        if (DEBUG) { System.out.println("[WL] begin oldId=" + oldId + " news=" + newKeys.size() + " k=" + k); System.out.flush(); }
        List<Candidate> out=new ArrayList<Candidate>();
        int progress = 0;
        final long wlWatchMs = Long.getLong("mapper.wl.watchdog.ms", 3000L).longValue();
        final long candWatchMs = Long.getLong("mapper.cand.watchdog.ms", 10000L).longValue();
        for(Object nk: newKeys){
            long t0 = System.nanoTime();
            String newId = "new#"+ fingerprintOf(nk);
            if (DEBUG) { System.out.println("[WL] bag.start newId=" + newId); System.out.flush(); }
            it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = wlBag(nodes.get(nk), newId);
            long dtMs = (System.nanoTime() - t0) / 1_000_000L;
            if (wlWatchMs > 0 && dtMs > wlWatchMs) {
                if (DEBUG) WLRefinement.dumpAllStacks("[WL] watchdog: slow bag newId=" + newId + " ms=" + dtMs);
            }
            if (DEBUG) {
                System.out.println("[WL] bag.end newId=" + newId + " ms=" + dtMs);
                if ((++progress % 10) == 0) System.out.flush();
            } else {
                progress++;
            }
            final long n2New = wlNorm(newId, nb);
            double score = cosineDotOnly(oldBag, nb, n2Old, n2New);
            out.add(new Candidate(newId,score));
            // Candidate-level watchdog (optional)
            long candMs = (System.nanoTime() - phaseStart) / 1_000_000L;
            if (candWatchMs > 0 && candMs > candWatchMs && DEBUG) {
                WLRefinement.dumpAllStacks("[WL] watchdog: candidates running slow oldId=" + oldId + " ms=" + candMs);
            }
        }
        final String baseOldId = oldId;
        Collections.sort(out, new Comparator<Candidate>(){ public int compare(Candidate a, Candidate b){ int c = Double.compare(b.wlScore, a.wlScore); if(c!=0) return c; long ha=StableHash64.hashUtf8(baseOldId+"->"+a.newId); long hb=StableHash64.hashUtf8(baseOldId+"->"+b.newId); return Long.compareUnsigned(ha,hb);} });
        if(out.size()>k) out = new ArrayList<Candidate>(out.subList(0,k));
        if (DEBUG) { System.out.println(String.format(java.util.Locale.ROOT, "[WL] end oldId=%s ms=%.1f", oldId, (System.nanoTime()-phaseStart)/1e6)); System.out.flush(); }
        return out;
    }

    /** Begin a per-run session cache sized to expected methods; deterministic access-order not needed. */
    public static void beginSessionCache(int expectedNewMethods){
        // Guard against misuse across runs; overwrite safely
    SESSION_WL_CACHE = new LinkedHashMap<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap>(Math.max(16, expectedNewMethods));
    SESSION_WL_NORM  = new LinkedHashMap<String, Long>(Math.max(16, expectedNewMethods));
    }

    /** End the current session cache and free references for GC. */
    public static void endSessionCache(){
    SESSION_WL_CACHE = null;
    SESSION_WL_NORM  = null;
    }

    private static String fingerprintOf(Object k){ try{ try{ java.lang.reflect.Method m=k.getClass().getMethod("fingerprintSha256"); Object r=m.invoke(k); return String.valueOf(r);} catch(NoSuchMethodException ex){ java.lang.reflect.Method m2=k.getClass().getMethod("fingerprint"); Object r2=m2.invoke(k); return String.valueOf(r2);} } catch(Exception e){ long h=StableHash64.hashUtf8(String.valueOf(k)); return Long.toHexString(h);} }
    private static it.unimi.dsi.fastutil.longs.Long2IntSortedMap wlBag(org.objectweb.asm.tree.MethodNode mn, String cacheKey){
        // Session hit: single-run non-evicting cache
        if (SESSION_WL_CACHE != null) {
            it.unimi.dsi.fastutil.longs.Long2IntSortedMap sh = SESSION_WL_CACHE.get(cacheKey);
            if (sh != null) { ensureNorm(cacheKey, sh); return sh; }
        }
        // Global LRU hit: return a defensive copy to avoid aliasing
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap hit = WL_CACHE.get(cacheKey);
        if (hit != null) { ensureNorm(cacheKey, hit); return hit; }
        ReducedCFG cfg=ReducedCFG.build(mn);
        Dominators dom=Dominators.compute(cfg);
        // Use unified capped WL path (fastutil), convert to SortedMap with unsigned ordering
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap fu = WLRefinement.tokenBagFinal(cfg, dom, 2);
        // Store into caches deterministically
        WL_CACHE.put(cacheKey, fu);
        if (SESSION_WL_CACHE != null) SESSION_WL_CACHE.put(cacheKey, fu);
        storeNorm(cacheKey, computeNorm2(fu));
        return fu;
    }

    private static void ensureNorm(String key, it.unimi.dsi.fastutil.longs.Long2IntSortedMap bag){
        if (SESSION_WL_NORM != null && SESSION_WL_NORM.containsKey(key)) return;
        if (WL_NORM_CACHE.containsKey(key)) return;
        storeNorm(key, computeNorm2(bag));
    }

    private static void storeNorm(String key, long n2){
        WL_NORM_CACHE.put(key, Long.valueOf(n2));
        if (SESSION_WL_NORM != null) SESSION_WL_NORM.put(key, Long.valueOf(n2));
    }

    private static long wlNorm(String key, it.unimi.dsi.fastutil.longs.Long2IntSortedMap bag){
        if (SESSION_WL_NORM != null) {
            Long v = SESSION_WL_NORM.get(key); if (v != null) return v.longValue();
        }
        Long g = WL_NORM_CACHE.get(key);
        if (g != null) return g.longValue();
        long n2 = computeNorm2(bag);
        storeNorm(key, n2);
        return n2;
    }

    private static long computeNorm2(it.unimi.dsi.fastutil.longs.Long2IntSortedMap bag){
        long n2 = 0;
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : bag.long2IntEntrySet()) { int v = e.getIntValue(); n2 += (long)v * (long)v; }
        return n2;
    }

    private static double cosine(it.unimi.dsi.fastutil.longs.Long2IntSortedMap a, it.unimi.dsi.fastutil.longs.Long2IntSortedMap b){
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ia = a.long2IntEntrySet().iterator();
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ib = b.long2IntEntrySet().iterator();
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry ea = ia.hasNext()? ia.next() : null;
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry eb = ib.hasNext()? ib.next() : null;
        long dot=0, n2=0, m2=0;
        while(ea!=null && eb!=null){
            long ka = ea.getLongKey(); long kb = eb.getLongKey();
            int cmp = Long.compareUnsigned(ka, kb);
            if (cmp==0){ int va=ea.getIntValue(), vb=eb.getIntValue(); dot += (long)va*vb; n2 += (long)va*va; m2 += (long)vb*vb; ea= ia.hasNext()? ia.next(): null; eb= ib.hasNext()? ib.next(): null; }
            else if (cmp<0){ int va=ea.getIntValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; }
            else { int vb=eb.getIntValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; }
        }
        while(ea!=null){ int va=ea.getIntValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; }
        while(eb!=null){ int vb=eb.getIntValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; }
        if(n2==0||m2==0) return 0.0; return dot / (Math.sqrt((double)n2) * Math.sqrt((double)m2));
    }

    private static double cosineDotOnly(it.unimi.dsi.fastutil.longs.Long2IntSortedMap a, it.unimi.dsi.fastutil.longs.Long2IntSortedMap b, long n2, long m2){
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ia = a.long2IntEntrySet().iterator();
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ib = b.long2IntEntrySet().iterator();
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry ea = ia.hasNext()? ia.next() : null;
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry eb = ib.hasNext()? ib.next() : null;
        long dot=0;
        while(ea!=null && eb!=null){
            long ka = ea.getLongKey(); long kb = eb.getLongKey();
            int cmp = Long.compareUnsigned(ka, kb);
            if (cmp==0){ int va=ea.getIntValue(), vb=eb.getIntValue(); dot += (long)va*vb; ea= ia.hasNext()? ia.next(): null; eb= ib.hasNext()? ib.next(): null; }
            else if (cmp<0){ ea= ia.hasNext()? ia.next(): null; }
            else { eb= ib.hasNext()? ib.next(): null; }
        }
        if(n2==0||m2==0) return 0.0; return dot / (Math.sqrt((double)n2) * Math.sqrt((double)m2));
    }
}
