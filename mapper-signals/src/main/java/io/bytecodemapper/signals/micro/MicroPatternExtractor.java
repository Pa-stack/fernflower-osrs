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
    /** Extract 17-bit micropattern bitset from analysis CFG (post-normalization). */
    public static BitSet extract(MethodNode mn, ReducedCFG cfg, Dominators dom) {
        BitSet bits = new BitSet(MicroPattern.values().length);

        // Signature-based:
        Type mt = Type.getMethodType(mn.desc);
        if (mt.getArgumentTypes().length == 0) bits.set(MicroPattern.NoParams.ordinal());
        if (mt.getReturnType().getSort() == Type.VOID) bits.set(MicroPattern.NoReturn.ordinal());

        // Assume straight-line by default; clear if branch-like opcode encountered
        bits.set(MicroPattern.StraightLine.ordinal());

        // Instruction scan:
        boolean hasInvoke = false;
        AbstractInsnNode insn = mn.instructions == null ? null : mn.instructions.getFirst();
        while (insn != null) {
            int op = insn.getOpcode();
            if (op >= 0) {
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
                            // Recursive: match name+desc against current method
                            if (call.name.equals(mn.name) && call.desc.equals(mn.desc)) {
                                bits.set(MicroPattern.Recursive.ordinal());
                            }
                            // SameName: same name but different owner or desc
                            if (call.name.equals(mn.name) && (!call.desc.equals(mn.desc) || !call.owner.equals(getOwnerInternalName(mn)))) {
                                bits.set(MicroPattern.SameName.ordinal());
                            }
                        }
                        break;

                    // OO
                    case NEW: bits.set(MicroPattern.ObjectCreator.ordinal()); break;
                    case GETFIELD:
                    case GETSTATIC: bits.set(MicroPattern.FieldReader.ordinal()); break;
                    case PUTFIELD:
                    case PUTSTATIC: bits.set(MicroPattern.FieldWriter.ordinal()); break;
                    case CHECKCAST:
                    case INSTANCEOF: bits.set(MicroPattern.TypeManipulator.ordinal()); break;

                    // Control flow markers (for StraightLine; Looping handled via dom back-edges)
                    case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
                    case IFNULL: case IFNONNULL:
                    case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
                    case IF_ACMPEQ: case IF_ACMPNE:
                    case GOTO:
                    case TABLESWITCH:
                    case LOOKUPSWITCH:
                        bits.set(MicroPattern.StraightLine.ordinal(), false); // mark not straight-line
                        break;

                    // Exceptions
                    case ATHROW: bits.set(MicroPattern.Exceptions.ordinal()); break;

                    // Locals
                    case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD:
                        bits.set(MicroPattern.LocalReader.ordinal()); break;
                    case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE:
                        bits.set(MicroPattern.LocalWriter.ordinal()); break;

                    // Arrays
                    case NEWARRAY: case ANEWARRAY: case MULTIANEWARRAY:
                        bits.set(MicroPattern.ArrayCreator.ordinal()); break;
                    case IALOAD: case LALOAD: case FALOAD: case DALOAD:
                    case AALOAD: case BALOAD: case CALOAD: case SALOAD:
                        bits.set(MicroPattern.ArrayReader.ordinal()); break;
                    case IASTORE: case LASTORE: case FASTORE: case DASTORE:
                    case AASTORE: case BASTORE: case CASTORE: case SASTORE:
                        bits.set(MicroPattern.ArrayWriter.ordinal()); break;
                }
            }
            insn = insn.getNext();
        }

        // Leaf if no calls at all:
        if (!hasInvoke) bits.set(MicroPattern.Leaf.ordinal());

        // Looping via dominator back-edge: use cfg+dom (placeholder; false until dom implemented)
        // bits.set(MicroPattern.Looping.ordinal(), hasBackEdge(cfg, dom));

        return bits;
    }

    // MethodNode has no owner; caller should pass owner separately in a richer API.
    private static String getOwnerInternalName(MethodNode mn) {
        return ""; // unknown owner in this stub
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroPatternExtractor END
