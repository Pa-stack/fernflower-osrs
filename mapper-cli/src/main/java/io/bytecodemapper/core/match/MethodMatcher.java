// >>> AUTOGEN: BYTECODEMAPPER CORE MethodMatcher OVERLOAD BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;
import io.bytecodemapper.signals.idf.IdfStore;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/**
 * Adapter facade for method matching that accepts precomputed feature caches.
 * Lives in mapper-cli to avoid introducing a mapper-core -> mapper-cli dependency.
 * Signatures mirror the intended core APIs without breaking existing call sites.
 */
public final class MethodMatcher {

    public static class Pair {
        public final String oldOwner, oldName, newName, desc;
        public Pair(String o, String on, String nn, String d) { this.oldOwner=o; this.oldName=on; this.newName=nn; this.desc=d; }
    }
    public static class MethodMatchResult {
        public final java.util.List<Pair> accepted;
        public MethodMatchResult(java.util.List<Pair> a){ this.accepted=a; }
    }

    /**
     * Overload that accepts feature caches. Current implementation ignores features and
     * maps methods by identical owner+name+desc where owners are already mapped in classMap.
     * Replace with a proper DF/TDF+WL + tie-breakers method matcher when available in core.
     */
    public static MethodMatchResult matchMethods(
            Map<String, ClassNode> oldClasses,
            Map<String, ClassNode> newClasses,
            Map<String,String> classMap,
            Map<String, Map<String, MethodFeatureCacheEntry>> oldFeat,
            Map<String, Map<String, MethodFeatureCacheEntry>> newFeat,
            IdfStore idf,
            boolean deterministic) {
        ArrayList<Pair> out = new ArrayList<Pair>();
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
                if (nm.containsKey(sig)) {
                    String oldName = sig.substring(0, sig.indexOf('('));
                    String desc = sig.substring(sig.indexOf('('));
                    out.add(new Pair(oldOwner, oldName, oldName, desc));
                }
            }
        }
        Collections.sort(out, new Comparator<Pair>() {
            public int compare(Pair a, Pair b) {
                int c = a.oldOwner.compareTo(b.oldOwner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.oldName.compareTo(b.oldName);
            }
        });
        return new MethodMatchResult(out);
    }

    private MethodMatcher() {}
}
// <<< AUTOGEN: BYTECODEMAPPER CORE MethodMatcher OVERLOAD END
