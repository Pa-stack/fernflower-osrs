// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatcher WL INDEX BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;
import io.bytecodemapper.signals.idf.IdfStore;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

/**
 * Method matching entrypoint (CLI-side adapter) using precomputed feature caches.
 * This Prompt A implementation builds a WL signature index on the NEW side and
 * produces abstentions with deterministic candidate lists. No acceptances yet.
 */
public final class MethodMatcher {

    public static final int WL_K = 4; // iterations used by WL signature (precomputed in cache)

    // Backwards-compatible DTOs (new in Prompt A)
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
    }

    /**
     * Overload that accepts feature caches. Builds WL index on NEW side and, for each OLD
     * method, emits an abstention with deterministic candidates (same desc, equal WL first;
     * if none, relaxed to same desc in mapped owner). Acceptance and scoring are added later.
     */
    public static MethodMatchResult matchMethods(
            Map<String, ClassNode> oldClasses,
            Map<String, ClassNode> newClasses,
            Map<String,String> classMap,
            Map<String, Map<String, MethodFeatureCacheEntry>> oldFeat,
            Map<String, Map<String, MethodFeatureCacheEntry>> newFeat,
            IdfStore idf,
            boolean deterministic) {
        MethodMatchResult out = new MethodMatchResult();

        // 1) Build NEW-side index by (desc, wl)
        Map<Key, List<NewRef>> wlIndex = buildNewSideWlIndex(newFeat);

        // 2) Iterate OLD owners deterministically
        ArrayList<String> owners = new ArrayList<String>(classMap.keySet());
        Collections.sort(owners);
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

                // Primary candidates: exact WL+desc
                List<NewRef> cands = wlIndex.getOrDefault(new Key(desc, oldWl), java.util.Collections.<NewRef>emptyList());

                // Relaxed: same owner and desc
                if (cands.isEmpty()) {
                    cands = relaxedCandidates(nm, newOwner, desc);
                }

                ArrayList<CandidateScore> cs = new ArrayList<CandidateScore>(cands.size());
                for (NewRef nr : cands) cs.add(new CandidateScore(nr.owner, nr.name, desc, 0.0, 0.0));
                // Deterministic candidate order
                Collections.sort(cs, new Comparator<CandidateScore>() {
                    public int compare(CandidateScore a, CandidateScore b) {
                        int c = a.newOwner.compareTo(b.newOwner); if (c!=0) return c;
                        return a.newName.compareTo(b.newName);
                    }
                });
                out.abstained.add(new Abstention(oldOwner, oldName, desc, cs));
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

    private static List<NewRef> relaxedCandidates(
            Map<String, MethodFeatureCacheEntry> nm,
            String newOwner,
            String desc) {
        if (nm == null) return java.util.Collections.emptyList();
        ArrayList<NewRef> out = new ArrayList<NewRef>();
        ArrayList<String> sigs = new ArrayList<String>(nm.keySet());
        Collections.sort(sigs);
        for (String sig : sigs) {
            String name = sig.substring(0, sig.indexOf('('));
            String d = sig.substring(sig.indexOf('('));
            if (!desc.equals(d)) continue;
            MethodFeatureCacheEntry mfe = nm.get(sig);
            if (mfe == null) continue;
            out.add(new NewRef(newOwner, name, desc, mfe.wlSignature));
        }
        return out;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatcher WL INDEX END
