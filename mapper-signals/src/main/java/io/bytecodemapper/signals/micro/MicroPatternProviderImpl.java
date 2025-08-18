// >>> AUTOGEN: BYTECODEMAPPER signals MicroPatternProviderImpl BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.fingerprint.MicroPatternProvider;
import org.objectweb.asm.tree.MethodNode;

import java.util.BitSet;

/** Adapter that implements the core MicroPatternProvider using MicroPatternExtractor. */
public final class MicroPatternProviderImpl implements MicroPatternProvider {
    @Override
    public BitSet extract(String ownerInternalName, MethodNode mn, ReducedCFG cfg, Dominators dom) {
        return MicroPatternExtractor.extract(ownerInternalName, mn, cfg, dom);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER signals MicroPatternProviderImpl END
