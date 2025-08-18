// >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.BitSet;
import java.util.List;

public final class MicroPatternExtractor implements Opcodes {
    // Expose frozen 17-bit order as public constants for tests and consumers
    public static final int NO_PARAMS      = 0;
    public static final int NO_RETURN      = 1;
    public static final int RECURSIVE      = 2;
    public static final int SAME_NAME      = 3;
    public static final int LEAF           = 4;
    public static final int OBJECT_CREATOR = 5;
    public static final int FIELD_READER   = 6;
    public static final int FIELD_WRITER   = 7;
    public static final int TYPE_MANIP     = 8;
    public static final int STRAIGHT_LINE  = 9;
    public static final int LOOPING        = 10;
    public static final int EXCEPTIONS     = 11;
    public static final int LOCAL_READER   = 12;
    public static final int LOCAL_WRITER   = 13;
    public static final int ARRAY_CREATOR  = 14;
    public static final int ARRAY_READER   = 15;
    public static final int ARRAY_WRITER   = 16;
    public static final int LEN            = 17;

    /** Back-compat overload (owner unknown). Prefer the owner-aware overload. */
    public static BitSet extract(MethodNode mn, ReducedCFG cfg, Dominators dom) {
        return extract(null, mn, cfg, dom);
    }

    /** Extract 17-bit micropattern bitset from analysis CFG (post-normalization). */
    public static BitSet extract(String ownerInternalName, MethodNode mn, ReducedCFG cfg, Dominators dom) {
        BitSet bits = new BitSet(LEN);

        // Signature-based
        Type mt = Type.getMethodType(mn.desc);
        if (mt.getArgumentTypes().length == 0) bits.set(NO_PARAMS);
        if (mt.getReturnType().getSort() == Type.VOID) bits.set(NO_RETURN);

        // Assume straight-line by default; clear if branch-like opcode encountered
        bits.set(STRAIGHT_LINE);

        // Instruction scan
        boolean hasInvoke = false;
        for (AbstractInsnNode insn = mn.instructions == null ? null : mn.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op < 0) continue;
            switch (op) {
                // Calls
                case INVOKEVIRTUAL:
                case INVOKESTATIC:
                case INVOKESPECIAL:
                case INVOKEINTERFACE:
                case INVOKEDYNAMIC:
                    hasInvoke = true;
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        // Recursive: same owner+name+desc
                        if (ownerInternalName != null && ownerInternalName.length() > 0
                                && call.owner.equals(ownerInternalName)
                                && call.name.equals(mn.name)
                                && call.desc.equals(mn.desc)) {
                            bits.set(RECURSIVE);
                        }
                        // SameName: same name but different owner (and not ctor) or different desc
                        if (call.name.equals(mn.name) && !"<init>".equals(call.name)) {
                            boolean differentOwner = ownerInternalName == null || !call.owner.equals(ownerInternalName);
                            boolean differentDesc = !call.desc.equals(mn.desc);
                            if (differentOwner || differentDesc) bits.set(SAME_NAME);
                        }
                    }
                    break;

                // OO
                case NEW: bits.set(OBJECT_CREATOR); break;
                case GETFIELD:
                case GETSTATIC: bits.set(FIELD_READER); break;
                case PUTFIELD:
                case PUTSTATIC: bits.set(FIELD_WRITER); break;
                case CHECKCAST:
                case INSTANCEOF: bits.set(TYPE_MANIP); break;

                // Control flow markers (for StraightLine; Looping handled via dom back-edges)
                case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
                case IFNULL: case IFNONNULL:
                case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
                case IF_ACMPEQ: case IF_ACMPNE:
                case GOTO:
                case TABLESWITCH:
                case LOOKUPSWITCH:
                    bits.set(STRAIGHT_LINE, false);
                    break;

                // Exceptions
                case ATHROW: bits.set(EXCEPTIONS); break;

                // Locals
                case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
                    bits.set(LOCAL_READER); break;
                case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
                    bits.set(LOCAL_WRITER); break;

                // Arrays
                case NEWARRAY: case ANEWARRAY: case MULTIANEWARRAY:
                    bits.set(ARRAY_CREATOR); break;
                case IALOAD: case LALOAD: case FALOAD: case DALOAD:
                case AALOAD: case BALOAD: case CALOAD: case SALOAD:
                    bits.set(ARRAY_READER); break;
                case IASTORE: case LASTORE: case FASTORE: case DASTORE:
                case AASTORE: case BASTORE: case CASTORE: case SASTORE:
                    bits.set(ARRAY_WRITER); break;
            }
        }

        // Leaf if no calls at all (treat invokedynamic as a call)
        if (!hasInvoke) bits.set(LEAF);

        // Looping via dominator back-edge: edge u->v where v dominates u
        if (cfg != null && dom != null) {
            boolean loop = hasBackEdge(cfg, dom);
            if (loop) bits.set(LOOPING);
        }

        return bits;
    }

    private static boolean hasBackEdge(ReducedCFG cfg, Dominators dom) {
        for (ReducedCFG.Block b : cfg.blocks()) {
            int u = b.id;
            int[] succ = b.succs();
            for (int v : succ) {
                if (dom.dominates(v, u)) return true;
            }
        }
        return false;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroPatternExtractor END
