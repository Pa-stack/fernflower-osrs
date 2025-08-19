// >>> AUTOGEN: BYTECODEMAPPER CORE ClassMatcher OVERLOAD BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;
import io.bytecodemapper.signals.idf.IdfStore;
import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter facade for class matching that accepts precomputed feature caches.
 * Lives in mapper-cli to avoid introducing a mapper-core -> mapper-cli dependency.
 * Signatures mirror the intended core APIs without breaking existing call sites.
 */
public final class ClassMatcher {

    public static class ClassMatchResult {
        public final java.util.Map<String,String> classMap;
        public ClassMatchResult(java.util.Map<String,String> m){ this.classMap = m; }
    }

    /**
     * Overload that accepts feature caches. Current implementation ignores features and
     * performs a deterministic identity-name intersection mapping as a safe default.
     * Replace with a proper DF/TDF+WL class matcher when available in core.
     */
    public static ClassMatchResult matchClasses(
            java.util.Map<String, ClassNode> oldClasses,
            java.util.Map<String, ClassNode> newClasses,
            java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> oldFeat,
            java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> newFeat,
            IdfStore idf,
            boolean deterministic) {
        // Delegate to name-intersection baseline to keep behavior deterministic and non-breaking.
        return matchClasses(oldClasses, newClasses, idf, deterministic);
    }

    /**
     * Baseline matcher: map classes with identical internal names present in both sets.
     * Deterministic and safe; acts as a fallback until the full matcher is wired.
     */
    public static ClassMatchResult matchClasses(
            java.util.Map<String, ClassNode> oldClasses,
            java.util.Map<String, ClassNode> newClasses,
            IdfStore idf,
            boolean deterministic) {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>(oldClasses.keySet());
        java.util.Collections.sort(names);
        Map<String,String> map = new LinkedHashMap<String,String>();
        for (String n : names) if (newClasses.containsKey(n)) map.put(n, n);
        return new ClassMatchResult(map);
    }

    private ClassMatcher() {}
}
// <<< AUTOGEN: BYTECODEMAPPER CORE ClassMatcher OVERLOAD END
