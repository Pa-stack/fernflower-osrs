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
    // Future knob (measurement-only): currently unused; zero-intersection gate is always ON
    @SuppressWarnings("unused")
    private static final boolean TOKEN_GATE_FLAG = Boolean.parseBoolean(System.getProperty("mapper.cand.tokenGate", "false"));
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
    // Disk cache (P1): feature flag and state
    private static final boolean DISK_CACHE = Boolean.parseBoolean(System.getProperty("mapper.cache.disk", "false"));
    private static volatile boolean DISK_CACHE_SOFT_DISABLED = false;
    // Tier-1 index: sig64 -> array of new method numeric ids (deterministic unsigned-sorted)
    // Built lazily per session. Volatile for safe publication after synchronized build.
    private static volatile Map<Long, int[]> TIER1_INDEX = null;
    // Feature flag to allow rollback
    private static final boolean ENABLE_TIER1 = Boolean.parseBoolean(System.getProperty("mapper.cand.tier1", "true"));

    // --- small helpers (P2-lite) for logging and soft-disable ---
    private static void logCache(String fmt, Object... args){ if (DEBUG) System.out.println("[CACHE] " + String.format(java.util.Locale.ROOT, fmt, args)); }
    private static void softDisableOnce(Throwable t){ boolean wasEnabled = !DISK_CACHE_SOFT_DISABLED; DISK_CACHE_SOFT_DISABLED = true; if (wasEnabled && DEBUG) System.out.println("[CACHE] disabled wl due to IO: " + t.getClass().getSimpleName()); }

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
        int tokenGateSkips = 0; // per-call counter
    // Build index lazily on first use (only if enabled)
    if (ENABLE_TIER1 && TIER1_INDEX == null) buildTier1Index(newKeys, nodes);

    // If we have an index, do a two-pass evaluation: Tier-1 indices first, then remaining keys.
    if (ENABLE_TIER1 && TIER1_INDEX != null) {
        long sigOld = sig64OfBag(oldBag);
        int[] idxs = TIER1_INDEX.get(Long.valueOf(sigOld));
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        int progressLocal = 0;
        if (idxs != null) {
            for (int i : idxs) {
                if (i < 0 || i >= newKeys.size()) continue;
                seen.add(i);
                Object nk = newKeys.get(i);
                String newId = "new#"+ fingerprintOf(nk);
                long t0 = System.nanoTime();
                if (DEBUG) { System.out.println("[WL] bag.start newId=" + newId); System.out.flush(); }
                it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = wlBag(nodes.get(nk), newId);
                long dtMs = (System.nanoTime() - t0) / 1_000_000L;
                if (wlWatchMs > 0 && dtMs > wlWatchMs) if (DEBUG) WLRefinement.dumpAllStacks("[WL] watchdog: slow bag newId=" + newId + " ms=" + dtMs);
                if (DEBUG) { System.out.println("[WL] bag.end newId=" + newId + " ms=" + dtMs); if ((++progressLocal % 10) == 0) System.out.flush(); } else { progressLocal++; }
                final long n2New = wlNorm(newId, nb);
                double score;
                if (!hasKeyIntersection(oldBag, nb)) { tokenGateSkips++; score = 0.0; }
                else { score = cosineDotOnly(oldBag, nb, n2Old, n2New); }
                // exact equality check to guard collisions
                if (bagsEqual(oldBag, nb)) out.add(new Candidate(newId, score)); else out.add(new Candidate(newId, score));
            }
        }
        // Pass B: remaining keys
        for (int i = 0; i < newKeys.size(); i++) {
            if (seen.contains(i)) continue;
            Object nk = newKeys.get(i);
            String newId = "new#"+ fingerprintOf(nk);
            long t0 = System.nanoTime();
            if (DEBUG) { System.out.println("[WL] bag.start newId=" + newId); System.out.flush(); }
            it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = wlBag(nodes.get(nk), newId);
            long dtMs = (System.nanoTime() - t0) / 1_000_000L;
            if (wlWatchMs > 0 && dtMs > wlWatchMs) if (DEBUG) WLRefinement.dumpAllStacks("[WL] watchdog: slow bag newId=" + newId + " ms=" + dtMs);
            if (DEBUG) { System.out.println("[WL] bag.end newId=" + newId + " ms=" + dtMs); if ((++progressLocal % 10) == 0) System.out.flush(); } else { progressLocal++; }
            final long n2New = wlNorm(newId, nb);
            double score;
            if (!hasKeyIntersection(oldBag, nb)) { tokenGateSkips++; score = 0.0; }
            else { score = cosineDotOnly(oldBag, nb, n2Old, n2New); }
            out.add(new Candidate(newId, score));
        }
    } else {
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
            double score;
            if (!hasKeyIntersection(oldBag, nb)) { tokenGateSkips++; score = 0.0; }
            else { score = cosineDotOnly(oldBag, nb, n2Old, n2New); }
            out.add(new Candidate(newId,score));
            // Candidate-level watchdog (optional)
            long candMs = (System.nanoTime() - phaseStart) / 1_000_000L;
            if (candWatchMs > 0 && candMs > candWatchMs && DEBUG) {
                WLRefinement.dumpAllStacks("[WL] watchdog: candidates running slow oldId=" + oldId + " ms=" + candMs);
            }
        }
    }
        // Emit token gate stats for this oldId
        if (DEBUG) { System.out.println("[CAND] tokenGate.skip=" + tokenGateSkips); }
        final String baseOldId = oldId;
        // If tier1 enabled, partition candidates into Tier-1 (same sig64 and exact bag equality) and Tier-2
        if (ENABLE_TIER1 && TIER1_INDEX != null) {
            List<Candidate> tier1 = new ArrayList<Candidate>();
            List<Candidate> tier2 = new ArrayList<Candidate>();
            // exact bag equality recheck for Tier-1 candidates (safe against collisions)
            it.unimi.dsi.fastutil.longs.Long2IntSortedMap oldBagCheck = wlBag(nodes.get(oldKey), baseOldId);
            for (Candidate c : out) {
                it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = WL_CACHE.get(c.newId);
                if (nb == null && SESSION_WL_CACHE != null) nb = SESSION_WL_CACHE.get(c.newId);
                if (nb != null && bagsEqual(oldBagCheck, nb)) tier1.add(c); else tier2.add(c);
            }
            if (DEBUG) System.out.println(String.format(java.util.Locale.ROOT, "[CAND] tier1.size=%d tier2.size=%d", tier1.size(), tier2.size()));
            out.clear(); out.addAll(tier1); out.addAll(tier2);
        }

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
        // Reset soft-disable at session start so tests/runs can proceed independently
        DISK_CACHE_SOFT_DISABLED = false;
        // Test-only behavior: optionally clear global LRU caches for deterministic cold/warm in tests
        if (Boolean.getBoolean("mapper.test.sessionReset")) {
            WL_CACHE.clear();
            WL_NORM_CACHE.clear();
        }
    }

    /** End the current session cache and free references for GC. */
    public static void endSessionCache(){
    SESSION_WL_CACHE = null;
    SESSION_WL_NORM  = null;
    }

    private static String fingerprintOf(Object k){ try{ try{ java.lang.reflect.Method m=k.getClass().getMethod("fingerprintSha256"); Object r=m.invoke(k); return String.valueOf(r);} catch(NoSuchMethodException ex){ java.lang.reflect.Method m2=k.getClass().getMethod("fingerprint"); Object r2=m2.invoke(k); return String.valueOf(r2);} } catch(Exception e){ long h=StableHash64.hashUtf8(String.valueOf(k)); return Long.toHexString(h);} }
    private static it.unimi.dsi.fastutil.longs.Long2IntSortedMap wlBag(org.objectweb.asm.tree.MethodNode mn, String cacheKey){
        // Determine WL mode eagerly (for disk key) and prepare cfg/dom for potential compute path
        final io.bytecodemapper.core.cfg.ReducedCFG cfg = io.bytecodemapper.core.cfg.ReducedCFG.build(mn);
        final io.bytecodemapper.core.dom.Dominators dom = io.bytecodemapper.core.dom.Dominators.compute(cfg);
        final String modeStr = cfg.allBlockIds().length > parseMaxBlocksProp() ? "lite" : "full";
        final String diskKey = DISK_CACHE ? diskKeyFor(cacheKey, modeStr) : null;

        // Session hit: single-run non-evicting cache
        if (SESSION_WL_CACHE != null) {
            it.unimi.dsi.fastutil.longs.Long2IntSortedMap sh = SESSION_WL_CACHE.get(cacheKey);
            if (sh != null) { ensureNorm(cacheKey, sh); return sh; }
        }
        // Global LRU hit (before any disk I/O)
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap hit = WL_CACHE.get(cacheKey);
        if (hit != null) { ensureNorm(cacheKey, hit); if (DISK_CACHE && diskKey != null) logCache("CACHE_HIT:wl key=%s", diskKey); return hit; }
        // Disk cache read-through (only if enabled and not soft-disabled)
        if (DISK_CACHE && !DISK_CACHE_SOFT_DISABLED) {
            try {
                // Path safety: soft-disable on suspicious cache dir
                String baseProp = System.getProperty("mapper.cache.dir");
                if (baseProp != null && baseProp.indexOf("..") >= 0) { softDisableOnce(new java.io.IOException("bad cache.dir")); }
                java.nio.file.Path dir = diskCacheDir();
                java.nio.file.Path file = dir.resolve(diskKey + ".bin").toAbsolutePath().normalize();
                if (java.nio.file.Files.isRegularFile(file)) {
                    byte[] all;
                    try (java.io.DataInputStream din = io.bytecodemapper.core.util.FileCacheIO.newDataInputStream(file)) {
                        all = new byte[Math.max(0, (int)java.nio.file.Files.size(file))];
                        int off = 0; while (off < all.length) { int r = din.read(all, off, all.length - off); if (r < 0) break; off += r; }
                    }
                    it.unimi.dsi.fastutil.longs.Long2IntSortedMap m = WLRefinement.decodeBagFastutil(all);
                    storeNorm(cacheKey, computeNorm2(m));
                    logCache("CACHE_HIT:wl key=%s", diskKey);
                    WL_CACHE.put(cacheKey, m);
                    if (SESSION_WL_CACHE != null) SESSION_WL_CACHE.put(cacheKey, m);
                    return m;
                }
            } catch (Throwable ioe) {
                softDisableOnce(ioe);
            }
        }
        // Use unified capped WL path (fastutil), convert to SortedMap with unsigned ordering
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap fu = WLRefinement.tokenBagFinal(cfg, dom, 2);
        // Store into caches deterministically
        WL_CACHE.put(cacheKey, fu);
        if (SESSION_WL_CACHE != null) SESSION_WL_CACHE.put(cacheKey, fu);
        storeNorm(cacheKey, computeNorm2(fu));
        // Write-back to disk if enabled
        if (DISK_CACHE && !DISK_CACHE_SOFT_DISABLED) {
            try {
                java.nio.file.Path dir = diskCacheDir(); io.bytecodemapper.core.util.FileCacheIO.ensureDir(dir);
                java.nio.file.Path target = dir.resolve(diskKey + ".bin").toAbsolutePath().normalize();
                boolean existed = java.nio.file.Files.isRegularFile(target);
                byte[] bytes = WLRefinement.encodeBagFastutil(fu); // v1 header + entries
                java.nio.file.Path tmp = io.bytecodemapper.core.util.FileCacheIO.tempSibling(target);
                try (java.io.DataOutputStream dout = io.bytecodemapper.core.util.FileCacheIO.newDataOutputStream(tmp)) { dout.write(bytes); }
                io.bytecodemapper.core.util.FileCacheIO.atomicReplace(tmp, target);
                logCache(existed ? "CACHE_HIT:wl key=%s" : "CACHE_MISS:wl key=%s", diskKey);
            } catch (Throwable ioe) {
                softDisableOnce(ioe);
            }
        }
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

    // Deterministic exact bag equality: compare entry sets (keys unsigned order and counts)
    private static boolean bagsEqual(it.unimi.dsi.fastutil.longs.Long2IntSortedMap a, it.unimi.dsi.fastutil.longs.Long2IntSortedMap b){
        if (a.size() != b.size()) return false;
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ia = a.long2IntEntrySet().iterator();
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ib = b.long2IntEntrySet().iterator();
        while(ia.hasNext() && ib.hasNext()){
            it.unimi.dsi.fastutil.longs.Long2IntMap.Entry ea = ia.next();
            it.unimi.dsi.fastutil.longs.Long2IntMap.Entry eb = ib.next();
            if (Long.compareUnsigned(ea.getLongKey(), eb.getLongKey()) != 0) return false;
            if (ea.getIntValue() != eb.getIntValue()) return false;
        }
        return true;
    }

    // Build a per-session tier1 index mapping StableHash64.of(bag) -> deterministic int[] of new method ids
    private static void buildTier1Index(List<?> newKeys, Map<?, org.objectweb.asm.tree.MethodNode> nodes){
        synchronized(MethodCandidateGenerator.class){
            if (TIER1_INDEX != null) return; // double-check
            Map<Long, java.util.List<Integer>> tmp = new java.util.TreeMap<Long, java.util.List<Integer>>(new java.util.Comparator<Long>(){ public int compare(Long a, Long b){ return Long.compareUnsigned(a.longValue(), b.longValue()); }});
            int idx = 0;
            for(Object nk: newKeys){
                String newId = "new#"+ fingerprintOf(nk);
                it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = wlBag(nodes.get(nk), newId);
                // signature over bag deterministic by iterating unsigned keys and counts
                long sig = sig64OfBag(nb);
                java.util.List<Integer> list = tmp.get(Long.valueOf(sig)); if (list==null){ list = new ArrayList<Integer>(); tmp.put(Long.valueOf(sig), list); }
                list.add(idx);
                idx++;
            }
            Map<Long,int[]> published = new java.util.TreeMap<Long,int[]>(new java.util.Comparator<Long>(){ public int compare(Long a, Long b){ return Long.compareUnsigned(a.longValue(), b.longValue()); }});
            for(java.util.Map.Entry<Long, java.util.List<Integer>> e: tmp.entrySet()){
                java.util.List<Integer> l = e.getValue(); int[] arr = new int[l.size()]; for(int i=0;i<l.size();i++) arr[i]=l.get(i); Arrays.sort(arr); published.put(e.getKey(), arr);
            }
            TIER1_INDEX = Collections.unmodifiableMap(published);
        }
    }

    // Canonical signature over a WL bag: iterate entries in unsigned-key order and build "key:count;" string
    private static long sig64OfBag(it.unimi.dsi.fastutil.longs.Long2IntSortedMap m){
        StringBuilder sb = new StringBuilder();
        for(it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : m.long2IntEntrySet()){
            sb.append(Long.toUnsignedString(e.getLongKey())); sb.append(':'); sb.append(e.getIntValue()); sb.append(';');
        }
        return StableHash64.hashUtf8(sb.toString());
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

    // Zero-intersection gate: iterate both entry sets in unsigned key order and bail early on first match
    private static boolean hasKeyIntersection(it.unimi.dsi.fastutil.longs.Long2IntSortedMap a, it.unimi.dsi.fastutil.longs.Long2IntSortedMap b){
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ia = a.long2IntEntrySet().iterator();
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ib = b.long2IntEntrySet().iterator();
        if(!ia.hasNext() || !ib.hasNext()) return false;
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry ea = ia.next();
        it.unimi.dsi.fastutil.longs.Long2IntMap.Entry eb = ib.next();
        while(true){
            long ka = ea.getLongKey(); long kb = eb.getLongKey();
            int cmp = Long.compareUnsigned(ka, kb);
            if(cmp==0) return true;
            if(cmp<0){ if(ia.hasNext()) ea = ia.next(); else return false; }
            else { if(ib.hasNext()) eb = ib.next(); else return false; }
        }
    }

    // --- Disk cache helpers ---
    private static java.nio.file.Path diskCacheDir() {
        String base = System.getProperty("mapper.cache.dir");
        if (base == null || base.trim().isEmpty()) {
            String home = System.getProperty("user.home", ".");
            base = home + java.io.File.separator + ".bytecodemapper" + java.io.File.separator + "cache" + java.io.File.separator + "wl";
        }
        return java.nio.file.Paths.get(base).toAbsolutePath().normalize();
    }

    private static String diskKeyFor(String cacheKey, String mode){
        // Normalize method id for disk key stability: strip leading old#/new# if present
        String methodId = cacheKey == null ? "" : cacheKey.trim();
        int hash = cacheKey.indexOf('#');
        if (hash >= 0 && (cacheKey.startsWith("old#") || cacheKey.startsWith("new#"))) {
            methodId = cacheKey.substring(hash + 1).trim();
        }
        String maxBlocks = System.getProperty("mapper.wl.max.blocks","800");
        String enrich = Boolean.getBoolean("mapper.wl.label.enrich") ? "|ENRICH=on" : ""; // TODO: bump ALGO to WLv2 when enrichment lands
        String s = "ALGO=WLv1|JAVA=" + System.getProperty("java.version","?")
                + "|MODE=" + (mode == null ? "full" : mode) + "|ROUNDS=2|MAXBLOCKS=" + maxBlocks
                + "|METHOD=" + methodId
                + "|BAGSEED=base" + enrich;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(d.length*2); for(byte b: d) hex.append(String.format(java.util.Locale.ROOT, "%02x", b)); return hex.toString().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e){
            return Integer.toHexString(s.hashCode());
        }
    }

    private static int parseMaxBlocksProp(){ try { return Integer.parseInt(System.getProperty("mapper.wl.max.blocks","800")); } catch(Exception e){ return 800; } }
}
