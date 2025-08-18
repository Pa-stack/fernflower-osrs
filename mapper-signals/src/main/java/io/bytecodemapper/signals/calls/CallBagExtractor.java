// >>> AUTOGEN: BYTECODEMAPPER CallBagExtractor BEGIN
package io.bytecodemapper.signals.calls;

import org.objectweb.asm.tree.*;
import java.util.*;

/** Extracts callee tokens as owner#name:desc, excluding java.* and javax.* owners. */
public final class CallBagExtractor {
    private CallBagExtractor(){}

    /** Owner normalizer stub (identity). Later we will apply class-map here. */
    public interface OwnerNormalizer { String normalize(String ownerInternalName); }

    private static final OwnerNormalizer IDENTITY = new OwnerNormalizer() {
        public String normalize(String o) { return o; }
    };

    /** Extract tokens for a method; owner arg unused here but reserved for future filtering if needed. */
    public static List<String> extract(String ownerInternalName, MethodNode mn) {
        return extract(ownerInternalName, mn, IDENTITY);
    }

    /** Extract with a custom normalizer (e.g., apply class-map after Phase-1). */
    public static List<String> extract(String ownerInternalName, MethodNode mn, OwnerNormalizer normalizer) {
        if (normalizer == null) normalizer = IDENTITY;
        ArrayList<String> tokens = new ArrayList<String>();
        for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode) p;
                String owner = normalizer.normalize(m.owner);
                if (owner == null) owner = m.owner;
                if (owner.startsWith("java/") || owner.startsWith("javax/")) continue;
                tokens.add(owner + "#" + m.name + ":" + m.desc);
            } else if (p instanceof InvokeDynamicInsnNode) {
                // Treat indy as a synthetic owner bucket
                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) p;
                tokens.add("INVOKEDYNAMIC#" + indy.name + ":" + indy.desc);
            }
        }
        return tokens;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CallBagExtractor END
