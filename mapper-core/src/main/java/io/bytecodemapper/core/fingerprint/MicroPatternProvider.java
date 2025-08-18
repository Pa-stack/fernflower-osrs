// >>> AUTOGEN: BYTECODEMAPPER core MicroPatternProvider BEGIN
package io.bytecodemapper.core.fingerprint;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import org.objectweb.asm.tree.MethodNode;

import java.util.BitSet;

/**
 * Core-side interface to obtain a 17-bit micropattern vector for a method.
 * Implemented in :mapper-signals to avoid a circular dependency.
 */
public interface MicroPatternProvider {
    /**
     * @param ownerInternalName internal class name, e.g. "pkg/Foo"
     * @param mn                method node (post-normalization semantics apply)
     * @param cfg               ReducedCFG built from the normalized method
     * @param dom               Dominators computed for cfg
     * @return 17-bit BitSet (frozen ABI order)
     */
    BitSet extract(String ownerInternalName, MethodNode mn, ReducedCFG cfg, Dominators dom);
}
// <<< AUTOGEN: BYTECODEMAPPER core MicroPatternProvider END
