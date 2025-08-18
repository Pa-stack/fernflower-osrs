// >>> AUTOGEN: BYTECODEMAPPER core FingerprintBuilder BEGIN
package io.bytecodemapper.core.fingerprint;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.hash.StableHash64;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Build ClassFingerprint deterministically from a ClassNode. */
public final class FingerprintBuilder {
    private final MicroPatternProvider microProvider;

    public FingerprintBuilder(MicroPatternProvider microProvider) {
        this.microProvider = microProvider;
    }

    public ClassFingerprint build(ClassNode cn) {
        final int[] hist = new int[ClassFingerprint.MICRO_BITS];
        final MethodSigBag bag = new MethodSigBag();

        // Sort methods by name+desc for determinism
        @SuppressWarnings("unchecked")
        List<MethodNode> methods = (List<MethodNode>) (List<?>) cn.methods;
        Collections.sort(methods, new Comparator<MethodNode>() {
            public int compare(MethodNode a, MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });

        for (MethodNode mn : methods) {
            // Skip synthetic bridge methods? Keep them; determinism comes from sort.
            ReducedCFG cfg = ReducedCFG.build(mn);
            Dominators dom = Dominators.compute(cfg);
            BitSet bits = microProvider.extract(cn.name, mn, cfg, dom);
            // Accumulate histogram
            for (int i = 0; i < ClassFingerprint.MICRO_BITS; i++) {
                if (bits.get(i)) hist[i]++;
            }
            // Add WL-like signature: owner + name + desc + param arity
            long sig = wlSignature64(cn.name, mn);
            bag.add(sig);
        }

        int methodCount = cn.methods == null ? 0 : cn.methods.size();
        int fieldCount = cn.fields == null ? 0 : cn.fields.size();
        String superName = cn.superName;
        String[] interfaces = toStringArray(cn.interfaces);

        return new ClassFingerprint(cn.name, hist, bag, methodCount, fieldCount, superName, interfaces);
    }

    private static String[] toStringArray(List<?> list) {
        if (list == null || list.isEmpty()) return new String[0];
        String[] out = new String[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = String.valueOf(list.get(i));
        java.util.Arrays.sort(out);
        return out;
    }

    private static long wlSignature64(String ownerInternalName, MethodNode mn) {
        // Deterministic composition: owner/name/desc + simple shape features
        StringBuilder sb = new StringBuilder();
        sb.append(ownerInternalName).append('#').append(mn.name).append(mn.desc);
        Type mt = Type.getMethodType(mn.desc);
        sb.append('|').append(mt.getArgumentTypes().length).append('|').append(mt.getReturnType().getSort());
        return StableHash64.hashUtf8(sb.toString());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER core FingerprintBuilder END
