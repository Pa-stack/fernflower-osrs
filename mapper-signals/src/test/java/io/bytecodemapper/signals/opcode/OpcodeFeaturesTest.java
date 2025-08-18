// >>> AUTOGEN: BYTECODEMAPPER TEST OpcodeFeaturesTest BEGIN
package io.bytecodemapper.signals.opcode;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class OpcodeFeaturesTest implements Opcodes {

    private static MethodNode seqA() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()I", null, null);
        InsnList in = m.instructions = new InsnList();
        // ILOAD 0 ; ILOAD 1 ; IADD ; IRETURN
        in.add(new VarInsnNode(ILOAD, 0));
        in.add(new VarInsnNode(ILOAD, 1));
        in.add(new InsnNode(IADD));
        in.add(new InsnNode(IRETURN));
        return m;
    }

    private static MethodNode seqB_permutation() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()I", null, null);
        InsnList in = m.instructions = new InsnList();
    // Same multiset of opcodes but different order: ILOAD 0 ; IADD ; ILOAD 1 ; IRETURN
    // This changes opcode bigrams while keeping histogram identical.
    in.add(new VarInsnNode(ILOAD, 0));
    in.add(new InsnNode(IADD));
    in.add(new VarInsnNode(ILOAD, 1));
    in.add(new InsnNode(IRETURN));
        return m;
    }

    @Test
    public void histogramPermutationInvariant() {
        int[] h1 = OpcodeFeatures.opcodeHistogram(seqA());
        int[] h2 = OpcodeFeatures.opcodeHistogram(seqB_permutation());
        double sim = OpcodeFeatures.cosineHistogram(h1, h2);
        assertEquals(1.0, sim, 1e-9);
    }

    // >>> AUTOGEN: BYTECODEMAPPER TEST OpcodeFeaturesTest POLISH BEGIN
    @Test
    public void ngramCapturesOrderChanges() {
        // seqA: ILOAD, ILOAD, IADD, IRETURN
        // seqB_permutation: ILOAD, IADD, ILOAD, IRETURN
        // Intention: SAME histogram, DIFFERENT bigrams.

        int[] h1 = OpcodeFeatures.opcodeHistogram(seqA());
        int[] h2 = OpcodeFeatures.opcodeHistogram(seqB_permutation());
        // Histogram should be identical (cosine == 1.0) even though order changed.
        assertEquals(1.0, OpcodeFeatures.cosineHistogram(h1, h2), 1e-9);

        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap g1 = OpcodeFeatures.opcodeNGram(seqA(), 2);
        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap g2 = OpcodeFeatures.opcodeNGram(seqB_permutation(), 2);
        double sim2 = OpcodeFeatures.cosineNGram(g1, g2);
        assertTrue("order-sensitive similarity should drop below 1.0", sim2 < 1.0);
    }
    // <<< AUTOGEN: BYTECODEMAPPER TEST OpcodeFeaturesTest POLISH END
}
// <<< AUTOGEN: BYTECODEMAPPER TEST OpcodeFeaturesTest END
