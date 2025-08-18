// >>> AUTOGEN: BYTECODEMAPPER Dominators BEGIN
package io.bytecodemapper.core.dom;

import io.bytecodemapper.core.cfg.ReducedCFG;

/** Placeholder dominator tree API for integration; implement later. */
public final class Dominators {
    private final ReducedCFG cfg;

    public Dominators(ReducedCFG cfg) {
        this.cfg = cfg;
    }

    public static Dominators compute(ReducedCFG cfg) {
        return new Dominators(cfg);
    }

    /** Return true if v dominates u (loop back-edge test uses this). */
    public boolean dominates(int v, int u) {
        // TODO: implement and back by idom tree
        return false;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER Dominators END
