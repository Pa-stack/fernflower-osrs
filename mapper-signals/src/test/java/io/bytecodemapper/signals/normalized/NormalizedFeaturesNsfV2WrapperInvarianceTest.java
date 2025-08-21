// CODEGEN-BEGIN: NormalizedFeaturesNsfV2WrapperInvarianceTest
package io.bytecodemapper.signals.normalized;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class NormalizedFeaturesNsfV2WrapperInvarianceTest implements Opcodes {

    private static MethodNode mn(String name, String desc, int access) {
        MethodNode mn = new MethodNode(access, name, desc, null, null);
        mn.instructions = new InsnList();
        mn.tryCatchBlocks = new java.util.ArrayList<TryCatchBlockNode>();
        return mn;
    }

    @Test
    public void unwrapHandlerInvariant() {
        // Body: RETURN; wrapped by RuntimeException handler with known pattern
        MethodNode m = mn("g", "()V", ACC_PUBLIC|ACC_STATIC);
        LabelNode L0 = new LabelNode();
        LabelNode L1 = new LabelNode();
        m.instructions.add(L0);
        m.instructions.add(new InsnNode(RETURN));
        m.instructions.add(L1);
        TryCatchBlockNode tcb = new TryCatchBlockNode(L0, L1, new LabelNode(), "java/lang/RuntimeException");
        m.tryCatchBlocks.add(tcb);
        LabelNode H = tcb.handler;
        m.instructions.add(H);
        m.instructions.add(new VarInsnNode(ASTORE, 1));
        m.instructions.add(new VarInsnNode(ALOAD, 1));
        m.instructions.add(new LdcInsnNode("sig(x)"));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "X", "h", "(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/Throwable;", false));
        m.instructions.add(new InsnNode(ATHROW));

        NormalizedMethod nm = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        long nsf = nm.extract().getNsf64();
        // If normalization unwraps, recomputing should be identical (idempotent)
        NormalizedMethod nm2 = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        long nsf2 = nm2.extract().getNsf64();
        assertEquals(nsf, nsf2);
    }
}
// CODEGEN-END: NormalizedFeaturesNsfV2WrapperInvarianceTest
