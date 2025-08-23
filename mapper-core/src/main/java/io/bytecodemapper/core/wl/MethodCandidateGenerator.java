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
    // Simple LRU cache (access-order) for WL bags keyed by newId; deterministic and single-threaded by default
    private static final LinkedHashMap<String, SortedMap<Long,Integer>> WL_CACHE = new LinkedHashMap<String, SortedMap<Long,Integer>>(16, 0.75f, true){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SortedMap<Long, Integer>> eldest) {
            return size() > WL_CACHE_CAP;
        }
    };

    public static List<Candidate> candidatesFor(Object oldKey, List<?> newKeys, int k, Map<?, org.objectweb.asm.tree.MethodNode> nodes){
        if(k<=0) k=DEFAULT_K;
        final long phaseStart = System.nanoTime();
        final SortedMap<Long,Integer> oldBag = wlBag(nodes.get(oldKey), "old#"+ fingerprintOf(oldKey));
        final String oldId = "old#"+ fingerprintOf(oldKey);
        if (DEBUG) { System.out.println("[WL] begin oldId=" + oldId + " news=" + newKeys.size() + " k=" + k); System.out.flush(); }
        List<Candidate> out=new ArrayList<Candidate>();
        int progress = 0;
        final long wlWatchMs = Long.getLong("mapper.wl.watchdog.ms", 3000L).longValue();
        final long candWatchMs = Long.getLong("mapper.cand.watchdog.ms", 10000L).longValue();
        for(Object nk: newKeys){
            long t0 = System.nanoTime();
            String newId = "new#"+ fingerprintOf(nk);
            if (DEBUG) { System.out.println("[WL] bag.start newId=" + newId); System.out.flush(); }
            SortedMap<Long,Integer> nb = wlBag(nodes.get(nk), newId);
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
            double score = cosine(oldBag, nb);
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

    private static String fingerprintOf(Object k){ try{ try{ java.lang.reflect.Method m=k.getClass().getMethod("fingerprintSha256"); Object r=m.invoke(k); return String.valueOf(r);} catch(NoSuchMethodException ex){ java.lang.reflect.Method m2=k.getClass().getMethod("fingerprint"); Object r2=m2.invoke(k); return String.valueOf(r2);} } catch(Exception e){ long h=StableHash64.hashUtf8(String.valueOf(k)); return Long.toHexString(h);} }
    private static SortedMap<Long,Integer> wlBag(org.objectweb.asm.tree.MethodNode mn, String cacheKey){
        // LRU hit: return a defensive copy to avoid aliasing
        SortedMap<Long,Integer> hit = WL_CACHE.get(cacheKey);
        if (hit != null) {
            return new TreeMap<Long,Integer>(hit); // TreeMap copy keeps ordering
        }
        ReducedCFG cfg=ReducedCFG.build(mn);
        Dominators dom=Dominators.compute(cfg);
        // Use unified capped WL path (fastutil), convert to SortedMap with unsigned ordering
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap fu = WLRefinement.tokenBagFinal(cfg, dom, 2);
        java.util.SortedMap<Long,Integer> bag = new java.util.TreeMap<Long,Integer>(new java.util.Comparator<Long>(){ public int compare(Long a, Long b){ return Long.compareUnsigned(a,b); } });
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : fu.long2IntEntrySet()) {
            bag.put(Long.valueOf(e.getLongKey()), Integer.valueOf(e.getIntValue()));
        }
        // Store copy into LRU cache
        WL_CACHE.put(cacheKey, new TreeMap<Long,Integer>(bag));
        return bag;
    }

    private static double cosine(SortedMap<Long,Integer> a, SortedMap<Long,Integer> b){ Iterator<Map.Entry<Long,Integer>> ia=a.entrySet().iterator(), ib=b.entrySet().iterator(); Map.Entry<Long,Integer> ea= ia.hasNext()? ia.next(): null; Map.Entry<Long,Integer> eb= ib.hasNext()? ib.next(): null; long dot=0, n2=0, m2=0; while(ea!=null && eb!=null){ long ka=ea.getKey(), kb=eb.getKey(); int cmp=Long.compareUnsigned(ka,kb); if(cmp==0){ int va=ea.getValue(), vb=eb.getValue(); dot += (long)va*vb; n2 += (long)va*va; m2 += (long)vb*vb; ea= ia.hasNext()? ia.next(): null; eb= ib.hasNext()? ib.next(): null; } else if(cmp<0){ int va=ea.getValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; } else { int vb=eb.getValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; } } while(ea!=null){ int va=ea.getValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; } while(eb!=null){ int vb=eb.getValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; } if(n2==0||m2==0) return 0.0; return dot / (Math.sqrt((double)n2) * Math.sqrt((double)m2)); }
}
