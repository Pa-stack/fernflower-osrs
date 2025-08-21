// CODEGEN-BEGIN: NormalizedFeaturesNsfV2StabilityTest
package io.bytecodemapper.signals.normalized;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class NormalizedFeaturesNsfV2StabilityTest implements Opcodes {

    private static MethodNode mn(String name, String desc, int access) {
        MethodNode mn = new MethodNode(access, name, desc, null, null);
        mn.instructions = new InsnList();
        mn.tryCatchBlocks = new java.util.ArrayList<TryCatchBlockNode>();
        return mn;
    }

    @Test
    public void ldcReorderStable() {
        // m1: LDC "a", LDC 42, RETURN
        MethodNode m1 = mn("f", "()V", ACC_PUBLIC|ACC_STATIC);
        m1.instructions.add(new LdcInsnNode("a"));
        m1.instructions.add(new LdcInsnNode(Integer.valueOf(42)));
        m1.instructions.add(new InsnNode(RETURN));

        // m2: same literals, different order
        MethodNode m2 = mn("f", "()V", ACC_PUBLIC|ACC_STATIC);
        m2.instructions.add(new LdcInsnNode(Integer.valueOf(42)));
        m2.instructions.add(new LdcInsnNode("a"));
        m2.instructions.add(new InsnNode(RETURN));

        NormalizedMethod n1 = new NormalizedMethod("A", m1, java.util.Collections.<Integer>emptySet());
        NormalizedMethod n2 = new NormalizedMethod("A", m2, java.util.Collections.<Integer>emptySet());

        long h1 = n1.extract().getNsf64();
        long h2 = n2.extract().getNsf64();
        assertEquals("NSFv2 must be stable to LDC order", h1, h2);
    }
}
// CODEGEN-END: NormalizedFeaturesNsfV2StabilityTest
