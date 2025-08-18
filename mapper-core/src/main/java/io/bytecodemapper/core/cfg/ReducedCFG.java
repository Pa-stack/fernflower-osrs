// >>> AUTOGEN: BYTECODEMAPPER ReducedCFG BEGIN
package io.bytecodemapper.core.cfg;

import org.objectweb.asm.tree.MethodNode;

/** Minimal placeholder reduced CFG structure (to be implemented). */
public final class ReducedCFG {
    public final MethodNode method;

    public ReducedCFG(MethodNode method) {
        this.method = method;
    }

    /** Build a reduced CFG (merge linear chains, minimal normalization). */
    public static ReducedCFG build(MethodNode mn) {
        return new ReducedCFG(mn);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER ReducedCFG END
