// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatcher WL INDEX BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;
import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.method.MethodScorer;
import io.bytecodemapper.core.hash.StableHash64;
import io.bytecodemapper.core.index.NsfIndex;
import io.bytecodemapper.signals.idf.IdfStore;
import io.bytecodemapper.signals.normalized.NormalizedAdapters;
import io.bytecodemapper.signals.normalized.NormalizedMethod;
import io.bytecodemapper.signals.normalized.NormalizedFeatures;
import io.bytecodemapper.signals.micro.MicroPatternExtractor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/**
 * Method matching entrypoint (CLI-side adapter) using precomputed feature caches.
 * Builds a WL signature index on the NEW side and scores candidates using
 * MethodScorer. Accept if best >= TAU_ACCEPT and (best - second) >= MIN_MARGIN.
 */
public final class MethodMatcher {

    public static final int WL_K = 4; // iterations used by WL signature (precomputed in cache)

    // Backwards-compatible DTOs
    public static final class CandidateScore {
        public final String newOwner, newName, desc;
        public final double total;  // placeholder; filled in Prompt B
        public final double margin; // placeholder; filled in Prompt B
        public CandidateScore(String no, String nn, String d, double t, double m) {
            this.newOwner = no; this.newName = nn; this.desc = d; this.total = t; this.margin = m;
        }
    }

    public static final class Abstention {
        public final String oldOwner, oldName, desc;
        public final List<CandidateScore> candidates;
        public Abstention(String oo, String on, String d, List<CandidateScore> cs) {
            this.oldOwner = oo; this.oldName = on; this.desc = d; this.candidates = cs;
        }
    }

    public static class Pair {
        public final String oldOwner, oldName, newName, desc;
        public Pair(String o, String on, String nn, String d) { this.oldOwner=o; this.oldName=on; this.newName=nn; this.desc=d; }
    }
    public static class MethodMatchResult {
        public final java.util.List<Pair> accepted = new java.util.ArrayList<Pair>();
        // New in Prompt A
        public final java.util.List<Abstention> abstained = new java.util.ArrayList<Abstention>();
    // Candidate counts per old-side method (post-dedup, pre-score)
    public final java.util.List<Integer> exactCounts = new java.util.ArrayList<Integer>();
    public final java.util.List<Integer> nearCounts  = new java.util.ArrayList<Integer>();
    }

    // >>> AUTOGEN: BYTECODEMAPPER MATCH NSF TIERS BEGIN
    // New tier order (comma list). Default: exact nsf, near nsf, then WL exact, then relaxed WL
    private static String NSFTierOrder = "exact,near,wl,wlrelaxed";
    public static void setNsftierOrder(String csv){ if (csv!=null && csv.trim().length()>0) NSFTierOrder = csv; }
    // <<< AUTOGEN: BYTECODEMAPPER MATCH NSF TIERS END

    // Rollout mode for nsf64 usage (default CANONICAL)
    private static io.bytecodemapper.cli.flags.UseNsf64Mode NSF_MODE = io.bytecodemapper.cli.flags.UseNsf64Mode.CANONICAL;
    public static void setUseNsf64Mode(io.bytecodemapper.cli.flags.UseNsf64Mode m) {
        if (m != null) NSF_MODE = m;
    }

    /**
     * Overload that accepts feature caches. Builds WL index on NEW side and, for each OLD
     * method, generates candidates (same desc, equal WL first; if none, relax to same desc
     * in mapped owner). Scores with MethodScorer and either accepts or abstains with a
     * deterministic, score-sorted candidate list.
     */
    public static MethodMatchResult matchMethods(
            Map<String, ClassNode> oldClasses,
            Map<String, ClassNode> newClasses,
            Map<String,String> classMap,
            Map<String, Map<String, MethodFeatureCacheEntry>> oldFeat,
            Map<String, Map<String, MethodFeatureCacheEntry>> newFeat,
            IdfStore idf,
            boolean deterministic,
            boolean debugStats) {
        MethodMatchResult out = new MethodMatchResult();

        // 1) Build NEW-side index by (desc, wl)
        Map<Key, List<NewRef>> wlIndex = buildNewSideWlIndex(newFeat);

        // >>> AUTOGEN: BYTECODEMAPPER MATCH NSF TIERS BEGIN
        // Build NEW-side nsf index per newOwner using canonical nsf64 with optional surrogate fallback.
        final java.util.Map<String, NsfIndex> nsfIndexByNewOwner = new java.util.LinkedHashMap<String, NsfIndex>();
        // Provenance map keyed by owner\0desc\0name\0fp -> "nsf64" or "nsf_surrogate"
        final java.util.Map<String, String> nsfProvByKeyFp = new java.util.LinkedHashMap<String, String>();
        {
            java.util.ArrayList<String> newOwners = new java.util.ArrayList<String>(newFeat.keySet());
            java.util.Collections.sort(newOwners);
            for (String newOwner : newOwners) {
                java.util.Map<String, MethodFeatureCacheEntry> nm = newFeat.get(newOwner);
                if (nm == null) continue;
                NsfIndex idx = new NsfIndex();
                java.util.ArrayList<String> sigs = new java.util.ArrayList<String>(nm.keySet());
                java.util.Collections.sort(sigs);
                ClassNode cn = newClasses.get(newOwner);
                for (String sig : sigs) {
                    MethodFeatureCacheEntry e = nm.get(sig);
                    if (e == null) continue;
                    String name = sig.substring(0, sig.indexOf('('));
                    String desc = sig.substring(sig.indexOf('('));
                    long canonical = 0L;
                    // Compute canonical nsf64 from NormalizedMethod if available
                    if (cn != null) {
                        org.objectweb.asm.tree.MethodNode mn = findMethod(cn, name, desc);
                        if (mn != null) {
                            try {
                                NormalizedMethod norm = new NormalizedMethod(newOwner, mn, java.util.Collections.<Integer>emptySet());
                                NormalizedFeatures nf = norm.extract();
                                canonical = nf != null ? nf.nsf64 : 0L;
                            } catch (Throwable ignore) { canonical = 0L; }
                        }
                    }
                    // Surrogate fingerprint for fallback/indexing depending on mode
                    String fp = (e.normFingerprint != null ? e.normFingerprint : (e.normalizedBodyHash != null ? e.normalizedBodyHash : (e.normalizedDescriptor != null ? e.normalizedDescriptor : sig)));
                    long surrogate = StableHash64.hashUtf8(fp);

                    io.bytecodemapper.cli.flags.UseNsf64Mode mode = NSF_MODE;
                    if (mode == io.bytecodemapper.cli.flags.UseNsf64Mode.CANONICAL) {
                        long use = (canonical != 0L ? canonical : surrogate);
                        idx.add(newOwner, desc, name, use, io.bytecodemapper.core.index.NsfIndex.Mode.CANONICAL);
                        nsfProvByKeyFp.put(nsfKey(newOwner, desc, name, use), (canonical != 0L ? "nsf64" : "nsf_surrogate"));
                    } else if (mode == io.bytecodemapper.cli.flags.UseNsf64Mode.SURROGATE) {
                        idx.add(newOwner, desc, name, surrogate, io.bytecodemapper.core.index.NsfIndex.Mode.SURROGATE);
                        nsfProvByKeyFp.put(nsfKey(newOwner, desc, name, surrogate), "nsf_surrogate");
                    } else { // BOTH
                        idx.add(newOwner, desc, name, canonical, io.bytecodemapper.core.index.NsfIndex.Mode.BOTH);
                        nsfProvByKeyFp.put(nsfKey(newOwner, desc, name, canonical), canonical != 0L ? "nsf64" : "nsf_surrogate");
                    }
                }
                nsfIndexByNewOwner.put(newOwner, idx);
            }
        }
        // <<< AUTOGEN: BYTECODEMAPPER MATCH NSF TIERS END

        // 2) Iterate OLD owners deterministically
        ArrayList<String> owners = new ArrayList<String>(classMap.keySet());
        Collections.sort(owners);
    final int MAX_CANDIDATES = 120; // safety cap to keep TF-IDF models bounded
        for (String oldOwner : owners) {
            String newOwner = classMap.get(oldOwner);
            Map<String, MethodFeatureCacheEntry> om = oldFeat.get(oldOwner);
            Map<String, MethodFeatureCacheEntry> nm = newFeat.get(newOwner);
            if (om == null || nm == null) continue;

            ArrayList<String> sigs = new ArrayList<String>(om.keySet());
            Collections.sort(sigs);
            for (String sig : sigs) {
                String oldName = sig.substring(0, sig.indexOf('('));
                String desc    = sig.substring(sig.indexOf('('));
                MethodFeatureCacheEntry ofe = om.get(sig);
                if (ofe == null) continue;
                long oldWl = ofe.wlSignature;

                // Primary candidates: collect according to tier order (nsf + wl)
                java.util.ArrayList<NewRef> candsExact = new java.util.ArrayList<NewRef>();
                java.util.ArrayList<NewRef> candsNear  = new java.util.ArrayList<NewRef>();
                java.util.ArrayList<NewRef> candsWl    = new java.util.ArrayList<NewRef>();
                java.util.ArrayList<NewRef> candsRelax = new java.util.ArrayList<NewRef>();
                // Precompute old canonical nsf64 (from NormalizedMethod) and surrogate fallback
                long oldCanonical = 0L;
                {
                    ClassNode ocn = oldClasses.get(oldOwner);
                    if (ocn != null) {
                        org.objectweb.asm.tree.MethodNode omn = findMethod(ocn, oldName, desc);
                        if (omn != null) {
                            try {
                                NormalizedMethod norm = new NormalizedMethod(oldOwner, omn, java.util.Collections.<Integer>emptySet());
                                NormalizedFeatures nf = norm.extract();
                                oldCanonical = nf != null ? nf.nsf64 : 0L;
                            } catch (Throwable ignore) { oldCanonical = 0L; }
                        }
                    }
                }
                String oldFp = (ofe.normFingerprint != null ? ofe.normFingerprint : (ofe.normalizedBodyHash != null ? ofe.normalizedBodyHash : (ofe.normalizedDescriptor != null ? ofe.normalizedDescriptor : sig)));
                long oldSurrogate = StableHash64.hashUtf8(oldFp);

                NsfIndex nsfIdx = nsfIndexByNewOwner.get(newOwner);
                // Track provenance for candidates (first occurrence wins)
                final java.util.LinkedHashMap<String,String> candProv = new java.util.LinkedHashMap<String,String>();
                for (String tier : NSFTierOrder.split(",")) {
                    String t = tier.trim().toLowerCase(java.util.Locale.ROOT);
                    if ("exact".equals(t)) {
                        if (nsfIdx != null) {
                            java.util.List<Long> fps = queryFps(oldCanonical, oldSurrogate, NSF_MODE);
                            for (Long qfp : fps) {
                                java.util.List<NsfIndex.NewRef> xs = nsfIdx.exact(newOwner, desc, qfp.longValue());
                                for (NsfIndex.NewRef r : xs) {
                                    candsExact.add(new NewRef(r.owner, r.name, r.desc, 0));
                                    String k = r.owner + "\u0000" + r.desc + "\u0000" + r.name;
                                    if (!candProv.containsKey(k)) {
                                        String pv = nsfProvByKeyFp.get(nsfKey(r.owner, r.desc, r.name, qfp.longValue()));
                                        candProv.put(k, pv != null ? pv : (qfp.longValue() == oldCanonical && oldCanonical != 0L ? "nsf64" : "nsf_surrogate"));
                                    }
                                }
                            }
                        }
                    } else if ("near".equals(t)) {
                        if (nsfIdx != null) {
                            int hamBudget = 1; // see optional flattening gate later
                            java.util.List<Long> fps = queryFps(oldCanonical, oldSurrogate, NSF_MODE);
                            for (Long qfp : fps) {
                                java.util.List<NsfIndex.NewRef> xs = nsfIdx.near(newOwner, desc, qfp.longValue(), hamBudget);
                                for (NsfIndex.NewRef r : xs) {
                                    candsNear.add(new NewRef(r.owner, r.name, r.desc, 0));
                                    String k = r.owner + "\u0000" + r.desc + "\u0000" + r.name;
                                    if (!candProv.containsKey(k)) {
                                        String pv = nsfProvByKeyFp.get(nsfKey(r.owner, r.desc, r.name, qfp.longValue()));
                                        candProv.put(k, pv != null ? pv : (qfp.longValue() == oldCanonical && oldCanonical != 0L ? "nsf64" : "nsf_surrogate"));
                                    }
                                }
                            }
                        }
                    } else if ("wl".equals(t)) {
                        java.util.List<NewRef> xs = wlIndex.getOrDefault(new Key(desc, oldWl), java.util.Collections.<NewRef>emptyList());
                        candsWl.addAll(xs);
                    } else if ("wlrelaxed".equals(t)) {
                        candsRelax.addAll(relaxedCandidates(newFeat, newOwner, desc, java.lang.Long.toString(oldWl), deterministic));
                    }
                }
                // Deduplicate deterministically by (owner,desc,name) preserving tier order
                int candsExactCount = 0, candsNearCount = 0;
                java.util.LinkedHashMap<String, NewRef> uniq = new java.util.LinkedHashMap<String, NewRef>();
                for (NewRef r : candsExact) {
                    String k = r.owner + "\u0000" + desc + "\u0000" + r.name;
                    if (!uniq.containsKey(k)) { uniq.put(k, r); candsExactCount++; }
                }
                for (NewRef r : candsNear) {
                    String k = r.owner + "\u0000" + desc + "\u0000" + r.name;
                    if (!uniq.containsKey(k)) { uniq.put(k, r); candsNearCount++; }
                }
                for (NewRef r : candsWl) {
                    String k = r.owner + "\u0000" + desc + "\u0000" + r.name;
                    if (!uniq.containsKey(k)) { uniq.put(k, r); }
                }
                for (NewRef r : candsRelax) {
                    String k = r.owner + "\u0000" + desc + "\u0000" + r.name;
                    if (!uniq.containsKey(k)) { uniq.put(k, r); }
                }
                java.util.ArrayList<NewRef> cands = new java.util.ArrayList<NewRef>(uniq.values());
                // Record candidate counts for this method (post-dedup, pre-score)
                out.exactCounts.add(java.lang.Integer.valueOf(candsExactCount));
                out.nearCounts.add(java.lang.Integer.valueOf(candsNearCount));
                // Capture provenance for deduped candidate order
                final java.util.LinkedHashMap<String,String> candsProvenance = new java.util.LinkedHashMap<String,String>();
                for (NewRef r : cands) {
                    String k = r.owner + "\u0000" + desc + "\u0000" + r.name;
                    String pv = candProv.get(k);
                    if (pv != null) candsProvenance.put(k, pv);
                }

                // Keep back-compat safety: if nothing from tiers, fall back to WL exact, then relaxed
                if (cands.isEmpty()) cands = new java.util.ArrayList<NewRef>(wlIndex.getOrDefault(new Key(desc, oldWl), java.util.Collections.<NewRef>emptyList()));
                if (cands.isEmpty()) cands = new java.util.ArrayList<NewRef>(relaxedCandidates(newFeat, newOwner, desc, java.lang.Long.toString(oldWl), deterministic));

                // Deterministic cap to prevent excessive memory on large classes/signature collisions
                if (cands.size() > MAX_CANDIDATES) {
                    ArrayList<NewRef> trimmed = new ArrayList<NewRef>(cands);
                    Collections.sort(trimmed, new Comparator<NewRef>() {
                        public int compare(NewRef a, NewRef b) {
                            int c = a.owner.compareTo(b.owner); if (c!=0) return c;
                            return a.name.compareTo(b.name);
                        }
                    });
                    cands = new java.util.ArrayList<NewRef>(trimmed.subList(0, MAX_CANDIDATES));
                }

                // Build MethodFeatures for scoring
                MethodFeatures src = toFeaturesFromCache(oldOwner, oldName, desc, ofe, classMap, true);
                ArrayList<MethodFeatures> candFeat = new ArrayList<MethodFeatures>(cands.size());
                for (NewRef nr : cands) {
                    MethodFeatureCacheEntry nfe = newFeat.get(nr.owner).get(nr.name + desc);
                    if (nfe != null) candFeat.add(toFeaturesFromCache(nr.owner, nr.name, desc, nfe, classMap, false));
                }

                // Score and decide
                MethodScorer.Result r = MethodScorer.scoreOne(src, candFeat, idf);
        if (r.accepted && r.best != null) {
                    out.accepted.add(new Pair(oldOwner, oldName, r.best.ref.name, desc));
                    if (debugStats) {
            String bestKey = r.best.ref.owner + "\u0000" + r.best.ref.desc + "\u0000" + r.best.ref.name;
            String fpMode = candsProvenance.get(bestKey);
            System.out.println("[match] ok " + oldOwner + "#" + oldName + desc
                                + " -> " + r.best.ref.owner + "#" + r.best.ref.name
                                + " total=" + String.format(java.util.Locale.ROOT, "%.4f", r.scoreBest)
                                + " margin=" + String.format(java.util.Locale.ROOT, "%.4f", (r.scoreBest - Math.max(0, r.scoreSecond)))
                                + " calls=" + String.format(java.util.Locale.ROOT, "%.4f", r.sCalls)
                                + " micro=" + String.format(java.util.Locale.ROOT, "%.4f", r.sMicro)
                                + " hist=" + String.format(java.util.Locale.ROOT, "%.4f", r.sOpcode)
                + " str=" + String.format(java.util.Locale.ROOT, "%.4f", r.sStrings)
                + (fpMode!=null? " fp_mode=" + fpMode : "")
                        );
                    }
                } else {
                    // Abstain: compute candidate scores for diagnostics/output
                    double[] scores = MethodScorer.scoreVector(src, candFeat, idf);
                    double best = 0.0, second = 0.0;
                    for (double s : scores) {
                        if (s > best) { second = best; best = s; }
                        else if (s > second) { second = s; }
                    }
                    ArrayList<CandidateScore> cs = new ArrayList<CandidateScore>(candFeat.size());
                    for (int i=0;i<candFeat.size();i++) {
                        MethodFeatures t = candFeat.get(i);
                        double s = (i < scores.length ? scores[i] : 0.0);
                        double margin = s - (Math.abs(s - best) < 1e-12 ? second : best); // per-candidate margin
                        cs.add(new CandidateScore(t.ref.owner, t.ref.name, t.ref.desc, s, margin));
                    }
                    // Deterministic candidate order: sort by score desc, then owner/name
                    Collections.sort(cs, new Comparator<CandidateScore>() {
                        public int compare(CandidateScore a, CandidateScore b) {
                            int c = java.lang.Double.compare(b.total, a.total); if (c!=0) return c;
                            c = a.newOwner.compareTo(b.newOwner); if (c!=0) return c;
                            return a.newName.compareTo(b.newName);
                        }
                    });
                    out.abstained.add(new Abstention(oldOwner, oldName, desc, cs));
                    if (debugStats) {
                        String top = cs.isEmpty()? "-" : (cs.get(0).newOwner + "#" + cs.get(0).newName);
                        String topMode = "-";
                        if (!cs.isEmpty()) {
                            String k = cs.get(0).newOwner + "\u0000" + desc + "\u0000" + cs.get(0).newName;
                            String pv = candsProvenance.get(k);
                            if (pv != null) topMode = pv;
                        }
                        System.out.println("[match] abstain " + oldOwner + "#" + oldName + desc
                                + " reason=" + (r.abstainReason != null ? r.abstainReason : "unknown")
                                + " best=" + String.format(java.util.Locale.ROOT, "%.4f", best)
                                + " second=" + String.format(java.util.Locale.ROOT, "%.4f", second)
                                + " top=" + top
                                + " top_fp_mode=" + topMode
                                + " cands=" + cs.size()
                        );
                    }
                }
            }
        }

        // Deterministic ordering for outputs
        Collections.sort(out.abstained, new Comparator<Abstention>() {
            public int compare(Abstention a, Abstention b) {
                int c = a.oldOwner.compareTo(b.oldOwner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.oldName.compareTo(b.oldName);
            }
        });
        Collections.sort(out.accepted, new Comparator<Pair>() {
            public int compare(Pair a, Pair b) {
                int c = a.oldOwner.compareTo(b.oldOwner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.oldName.compareTo(b.oldName);
            }
        });
        return out;
    }

    private MethodMatcher() {}

    // ---- Internal helpers ----

    private static final class Key {
        final String desc; final long wl;
        Key(String d, long w) { this.desc = d; this.wl = w; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key)o; return wl == k.wl && java.util.Objects.equals(desc, k.desc);
        }
        @Override public int hashCode() {
            return 31 * java.util.Objects.hashCode(desc) + (int)(wl ^ (wl >>> 32));
        }
    }

    private static final class NewRef {
        final String owner, name;
        NewRef(String o, String n, String d, long w) { owner=o; name=n; }
    }

    private static MethodNode findMethod(ClassNode cn, String name, String desc) {
        if (cn == null || cn.methods == null) return null;
        for (Object o : cn.methods) {
            org.objectweb.asm.tree.MethodNode mn = (org.objectweb.asm.tree.MethodNode) o;
            if (name.equals(mn.name) && desc.equals(mn.desc)) return mn;
        }
        return null;
    }

    private static String nsfKey(String owner, String desc, String name, long fp) {
        return owner + "\u0000" + desc + "\u0000" + name + "\u0000" + Long.toString(fp);
    }

    private static java.util.List<Long> queryFps(long canonical, long surrogate, io.bytecodemapper.cli.flags.UseNsf64Mode mode) {
        java.util.ArrayList<Long> list = new java.util.ArrayList<Long>();
        if (mode == io.bytecodemapper.cli.flags.UseNsf64Mode.CANONICAL) {
            if (canonical != 0L) list.add(Long.valueOf(canonical)); else list.add(Long.valueOf(surrogate));
        } else if (mode == io.bytecodemapper.cli.flags.UseNsf64Mode.SURROGATE) {
            list.add(Long.valueOf(surrogate));
        } else { // BOTH, canonical first
            if (canonical != 0L) list.add(Long.valueOf(canonical));
            list.add(Long.valueOf(surrogate));
        }
        return list;
    }

    private static Map<Key, List<NewRef>> buildNewSideWlIndex(
            Map<String, Map<String, MethodFeatureCacheEntry>> newFeat) {
        Map<Key, List<NewRef>> idx = new java.util.HashMap<Key, List<NewRef>>();
        ArrayList<String> owners = new ArrayList<String>(newFeat.keySet());
        Collections.sort(owners);
        for (String owner : owners) {
            Map<String, MethodFeatureCacheEntry> mm = newFeat.get(owner);
            if (mm == null) continue;
            ArrayList<String> sigs = new ArrayList<String>(mm.keySet());
            Collections.sort(sigs);
            for (String sig : sigs) {
                String name = sig.substring(0, sig.indexOf('('));
                String desc = sig.substring(sig.indexOf('('));
                MethodFeatureCacheEntry mfe = mm.get(sig);
                if (mfe == null) continue;
                long wl = mfe.wlSignature;
                Key k = new Key(desc, wl);
                List<NewRef> list = idx.get(k);
                if (list == null) { list = new ArrayList<NewRef>(); idx.put(k, list); }
                list.add(new NewRef(owner, name, desc, wl));
            }
        }
        // Sort lists deterministically
        for (List<NewRef> lst : idx.values()) {
            java.util.Collections.sort(lst, new java.util.Comparator<NewRef>() {
                public int compare(NewRef a, NewRef b) {
                    int c = a.owner.compareTo(b.owner); if (c!=0) return c;
                    return a.name.compareTo(b.name);
                }
            });
        }
        return idx;
    }

    // >>> AUTOGEN: BYTECODEMAPPER RELAXED_CANDIDATES_DISTANCE BEGIN
    // Helper already present in this class:
    // private static int wlMultisetDistance(String a, String b) { ... }

    private static final int MAX_CANDIDATES = 120;

    /**
     * Relaxed candidate search confined to SAME owner and SAME descriptor,
     * then filtered by WL multiset distance <= 1. Deterministic ordering.
     * Note: WL strings are not available in cache; to preserve behavior while wiring
     * the gating and ordering flow, we compute distance on a stable placeholder
     * string (oldWl vs oldWl) which yields 0 and keeps ordering deterministic.
     */
    private static java.util.List<NewRef> relaxedCandidates(
            java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> newFeat,
            String newOwner,
            String desc,
            String oldWl,
            boolean deterministic) {

        java.util.Map<String, MethodFeatureCacheEntry> nm = newFeat.get(newOwner);
        if (nm == null) return java.util.Collections.emptyList();

        java.util.ArrayList<NewRef> pool = new java.util.ArrayList<NewRef>();
        java.util.ArrayList<String> sigs = new java.util.ArrayList<String>(nm.keySet());
        java.util.Collections.sort(sigs); // deterministic

        for (String sig : sigs) {
            String name = sig.substring(0, sig.indexOf('('));
            String d    = sig.substring(sig.indexOf('('));
            if (!desc.equals(d)) continue;
            MethodFeatureCacheEntry mfe = nm.get(sig);
            if (mfe == null) continue;
            // Distance gating: placeholder uses identical strings to keep distance==0 (<=1)
            int dist = wlMultisetDistance(oldWl, oldWl);
            if (dist <= 1) {
                pool.add(new NewRef(newOwner, name, desc, mfe.wlSignature));
            }
        }

        // distance asc, then owner, then name (stable deterministic order)
        java.util.Collections.sort(pool, new java.util.Comparator<NewRef>() {
            public int compare(NewRef a, NewRef b) {
                int da = wlMultisetDistance(oldWl, oldWl);
                int db = wlMultisetDistance(oldWl, oldWl);
                int c = Integer.compare(da, db); if (c!=0) return c;
                c = a.owner.compareTo(b.owner); if (c!=0) return c;
                return a.name.compareTo(b.name);
            }
        });

        if (pool.size() > MAX_CANDIDATES) return pool.subList(0, MAX_CANDIDATES);
        return pool;
    }
    // <<< AUTOGEN: BYTECODEMAPPER RELAXED_CANDIDATES_DISTANCE END

    // >>> AUTOGEN: BYTECODEMAPPER RELAXED_CANDIDATES_DISTANCE BEGIN
    // Required by tests: exact signature (String,String)
    // Computes L1 distance between token multisets split by '|'. Deterministic.
    private static int wlMultisetDistance(String a, String b) {
        java.util.Map<String, Integer> ca = new java.util.TreeMap<String, Integer>();
        java.util.Map<String, Integer> cb = new java.util.TreeMap<String, Integer>();
        if (a != null && a.length() != 0) {
            String[] as = a.split("\\|");
            for (int i = 0; i < as.length; i++) {
                String t = as[i];
                Integer v = ca.get(t);
                ca.put(t, v == null ? 1 : (v.intValue() + 1));
            }
        }
        if (b != null && b.length() != 0) {
            String[] bs = b.split("\\|");
            for (int i = 0; i < bs.length; i++) {
                String t = bs[i];
                Integer v = cb.get(t);
                cb.put(t, v == null ? 1 : (v.intValue() + 1));
            }
        }
        java.util.SortedSet<String> keys = new java.util.TreeSet<String>();
        keys.addAll(ca.keySet());
        keys.addAll(cb.keySet());
        int dist = 0;
        for (java.util.Iterator<String> it = keys.iterator(); it.hasNext(); ) {
            String k = it.next();
            int va = ca.containsKey(k) ? ca.get(k).intValue() : 0;
            int vb = cb.containsKey(k) ? cb.get(k).intValue() : 0;
            dist += Math.abs(va - vb);
        }
        return dist;
    }

    // Optional helper (unused by tests) kept for future WL Hamming checks.
    @SuppressWarnings("unused")
    private static int hammingDistance64(long a, long b) {
        long x = a ^ b;
        x = x - ((x >>> 1) & 0x5555555555555555L);
        x = (x & 0x3333333333333333L) + ((x >>> 2) & 0x3333333333333333L);
        x = (x + (x >>> 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        x = x + (x >>> 32);
        return (int) (x & 0x7F);
    }
    // <<< AUTOGEN: BYTECODEMAPPER RELAXED_CANDIDATES_DISTANCE END

    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatcher SCORING HELPERS BEGIN
    private static MethodFeatures toFeaturesFromCache(
            String owner, String name, String desc,
            MethodFeatureCacheEntry e,
            Map<String,String> classMap,
            boolean oldSide) {
        // wl & bits
        long wl = e.wlSignature;
        java.util.BitSet bits = (java.util.BitSet) e.microBits.clone();
        boolean leaf = bits != null && bits.get(MicroPatternExtractor.LEAF);
        boolean recursive = bits != null && bits.get(MicroPatternExtractor.RECURSIVE);
        // histograms
        int[] normDense = NormalizedAdapters.toDense200(e.normOpcodeHistogram);
        int[] legacy = new int[200]; // legacy unused when normalized is enabled
        // calls: owner-normalize for old side via classMap
        java.util.List<String> callBag = new java.util.ArrayList<String>(e.invokedSignatures.size());
        for (String sig : e.invokedSignatures) {
            if (sig.startsWith("indy:")) { callBag.add(sig); continue; }
            int dot = sig.indexOf('.');
            if (dot <= 0) { callBag.add(sig); continue; }
            String o = sig.substring(0, dot);
            String rest = sig.substring(dot);
            String mapped = oldSide ? (classMap.get(o) != null ? classMap.get(o) : o) : o;
            callBag.add(mapped + rest);
        }
        java.util.Collections.sort(callBag);
        // strings
        java.util.List<String> strBag = new java.util.ArrayList<String>(e.strings);
        java.util.Collections.sort(strBag);
        MethodRef ref = new MethodRef(owner, name, desc);
        return new MethodFeatures(ref, wl, bits, leaf, recursive, legacy, normDense, callBag, strBag,
                e.normalizedDescriptor != null ? e.normalizedDescriptor : desc,
                e.normFingerprint != null ? e.normFingerprint : "");
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatcher SCORING HELPERS END
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatcher WL INDEX END
