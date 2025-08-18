// >>> AUTOGEN: BYTECODEMAPPER TEST MicroPatternExtractorTest BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.BitSet;

import static org.junit.Assert.*;

public class MicroPatternExtractorTest implements Opcodes {

    private static MethodNode mn(String name, String desc) {
        return new MethodNode(ACC_PUBLIC | ACC_STATIC, name, desc, null, null);
    }

    private static ReducedCFG cfg(MethodNode mn) {
        return ReducedCFG.build(mn); // already normalizes
    }

    private static Dominators dom(ReducedCFG cfg) { return Dominators.compute(cfg); }

    private static BitSet extract(String owner, MethodNode mn) {
        ReducedCFG g = cfg(mn);
        return MicroPatternExtractor.extract(owner, mn, g, dom(g));
    }

    /** helper: insert a benign call to break Leaf without adding other bits */
    private static void addLeafBreaker(InsnList in) {
        // Call to java/lang/Math: abs(I)I ; pushes const then INVOKESTATIC
        in.add(new InsnNode(ICONST_1));
        in.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
        in.add(new InsnNode(POP)); // pop result
    }

    /** helper: add a branch IFNONNULL over NOP to make StraightLine=false (normalizer won't fold this) */
    private static void addBenignBranch(InsnList in) {
        LabelNode L = new LabelNode();
        in.add(new InsnNode(ACONST_NULL));
        in.add(new JumpInsnNode(IFNONNULL, L));
        in.add(new InsnNode(NOP));
        in.add(L);
    }

    private static void assertOnly(BitSet bs, int bit) {
        for (int i = 0; i < MicroPatternExtractor.LEN; i++) {
            assertEquals("Bit "+i, i==bit, bs.get(i));
        }
    }

    @Test public void bit0_NoParams_only() {
        MethodNode m = mn("m","()I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);    // StraightLine=false
        addLeafBreaker(in);     // Leaf=false
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.NO_PARAMS);
    }

    @Test public void bit1_NoReturn_only() {
        MethodNode m = mn("m","(I)V");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new InsnNode(RETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.NO_RETURN);
    }

    @Test public void bit2_Recursive_only() {
        MethodNode m = mn("rec","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        // recursive self-call (owner/name/desc exact)
        in.add(new InsnNode(ICONST_1));
        in.add(new MethodInsnNode(INVOKESTATIC, "X/Owner", "rec", "(I)I", false));
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.RECURSIVE);
    }

    @Test public void bit3_SameName_only() {
        MethodNode m = mn("foo","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        // Call same name but different owner to avoid recursion; ignore ctors
        in.add(new InsnNode(ICONST_1));
        in.add(new MethodInsnNode(INVOKESTATIC, "Y/Other", "foo", "(I)I", false));
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.SAME_NAME);
    }

    @Test public void bit4_Leaf_only() {
        MethodNode m = mn("leaf","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.LEAF);
    }

    @Test public void bit5_ObjectCreator_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.OBJECT_CREATOR);
    }

    @Test public void bit6_FieldReader_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.FIELD_READER);
    }

    @Test public void bit7_FieldWriter_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new InsnNode(ACONST_NULL));
        in.add(new FieldInsnNode(PUTSTATIC, "Z/Holder", "x", "Ljava/lang/Object;"));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.FIELD_WRITER);
    }

    @Test public void bit8_TypeManipulator_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new InsnNode(ACONST_NULL));
        in.add(new TypeInsnNode(CHECKCAST, "java/lang/Object"));
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.TYPE_MANIP);
    }

    @Test public void bit9_StraightLine_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        // no branch, break Leaf to avoid bit 4
        addLeafBreaker(in);
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.STRAIGHT_LINE);
    }

    @Test public void bit10_Looping_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        // create a back-edge: label L then GOTO L; also break Leaf
        LabelNode L = new LabelNode();
        in.add(L);
        addLeafBreaker(in);
        in.add(new JumpInsnNode(GOTO, L)); // back-edge
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.LOOPING);
    }

    @Test public void bit11_Exceptions_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new InsnNode(ACONST_NULL));
        in.add(new InsnNode(ATHROW));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.EXCEPTIONS);
    }

    @Test public void bit12_LocalReader_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new VarInsnNode(ILOAD, 0)); // read local 0 (static method arg? none) - still ILOAD ok
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.LOCAL_READER);
    }

    @Test public void bit13_LocalWriter_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new InsnNode(ICONST_0));
        in.add(new VarInsnNode(ISTORE, 0)); // write local
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.LOCAL_WRITER);
    }

    @Test public void bit14_ArrayCreator_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        in.add(new IntInsnNode(BIPUSH, 1));
        in.add(new IntInsnNode(NEWARRAY, T_INT)); // int[]
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.ARRAY_CREATOR);
    }

    @Test public void bit15_ArrayReader_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        // use null array ref to avoid setting ArrayCreator
        in.add(new InsnNode(ACONST_NULL));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IALOAD)); // *ALOAD (correct)
        in.add(new InsnNode(POP));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.ARRAY_READER);
    }

    @Test public void bit16_ArrayWriter_only() {
        MethodNode m = mn("m","(I)I");
        InsnList in = m.instructions = new InsnList();
        addBenignBranch(in);
        addLeafBreaker(in);
        // create array, then store element via *ASTORE
        in.add(new InsnNode(ACONST_NULL));
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(ICONST_1));
        in.add(new InsnNode(IASTORE)); // *ASTORE (correct)
        in.add(new InsnNode(ICONST_0));
        in.add(new InsnNode(IRETURN));
        BitSet bs = extract("X/Owner", m);
        assertOnly(bs, MicroPatternExtractor.ARRAY_WRITER);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MicroPatternExtractorTest END
