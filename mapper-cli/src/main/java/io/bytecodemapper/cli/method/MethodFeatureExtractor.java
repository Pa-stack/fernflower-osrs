// >>> AUTOGEN: BYTECODEMAPPER CLI MethodFeatureExtractor BEGIN
package io.bytecodemapper.cli.method;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.normalize.Normalizer;
import io.bytecodemapper.core.wl.WLRefinement;
import io.bytecodemapper.signals.micro.MicroPatternExtractor;
import io.bytecodemapper.signals.micro.MicroPatternProviderImpl;
import io.bytecodemapper.signals.calls.CallBagExtractor;
import io.bytecodemapper.signals.opcode.OpcodeFeatures;
import io.bytecodemapper.signals.strings.StringBagExtractor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public final class MethodFeatureExtractor {

    public interface ClassOwnerMapper {
        /** Map an old owner to a new owner (class-map). For new-side methods, return the input unchanged. */
        String mapOldOwnerToNew(String ownerInternalName);
    }

    private final MicroPatternProviderImpl microProvider = new MicroPatternProviderImpl();

    public MethodFeatures extractForOld(ClassNode owner, MethodNode mn, ClassOwnerMapper classMap) {
        return extractInternal(owner, mn, classMap, true);
    }
    public MethodFeatures extractForNew(ClassNode owner, MethodNode mn) {
        return extractInternal(owner, mn, new IdentityMapper(), false);
    }

    private MethodFeatures extractInternal(ClassNode owner, MethodNode mn, ClassOwnerMapper mapper, boolean oldSide) {
        // Normalize -> CFG -> Dominators
        Normalizer.Result norm = Normalizer.normalize(mn, Normalizer.Options.defaults());
        ReducedCFG cfg = ReducedCFG.build(norm.method);
        Dominators dom = Dominators.compute(cfg);

        WLRefinement.MethodSignature ms = WLRefinement.computeSignature(cfg, dom, 4);
        long wl = ms.hash;

        // Micropatterns
        BitSet bits = microProvider.extract(owner.name, norm.method, cfg, dom);
        boolean leaf = bits != null && bits.get(MicroPatternExtractor.LEAF);
        boolean recursive = bits != null && bits.get(MicroPatternExtractor.RECURSIVE);

        // Opcode histogram
        int[] hist = OpcodeFeatures.opcodeHistogram(norm.method);

        // Calls (owner-normalized to new-space)
        CallBagExtractor.OwnerNormalizer normalizer = new CallBagExtractor.OwnerNormalizer() {
            public String normalize(String o) {
                if (!oldSide) return o; // already new-space
                String mapped = mapper.mapOldOwnerToNew(o);
                return mapped != null ? mapped : o;
            }
        };
        List<String> callBag = CallBagExtractor.extract(owner.name, norm.method, normalizer);

        // Strings
        List<String> strBag = StringBagExtractor.extract(norm.method);

        MethodRef ref = new MethodRef(owner.name, mn.name, mn.desc);
        return new MethodFeatures(ref, wl, bits, leaf, recursive, hist, callBag, strBag);
    }

    private static final class IdentityMapper implements ClassOwnerMapper {
        public String mapOldOwnerToNew(String ownerInternalName) { return ownerInternalName; }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodFeatureExtractor END
