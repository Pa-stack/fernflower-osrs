// >>> AUTOGEN: BYTECODEMAPPER TEST NormalizedMethodTest BEGIN
package io.bytecodemapper.signals.normalized;

import org.junit.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

import static org.junit.Assert.*;

public class NormalizedMethodTest implements Opcodes {

    private static MethodNode mn(String name, String desc, int access) {
        MethodNode mn = new MethodNode(access, name, desc, null, null);
        mn.instructions = new InsnList();
        mn.tryCatchBlocks = new java.util.ArrayList<TryCatchBlockNode>();
        return mn;
    }

    @Test
    public void fingerprintDeterministic() {
        MethodNode m = mn("f", "()V", ACC_PUBLIC|ACC_STATIC);
        m.instructions.add(new LdcInsnNode("hello"));
        m.instructions.add(new InsnNode(RETURN));

        NormalizedMethod n1 = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        NormalizedMethod n2 = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());

        assertEquals(n1.fingerprint, n2.fingerprint);
        assertEquals("()V", n1.normalizedDescriptor);
        assertTrue(n1.invokedSignatures.isEmpty());
        assertTrue(n1.opcodeHistogram.size() > 0);
    }

    @Test
    public void unwrapsRuntimeExceptionWrapperWhenWholeBody() {
        MethodNode m = mn("g", "()V", ACC_PUBLIC|ACC_STATIC);

        LabelNode L0 = new LabelNode();
        LabelNode L1 = new LabelNode();
        m.instructions.add(L0);
        m.instructions.add(new InsnNode(RETURN));
        m.instructions.add(L1);
        // try/catch RuntimeException with typical obfuscation handler:
        TryCatchBlockNode tcb = new TryCatchBlockNode(L0, L1, new LabelNode(), "java/lang/RuntimeException");
        m.tryCatchBlocks.add(tcb);
        // handler: [ASTORE e], ALOAD e, LDC "sig(x)", INVOKESTATIC helper, ATHROW
        LabelNode H = tcb.handler;
        m.instructions.add(H);
        m.instructions.add(new VarInsnNode(ASTORE, 1));
        m.instructions.add(new VarInsnNode(ALOAD, 1));
        m.instructions.add(new LdcInsnNode("sig(x)"));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "X", "h", "(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/Throwable;", false));
        m.instructions.add(new InsnNode(ATHROW));

        NormalizedMethod n = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        // no wrapper strings should leak; opcode histogram should count just RETURN path
        assertFalse(n.stringConstants.contains("sig(x)"));
        assertTrue(n.opcodeHistogram.containsKey(RETURN));
        assertFalse(n.opcodeHistogram.containsKey(ATHROW)); // handler excluded
    }

    @Test
    public void opaqueGuardExclusionLowersHistogram() {
        // param0 opaque int; ILOAD 0; ICONST_1; IF_ICMPEQ Lret; RETURN; Lret: RETURN
        MethodNode m = mn("h", "(I)V", ACC_PUBLIC);
        LabelNode Lret = new LabelNode();
        m.instructions.add(new VarInsnNode(ILOAD, 1)); // param0 (non-static: local 1)
        m.instructions.add(new InsnNode(ICONST_1));
        m.instructions.add(new JumpInsnNode(IF_ICMPEQ, Lret));
        m.instructions.add(new InsnNode(RETURN));
        m.instructions.add(Lret);
        m.instructions.add(new InsnNode(RETURN));

        NormalizedMethod n0 = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        NormalizedMethod n1 = new NormalizedMethod("A", m, new java.util.LinkedHashSet<Integer>(java.util.Arrays.asList(new Integer[]{Integer.valueOf(0)})));

        int sum0 = sum(n0.opcodeHistogram);
        int sum1 = sum(n1.opcodeHistogram);
        assertTrue("excluding guard should reduce opcode mass", sum1 < sum0);
    }

    @Test
    public void ownerPlumbingInInvokedSignatures() {
        MethodNode m = mn("q", "()V", ACC_PUBLIC|ACC_STATIC);
        m.instructions.add(new MethodInsnNode(INVOKESTATIC, "B", "foo", "()V", false));
        m.instructions.add(new InvokeDynamicInsnNode("bar", "()V", new Handle(H_INVOKESTATIC, "C", "bootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false)));
        m.instructions.add(new InsnNode(RETURN));

        NormalizedMethod n = new NormalizedMethod("A", m, java.util.Collections.<Integer>emptySet());
        assertTrue(n.invokedSignatures.contains("B.foo()V"));
        assertTrue(n.invokedSignatures.contains("indy:bar()V"));
    }

    private static int sum(Map<Integer,Integer> m){
        int s=0; for (Integer v : m.values()) s+= v.intValue(); return s;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST NormalizedMethodTest END
