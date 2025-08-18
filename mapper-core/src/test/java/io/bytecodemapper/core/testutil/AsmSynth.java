// >>> AUTOGEN: BYTECODEMAPPER TEST AsmSynth BEGIN
package io.bytecodemapper.core.testutil;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class AsmSynth implements Opcodes {
    private AsmSynth(){}

    private static MethodNode mnInit() {
        MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_STATIC, "m", "()V", null, null);
        mn.instructions = new InsnList();
        return mn;
    }

    public static MethodNode singleBlock() {
        MethodNode mn = mnInit();
        mn.instructions.add(new InsnNode(NOP));
        mn.instructions.add(new InsnNode(RETURN));
        return mn;
    }

    // Two NOPs then RETURN (order A)
    public static MethodNode singleBlockTwoNopsA() {
        MethodNode mn = mnInit();
        mn.instructions.add(new InsnNode(NOP));
        mn.instructions.add(new InsnNode(NOP));
        mn.instructions.add(new InsnNode(RETURN));
        return mn;
    }

    // Same block, reorder NOPs (effectively identical CFG)
    public static MethodNode singleBlockTwoNopsB() {
        MethodNode mn = mnInit();
        mn.instructions.add(new InsnNode(NOP));
        // Insert a NOP, then another NOP (order differs but still contiguous)
        mn.instructions.add(new InsnNode(NOP));
        mn.instructions.add(new InsnNode(RETURN));
        return mn;
    }

    // entry -> (IFNE) -> then/else -> join -> return
    public static MethodNode diamond() {
        MethodNode mn = mnInit();
        InsnList insn = mn.instructions;
        LabelNode Lthen = new LabelNode();
        LabelNode Ljoin = new LabelNode();

        insn.add(new InsnNode(ICONST_0));           // push 0
        insn.add(new JumpInsnNode(IFNE, Lthen));    // never true; but structural
        // else:
        insn.add(new InsnNode(NOP));
        insn.add(new JumpInsnNode(GOTO, Ljoin));
        // then:
        insn.add(Lthen);
        insn.add(new InsnNode(NOP));
        // join:
        insn.add(Ljoin);
        insn.add(new InsnNode(RETURN));
        return mn;
    }

    // diamond variant: same CFG, different constant (1 instead of 0)
    public static MethodNode diamondConst1() {
        MethodNode mn = mnInit();
        InsnList insn = mn.instructions;
        LabelNode Lthen = new LabelNode();
        LabelNode Ljoin = new LabelNode();

        insn.add(new InsnNode(ICONST_1));           // push 1
        insn.add(new JumpInsnNode(IFNE, Lthen));    // true at runtime, but CFG identical
        // else:
        insn.add(new InsnNode(NOP));
        insn.add(new JumpInsnNode(GOTO, Ljoin));
        // then:
        insn.add(Lthen);
        insn.add(new InsnNode(NOP));
        // join:
        insn.add(Ljoin);
        insn.add(new InsnNode(RETURN));
        return mn;
    }

    // while loop: L: (IFNE back to L)
    public static MethodNode loopSimple() {
        MethodNode mn = mnInit();
        InsnList insn = mn.instructions;
        LabelNode L = new LabelNode();
        LabelNode Lend = new LabelNode();

        insn.add(L);
        insn.add(new InsnNode(ICONST_1));
        insn.add(new JumpInsnNode(IFEQ, Lend));
        // body
        insn.add(new InsnNode(NOP));
        // back-edge
        insn.add(new JumpInsnNode(GOTO, L));
        insn.add(Lend);
        insn.add(new InsnNode(RETURN));
        return mn;
    }

    // nested loops: outer with inner back-edge
    public static MethodNode loopNested() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode A = new LabelNode(); // outer head
        LabelNode B = new LabelNode(); // inner head
        LabelNode C = new LabelNode(); // after inner
        LabelNode D = new LabelNode(); // after outer

        in.add(A);
        in.add(new InsnNode(ICONST_1));
        in.add(new JumpInsnNode(IFEQ, D)); // exit outer
        // inner
        in.add(B);
        in.add(new InsnNode(ICONST_1));
        in.add(new JumpInsnNode(IFEQ, C)); // exit inner
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, B)); // inner back-edge
        in.add(C);
        in.add(new JumpInsnNode(GOTO, A)); // outer back-edge
        in.add(D);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    // try-catch: throw → handler
    public static MethodNode tryCatch() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode Lstart = new LabelNode();
        LabelNode Lend = new LabelNode();
        LabelNode Lhandler = new LabelNode();

        mn.tryCatchBlocks.add(new TryCatchBlockNode(Lstart, Lend, Lhandler, "java/lang/Exception"));
        in.add(Lstart);
        // ATHROW path
        in.add(new TypeInsnNode(NEW, "java/lang/Exception"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false));
        in.add(new InsnNode(ATHROW));
        in.add(Lend);
        // handler:
        in.add(Lhandler);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    // tableswitch with default and 2 cases
    public static MethodNode tableSwitch() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode L0 = new LabelNode();
        LabelNode L1 = new LabelNode();
        LabelNode Ldef = new LabelNode();
        LabelNode Lend = new LabelNode();

        in.add(new InsnNode(ICONST_1));
        in.add(new TableSwitchInsnNode(0, 1, Ldef, new LabelNode[]{L0, L1}));
        in.add(L0);
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, Lend));
        in.add(L1);
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, Lend));
        in.add(Ldef);
        in.add(new InsnNode(NOP));
        in.add(Lend);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    // Non-folding variants to survive Normalizer's constant-folding

    // diamond: use IF_ACMPNE with two nulls (not folded by Normalizer)
    public static MethodNode diamondNoFold() {
        MethodNode mn = mnInit();
        InsnList insn = mn.instructions;
        LabelNode Lthen = new LabelNode();
        LabelNode Ljoin = new LabelNode();

        insn.add(new InsnNode(ACONST_NULL));
        insn.add(new InsnNode(ACONST_NULL));
        insn.add(new JumpInsnNode(IF_ACMPNE, Lthen));
        // else
        insn.add(new InsnNode(NOP));
        insn.add(new JumpInsnNode(GOTO, Ljoin));
        // then
        insn.add(Lthen);
        insn.add(new InsnNode(NOP));
        // join
        insn.add(Ljoin);
        insn.add(new InsnNode(RETURN));
        return mn;
    }

    // simple loop: header with IF_ACMPEQ (two different objects) so not folded; back-edge via GOTO
    public static MethodNode loopSimpleNoFold() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode L = new LabelNode();
        LabelNode Lend = new LabelNode();

        in.add(L);
        // push two distinct objects
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        // if equal, exit (never equal), so fallthrough into body
        in.add(new JumpInsnNode(IF_ACMPEQ, Lend));
        // body
        in.add(new InsnNode(NOP));
        // back-edge
        in.add(new JumpInsnNode(GOTO, L));
        in.add(Lend);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    // nested loops: both conditions use IF_ACMPEQ with distinct objects to avoid folding
    public static MethodNode loopNestedNoFold() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode A = new LabelNode(); // outer head
        LabelNode B = new LabelNode(); // inner head
        LabelNode C = new LabelNode(); // after inner
        LabelNode D = new LabelNode(); // after outer

        in.add(A);
        // outer condition: two distinct objects
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new JumpInsnNode(IF_ACMPEQ, D)); // exit outer if equal (never)

        // inner
        in.add(B);
        // inner condition: two distinct objects
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new TypeInsnNode(NEW, "java/lang/Object"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        in.add(new JumpInsnNode(IF_ACMPEQ, C)); // exit inner if equal (never)
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, B)); // inner back-edge
        in.add(C);
        in.add(new JumpInsnNode(GOTO, A)); // outer back-edge
        in.add(D);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    // table switch with non-constant producer: use ARRAYLENGTH of a new array so Normalizer won't fold
    public static MethodNode tableSwitchNoFold() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions;
        LabelNode L0 = new LabelNode();
        LabelNode L1 = new LabelNode();
        LabelNode Ldef = new LabelNode();
        LabelNode Lend = new LabelNode();

        // Compute a value via ARRAYLENGTH; still constant, but not recognized by foldConstantSwitches
        in.add(new IntInsnNode(BIPUSH, 1));
        in.add(new IntInsnNode(NEWARRAY, T_INT));
        in.add(new InsnNode(ARRAYLENGTH));
        in.add(new TableSwitchInsnNode(0, 1, Ldef, new LabelNode[]{L0, L1}));
        in.add(L0);
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, Lend));
        in.add(L1);
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, Lend));
        in.add(Ldef);
        in.add(new InsnNode(NOP));
        in.add(Lend);
        in.add(new InsnNode(RETURN));
        return mn;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST AsmSynth END
