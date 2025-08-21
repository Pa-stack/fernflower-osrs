// CODEGEN-BEGIN: NormalizedFeaturesNsfV2SensitivityTest
package io.bytecodemapper.signals.normalized;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class NormalizedFeaturesNsfV2SensitivityTest implements Opcodes {

    private static MethodNode mn(String name, String desc, int access) {
        MethodNode mn = new MethodNode(access, name, desc, null, null);
        mn.instructions = new InsnList();
        mn.tryCatchBlocks = new java.util.ArrayList<TryCatchBlockNode>();
        return mn;
    }

    @Test
    public void opcodePerturbationChangesNsf() {
        // IADD vs ISUB should alter opcode set and stack deltas minimally, changing NSFv2
        MethodNode a = mn("f", "()I", ACC_PUBLIC|ACC_STATIC);
        a.instructions.add(new InsnNode(ICONST_2));
        a.instructions.add(new InsnNode(ICONST_3));
        a.instructions.add(new InsnNode(IADD));
        a.instructions.add(new InsnNode(IRETURN));

        MethodNode b = mn("f", "()I", ACC_PUBLIC|ACC_STATIC);
        b.instructions.add(new InsnNode(ICONST_2));
        b.instructions.add(new InsnNode(ICONST_3));
        b.instructions.add(new InsnNode(ISUB));
        b.instructions.add(new InsnNode(IRETURN));

        long h1 = new NormalizedMethod("A", a, java.util.Collections.<Integer>emptySet()).extract().getNsf64();
        long h2 = new NormalizedMethod("A", b, java.util.Collections.<Integer>emptySet()).extract().getNsf64();
        assertNotEquals("NSFv2 should be sensitive to opcode change", h1, h2);
    }
}
// CODEGEN-END: NormalizedFeaturesNsfV2SensitivityTest
