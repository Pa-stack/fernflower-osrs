// >>> AUTOGEN: BYTECODEMAPPER Normalizer BEGIN
package io.bytecodemapper.core.normalize;

import org.objectweb.asm.tree.MethodNode;

/** Minimal normalization placeholder; extend to peel opaque predicates, flattening, etc. */
public final class Normalizer {
    private Normalizer(){}

    public static MethodNode normalize(MethodNode mn) {
        // TODO: remove trivial wrappers, opaque branches as per policy
        return mn;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER Normalizer END
