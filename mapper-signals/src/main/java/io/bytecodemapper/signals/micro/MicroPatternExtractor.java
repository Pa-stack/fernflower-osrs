// >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.dom.Dominators;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.BitSet;

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

    // >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor NULLSAFE OWNER PATCH BEGIN
    /**
     * @deprecated Prefer {@link #extract(String, MethodNode, ReducedCFG, Dominators)} so that
     *             Recursive and SameName are evaluated accurately. When owner is unknown (null),
     *             these two bits will NOT be set.
     */
    @Deprecated
    public static BitSet extract(MethodNode mn, ReducedCFG cfg, Dominators dom) {
        return extract(null, mn, cfg, dom);
    }

    /**
     * Extract 17-bit nano-patterns.
     *
     * @param ownerInternalName internal name (e.g., "pkg/Foo") of the declaring class.
     *                          If null, {@code Recursive} and {@code SameName} are not evaluated
     *                          (left unset) to avoid false positives.
     * @param mn method node
     * @param cfg ReducedCFG (post-normalization)
     * @param dom Dominators for cfg
     */
    public static BitSet extract(String ownerInternalName, MethodNode mn, ReducedCFG cfg, Dominators dom) {
        BitSet bits = new BitSet(LEN);

        // 0 NoParams
        if (Type.getArgumentTypes(mn.desc).length == 0) bits.set(NO_PARAMS);

        // 1 NoReturn
        if (Type.getReturnType(mn.desc).equals(Type.VOID_TYPE)) bits.set(NO_RETURN);

        // 4 Leaf (assume true; clear if any call)
        boolean leaf = true;

        // 9 StraightLine (assume true; clear on any branch/switch)
        boolean straight = true;

        // Evaluate name/owner only if owner is known
        final boolean ownerKnown = ownerInternalName != null;

        boolean recursive = false;
        boolean sameName = false;

        boolean localRead=false, localWrite=false, fieldRead=false, fieldWrite=false;
        boolean arrayCreate=false, arrayRead=false, arrayWrite=false;
        boolean typeManip=false, exceptions=false;
        boolean objectCreate=false;

        for (AbstractInsnNode insn = mn.instructions == null ? null : mn.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            final int op = insn.getOpcode();
            if (op < 0) continue;

            // calls
            if (insn instanceof MethodInsnNode) {
                leaf = false;
                MethodInsnNode m = (MethodInsnNode) insn;
                final boolean isCtor = "<init>".equals(m.name) || "<clinit>".equals(m.name);

                if (ownerKnown) {
                    // Recursive: exact owner+name+desc
                    if (ownerInternalName.equals(m.owner)
                            && m.name.equals(mn.name)
                            && m.desc.equals(mn.desc)) {
                        recursive = true;
                    } else {
                        // SameName: same name; exclude self-call; ignore ctors
                        if (!isCtor && m.name.equals(mn.name)) {
                            if (!(ownerInternalName.equals(m.owner) && m.desc.equals(mn.desc))) {
                                sameName = true;
                            }
                        }
                    }
                }
            } else if (insn instanceof InvokeDynamicInsnNode) {
                leaf = false; // treat indy as a call
            }

            // branches / switches
            if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
                straight = false;
            }

            switch (op) {
                // locals
                case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD: localRead = true; break;
                case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE: localWrite = true; break;

                // fields
                case GETFIELD: case GETSTATIC: fieldRead = true; break;
                case PUTFIELD: case PUTSTATIC: fieldWrite = true; break;

                // arrays
                case NEWARRAY: case ANEWARRAY: case MULTIANEWARRAY: arrayCreate = true; break;
                case IALOAD: case LALOAD: case FALOAD: case DALOAD:
                case AALOAD: case BALOAD: case CALOAD: case SALOAD: arrayRead = true; break;
                case IASTORE: case LASTORE: case FASTORE: case DASTORE:
                case AASTORE: case BASTORE: case CASTORE: case SASTORE: arrayWrite = true; break;

                // type
                case CHECKCAST: case INSTANCEOF: typeManip = true; break;

                // exceptions
                case ATHROW: exceptions = true; break;

                // new object
                case NEW: objectCreate = true; break;

                default: break;
            }
        }

        if (recursive) bits.set(RECURSIVE);
        if (sameName) bits.set(SAME_NAME);
        if (leaf) bits.set(LEAF);
        if (objectCreate) bits.set(OBJECT_CREATOR);
        if (fieldRead) bits.set(FIELD_READER);
        if (fieldWrite) bits.set(FIELD_WRITER);
        if (typeManip) bits.set(TYPE_MANIP);
        if (straight) bits.set(STRAIGHT_LINE);

        // Looping via succ back-edges u->v with dominates(v,u)
        if (cfg != null && dom != null && hasBackEdge(cfg, dom)) bits.set(LOOPING);

        if (exceptions) bits.set(EXCEPTIONS);
        if (localRead) bits.set(LOCAL_READER);
        if (localWrite) bits.set(LOCAL_WRITER);
        if (arrayCreate) bits.set(ARRAY_CREATOR);
        if (arrayRead)   bits.set(ARRAY_READER);
        if (arrayWrite)  bits.set(ARRAY_WRITER);

        return bits;
    }
    // >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor NULLSAFE OWNER PATCH END

    // >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor LOOPING SUCCS PATCH BEGIN
    private static boolean hasBackEdge(ReducedCFG cfg, Dominators dom) {
        for (Block b : cfg.blocks()) {
            final int u = b.id;
            for (int v : b.succs()) {
                if (u == v) continue;
                if (dom.dominates(v, u)) return true;
            }
        }
        return false;
    }
    // >>> AUTOGEN: BYTECODEMAPPER MicroPatternExtractor LOOPING SUCCS PATCH END
}
// <<< AUTOGEN: BYTECODEMAPPER MicroPatternExtractor END
