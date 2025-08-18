// >>> AUTOGEN: BYTECODEMAPPER DF BEGIN
package io.bytecodemapper.core.df;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Dominance frontier + transitive DF (TDF) scaffolding. */
public final class DF {
    public static Map<Integer, Set<Integer>> compute(ReducedCFG cfg, Dominators dom) {
        // TODO: compute DF
        return Collections.emptyMap();
    }

    public static Map<Integer, Set<Integer>> iterateToFixpoint(Map<Integer, Set<Integer>> df) {
        // TODO: compute TDF (IDF)
        return df;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER DF END
