package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;

import java.util.*;

public final class MethodCandidateGenerator {
    private MethodCandidateGenerator(){}
    public static final int DEFAULT_K = 25;
    public static final class Candidate { public final String newId; public final double wlScore; public Candidate(String id,double s){this.newId=id;this.wlScore=s;} }
    static final boolean DEBUG = Boolean.getBoolean("mapper.debug");

    public static List<Candidate> candidatesFor(Object oldKey, List<?> newKeys, int k, Map<?, org.objectweb.asm.tree.MethodNode> nodes){
        if(k<=0) k=DEFAULT_K;
        final long phaseStart = System.nanoTime();
        final SortedMap<Long,Integer> oldBag = wlBag(nodes.get(oldKey));
        final String oldId = "old#"+ fingerprintOf(oldKey);
        if (DEBUG) { System.out.println("[WL] begin oldId=" + oldId + " news=" + newKeys.size() + " k=" + k); System.out.flush(); }
        List<Candidate> out=new ArrayList<Candidate>();
        int progress = 0;
        for(Object nk: newKeys){
            long t0 = System.nanoTime();
            String newId = "new#"+ fingerprintOf(nk);
            if (DEBUG) { System.out.println("[WL] bag.start newId=" + newId); System.out.flush(); }
            SortedMap<Long,Integer> nb = wlBag(nodes.get(nk));
            long dtMs = (System.nanoTime() - t0) / 1_000_000L;
            long wlWatchMs = Long.getLong("mapper.wl.watchdog.ms", 3000L).longValue();
            if (dtMs > wlWatchMs) {
                WLRefinement.dumpAllStacks("[WL] watchdog: slow bag newId=" + newId + " ms=" + dtMs);
            }
            if (DEBUG) {
                System.out.println("[WL] bag.end newId=" + newId + " ms=" + dtMs);
                if ((++progress % 10) == 0) System.out.flush();
            } else {
                progress++;
            }
            double score = cosine(oldBag, nb);
            out.add(new Candidate(newId,score));
        }
        final String baseOldId = oldId;
        Collections.sort(out, new Comparator<Candidate>(){ public int compare(Candidate a, Candidate b){ int c = Double.compare(b.wlScore, a.wlScore); if(c!=0) return c; long ha=StableHash64.hashUtf8(baseOldId+"->"+a.newId); long hb=StableHash64.hashUtf8(baseOldId+"->"+b.newId); return Long.compareUnsigned(ha,hb);} });
        if(out.size()>k) out = new ArrayList<Candidate>(out.subList(0,k));
        if (DEBUG) { System.out.println(String.format(java.util.Locale.ROOT, "[WL] end oldId=%s ms=%.1f", oldId, (System.nanoTime()-phaseStart)/1e6)); System.out.flush(); }
        return out;
    }

    private static String fingerprintOf(Object k){ try{ try{ java.lang.reflect.Method m=k.getClass().getMethod("fingerprintSha256"); Object r=m.invoke(k); return String.valueOf(r);} catch(NoSuchMethodException ex){ java.lang.reflect.Method m2=k.getClass().getMethod("fingerprint"); Object r2=m2.invoke(k); return String.valueOf(r2);} } catch(Exception e){ long h=StableHash64.hashUtf8(String.valueOf(k)); return Long.toHexString(h);} }
    private static SortedMap<Long,Integer> wlBag(org.objectweb.asm.tree.MethodNode mn){ ReducedCFG cfg=ReducedCFG.build(mn); Dominators dom=Dominators.compute(cfg); return WLRefinement.tokenBagFinalSorted(cfg,dom,2);}

    private static double cosine(SortedMap<Long,Integer> a, SortedMap<Long,Integer> b){ Iterator<Map.Entry<Long,Integer>> ia=a.entrySet().iterator(), ib=b.entrySet().iterator(); Map.Entry<Long,Integer> ea= ia.hasNext()? ia.next(): null; Map.Entry<Long,Integer> eb= ib.hasNext()? ib.next(): null; long dot=0, n2=0, m2=0; while(ea!=null && eb!=null){ long ka=ea.getKey(), kb=eb.getKey(); int cmp=Long.compareUnsigned(ka,kb); if(cmp==0){ int va=ea.getValue(), vb=eb.getValue(); dot += (long)va*vb; n2 += (long)va*va; m2 += (long)vb*vb; ea= ia.hasNext()? ia.next(): null; eb= ib.hasNext()? ib.next(): null; } else if(cmp<0){ int va=ea.getValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; } else { int vb=eb.getValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; } } while(ea!=null){ int va=ea.getValue(); n2 += (long)va*va; ea= ia.hasNext()? ia.next(): null; } while(eb!=null){ int vb=eb.getValue(); m2 += (long)vb*vb; eb= ib.hasNext()? ib.next(): null; } if(n2==0||m2==0) return 0.0; return dot / (Math.sqrt((double)n2) * Math.sqrt((double)m2)); }
}
