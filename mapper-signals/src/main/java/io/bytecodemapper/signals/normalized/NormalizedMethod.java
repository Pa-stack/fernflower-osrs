// >>> AUTOGEN: BYTECODEMAPPER signals NormalizedMethod BEGIN
package io.bytecodemapper.signals.normalized;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * NormalizedMethod: normalized, bytecode-only features for robust matching.
 * Inputs are expected to be POST-NORMALIZATION (analysis CFG alignment).
 *
 * Features:
 *  - opcodeHistogram: counts of opcodes after excluding wrapper/opaque blocks
 *  - stringConstants: string LDCs, excluding obfuscation wrapper signature strings
 *  - invokedSignatures: owner.name+desc for INVOKExxx; "indy:name desc" for INVOKEDYNAMIC
 *  - normalizedDescriptor: descriptor optionally filtered by opaque-parameter policy (stub OK)
 *  - fingerprint: SHA-256 over normalizedDescriptor + sorted opcode set + invoked + strings
 *
 * Owner must be plumbed explicitly (MethodNode doesn't carry it).
 */
public final class NormalizedMethod implements Opcodes {

    public static final class MethodKey {
        public final String ownerInternalName;
        public final String name;
        public final String desc;
        public MethodKey(String ownerInternalName, String name, String desc) {
            this.ownerInternalName = ownerInternalName;
            this.name = name;
            this.desc = desc;
        }
        @Override public String toString(){ return ownerInternalName + "#" + name + desc; }
    }

    public final MethodKey key;
    public final Set<String> stringConstants = new LinkedHashSet<String>();
    public final Map<Integer, Integer> opcodeHistogram = new LinkedHashMap<Integer, Integer>();
    public final Set<String> invokedSignatures = new LinkedHashSet<String>();
    public final String normalizedDescriptor;
    public final String fingerprint;

    /**
     * Build a normalized feature view for the given (already normalized) MethodNode.
     * @param ownerInternalName class internal name
     * @param normalizedMethod  method node, after Normalizer
     * @param opaqueParamIndexes indexes of params considered opaque (stub OK: pass empty)
     */
    public NormalizedMethod(String ownerInternalName, MethodNode normalizedMethod, Set<Integer> opaqueParamIndexes) {
        if (opaqueParamIndexes == null) opaqueParamIndexes = java.util.Collections.<Integer>emptySet();
        this.key = new MethodKey(ownerInternalName, normalizedMethod.name, normalizedMethod.desc);

        // Work on a shallow clone so exclusion rewrites do not mutate upstream nodes
        MethodNode working = cloneShallow(normalizedMethod);

        // 1) Attempt to unwrap whole-method RuntimeException wrapper
        InsnList unwrapped = tryUnwrapWholeRuntimeExceptionWrapper(working);
        boolean didUnwrap = false;
        Set<String> wrapperNoisyStrings = java.util.Collections.<String>emptySet();
        if (unwrapped != null) {
            working.instructions = unwrapped;
            working.tryCatchBlocks = new ArrayList<TryCatchBlockNode>(); // drop handlers
            didUnwrap = true;
        } else {
            // 2) If not unwrapped, collect wrapper signature strings to exclude from features
            wrapperNoisyStrings = collectWrapperSignatureStrings(working);
        }

        // 3) Normalized descriptor (may drop opaque params; stub is OK)
        this.normalizedDescriptor = normalizeDescriptor(working.desc, opaqueParamIndexes);

        // 4) Determine instruction exclusions: opaque guards + (if not unwrapped) wrapper handler code
        Set<AbstractInsnNode> toExclude = new LinkedHashSet<AbstractInsnNode>(findOpaquePredicateGuardInsns(working, opaqueParamIndexes));
        if (!didUnwrap) toExclude.addAll(findObfuscationExceptionWrapperInsns(working));

        // 5) Process instructions to fill opcodeHistogram, strings, invoked signatures
        processInstructions(working, toExclude, wrapperNoisyStrings);

        // 6) Stable fingerprint
        this.fingerprint = fingerprint();
    }

    // -- shallow clone to detach instruction edits from upstream graph --
    private static MethodNode cloneShallow(MethodNode mn) {
        MethodNode c = new MethodNode(mn.access, mn.name, mn.desc, mn.signature,
                (mn.exceptions == null ? null : (String[]) mn.exceptions.toArray(new String[mn.exceptions.size()])));
        if (mn.instructions != null) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) c.instructions.add(insn);
        }
        if (mn.tryCatchBlocks != null) c.tryCatchBlocks = new ArrayList<TryCatchBlockNode>(mn.tryCatchBlocks);
        return c;
    }

    // -- main pass: collect features --
    private void processInstructions(MethodNode mn, Set<AbstractInsnNode> excludeInsns, Set<String> excludeStrings) {
        if (mn.instructions == null) return;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (excludeInsns.contains(insn)) continue;

            int op = insn.getOpcode();
            if (op >= 0) {
                Integer cur = opcodeHistogram.get(op);
                opcodeHistogram.put(op, (cur == null ? 1 : (cur.intValue() + 1)));
            }

            if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                if (cst instanceof String) {
                    String s = (String) cst;
                    if (!excludeStrings.contains(s)) stringConstants.add(s);
                }
            }

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                invokedSignatures.add(mi.owner + "." + mi.name + mi.desc);
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                invokedSignatures.add("indy:" + indy.name + indy.desc);
            }
        }
    }

    // -- unwrap whole-method RuntimeException wrapper if it matches the known pattern --
    private static InsnList tryUnwrapWholeRuntimeExceptionWrapper(MethodNode mn) {
        if (mn.tryCatchBlocks == null || mn.tryCatchBlocks.size() != 1) return null;
        TryCatchBlockNode tcb = (TryCatchBlockNode) mn.tryCatchBlocks.get(0);
        if (!"java/lang/RuntimeException".equals(tcb.type)) return null;
        if (!isObfuscationCatchWrapper(tcb, mn)) return null;

        List<AbstractInsnNode> inner = new ArrayList<AbstractInsnNode>();
        AbstractInsnNode cur = tcb.start;
        boolean sawExit = false;
        while (cur != null && cur != tcb.end) {
            if (cur.getOpcode() >= 0) {
                inner.add(cur);
                int op = cur.getOpcode();
                if (isReturnOpcode(op) || op == ATHROW) {
                    sawExit = true;
                    AbstractInsnNode after = cur.getNext();
                    while (after != null && after != tcb.end) {
                        if (!isTrivial(after)) return null;
                        after = after.getNext();
                    }
                    break;
                }
            } else {
                inner.add(cur);
            }
            cur = cur.getNext();
        }
        if (!sawExit) return null;

        InsnList cleaned = new InsnList();
        Map<LabelNode, LabelNode> map = new LinkedHashMap<LabelNode, LabelNode>();
        // First pass: create label mappings
        for (AbstractInsnNode insn : inner) {
            if (insn instanceof LabelNode) {
                LabelNode ln = (LabelNode) insn;
                if (!map.containsKey(ln)) map.put(ln, new LabelNode());
            }
        }
        // Second pass: clone with map
        for (AbstractInsnNode insn : inner) {
            AbstractInsnNode cloned = insn.clone(map);
            if (cloned != null) cleaned.add(cloned);
        }
        return cleaned;
    }

    private static boolean isTrivial(AbstractInsnNode insn){ return insn.getOpcode() < 0; }
    private static boolean isReturnOpcode(int opcode){
        return opcode==RETURN||opcode==IRETURN||opcode==ARETURN||opcode==LRETURN||opcode==DRETURN||opcode==FRETURN;
    }

    // -- collect noisy strings from the obfuscation catch wrapper handler --
    private static Set<String> collectWrapperSignatureStrings(MethodNode mn) {
        Set<String> out = new LinkedHashSet<String>();
        if (mn.tryCatchBlocks == null) return out;

        for (Object o : mn.tryCatchBlocks) {
            TryCatchBlockNode tcb = (TryCatchBlockNode) o;
            if (!"java/lang/RuntimeException".equals(tcb.type)) continue;
            AbstractInsnNode cur = tcb.handler;

            if (cur instanceof VarInsnNode && cur.getOpcode() == ASTORE) cur = cur.getNext();
            while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
            if (!(cur instanceof VarInsnNode && cur.getOpcode() == ALOAD)) continue;
            cur = cur.getNext();

            while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
            if (cur instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) cur).cst;
                if (cst instanceof String) {
                    String s = (String) cst;
                    if (s.indexOf('(') >= 0 && s.indexOf(')') >= 0) out.add(s);
                }
            }
        }
        return out;
    }

    // -- detect opaque guard blocks around opaque params and mark early-exit region for exclusion --
    private static Set<AbstractInsnNode> findOpaquePredicateGuardInsns(MethodNode mn, Set<Integer> opaqueParams) {
        Set<AbstractInsnNode> toExclude = new LinkedHashSet<AbstractInsnNode>();
        boolean isStatic = (mn.access & ACC_STATIC) != 0;
        int paramBase = isStatic ? 0 : 1;
        Type[] args = Type.getArgumentTypes(mn.desc);

        AbstractInsnNode[] insns = (mn.instructions==null? new AbstractInsnNode[0] : mn.instructions.toArray());

        for (Integer paramIdxObj : opaqueParams) {
            if (paramIdxObj == null) continue;
            int paramIdx = paramIdxObj.intValue();
            int localIndex = paramBase + computeLocalIndex(args, paramIdx);

            for (AbstractInsnNode insn : insns) {
                if (insn instanceof VarInsnNode) {
                    VarInsnNode vin = (VarInsnNode) insn;
                    if (vin.getOpcode() == ILOAD && vin.var == localIndex) {
                        AbstractInsnNode n1 = vin.getNext();
                        if (n1 != null) {
                            Integer cst = extractIntConstant(n1);
                            if (cst != null) {
                                AbstractInsnNode n2 = n1.getNext();
                                if (n2 instanceof JumpInsnNode) {
                                    int opc = ((JumpInsnNode) n2).getOpcode();
                                    if (opc == IF_ICMPEQ || opc == IF_ICMPNE) {
                                        Set<AbstractInsnNode> early = gatherEarlyExitBlock(((JumpInsnNode) n2).label);
                                        if (!early.isEmpty()) {
                                            toExclude.add(vin); toExclude.add(n1); toExclude.add(n2); toExclude.addAll(early);
                                        }
                                    }
                                }
                            }
                        }
                        AbstractInsnNode n3 = vin.getNext();
                        if (n3 instanceof JumpInsnNode) {
                            int opc2 = ((JumpInsnNode) n3).getOpcode();
                            if (opc2 == IFEQ || opc2 == IFNE) {
                                Set<AbstractInsnNode> early = gatherEarlyExitBlock(((JumpInsnNode) n3).label);
                                if (!early.isEmpty()) { toExclude.add(vin); toExclude.add(n3); toExclude.addAll(early); }
                            }
                        }
                    }
                }
            }
        }
        return toExclude;
    }

    // -- mark handler body of known obfuscation wrapper for exclusion (if not fully unwrapped) --
    private static Set<AbstractInsnNode> findObfuscationExceptionWrapperInsns(MethodNode mn) {
        Set<AbstractInsnNode> exclude = new LinkedHashSet<AbstractInsnNode>();
        if (mn.tryCatchBlocks == null) return exclude;

        for (Object o : mn.tryCatchBlocks) {
            TryCatchBlockNode tcb = (TryCatchBlockNode) o;
            if (!"java/lang/RuntimeException".equals(tcb.type)) continue;
            if (!isObfuscationCatchWrapper(tcb, mn)) continue;

            AbstractInsnNode cur = tcb.handler;
            while (cur != null) {
                exclude.add(cur);
                if (cur instanceof InsnNode && cur.getOpcode() == ATHROW) break;
                cur = cur.getNext();
            }
        }
        return exclude;
    }

    // -- rough recognizer of obfuscation catch wrapper: ALOAD, LDC(String sig), INVOKESTATIC helper, ATHROW --
    private static boolean isObfuscationCatchWrapper(TryCatchBlockNode tcb, MethodNode mn) {
        AbstractInsnNode cur = tcb.handler;

        // Skip non-opcode nodes at handler start
        while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
        // Optional: initial ASTORE of the caught exception
        if (cur instanceof VarInsnNode && cur.getOpcode() == ASTORE) {
            cur = cur.getNext();
            while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
        }
        // Expect ALOAD of the stored exception
        if (!(cur instanceof VarInsnNode && cur.getOpcode() == ALOAD)) return false; cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
        if (!(cur instanceof LdcInsnNode)) return false;
        Object cst = ((LdcInsnNode) cur).cst;
        if (!(cst instanceof String)) return false;
        String s = (String) cst;
        if (s.indexOf('(') < 0 || s.indexOf(')') < 0) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
        if (!(cur instanceof MethodInsnNode)) return false;
        MethodInsnNode mi = (MethodInsnNode) cur;
        if (mi.getOpcode() != INVOKESTATIC) return false;
        if (!"(Ljava/lang/Throwable;Ljava/lang/String;)Ljava/lang/Throwable;".equals(mi.desc)) return false;
        cur = cur.getNext();

        while (cur != null && cur.getOpcode() < 0) cur = cur.getNext();
        return (cur instanceof InsnNode) && (cur.getOpcode() == ATHROW);
    }

    private static int computeLocalIndex(Type[] args, int targetParamIndex) {
        int idx = 0;
        for (int i=0;i<targetParamIndex;i++) idx += args[i].getSize();
        return idx;
    }

    private static Integer extractIntConstant(AbstractInsnNode insn) {
        if (insn == null) return null;
        int op = insn.getOpcode();
        switch (op) {
            case ICONST_M1: return Integer.valueOf(-1);
            case ICONST_0:  return Integer.valueOf(0);
            case ICONST_1:  return Integer.valueOf(1);
            case ICONST_2:  return Integer.valueOf(2);
            case ICONST_3:  return Integer.valueOf(3);
            case ICONST_4:  return Integer.valueOf(4);
            case ICONST_5:  return Integer.valueOf(5);
            case BIPUSH:
            case SIPUSH:
                if (insn instanceof IntInsnNode) return Integer.valueOf(((IntInsnNode) insn).operand);
                break;
            default:
                if (insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Integer) return (Integer) cst;
                }
        }
        return null;
    }

    private String normalizeDescriptor(String desc, Set<Integer> opaqueParamIndexes) {
        if (opaqueParamIndexes == null || opaqueParamIndexes.isEmpty()) return desc;
        Type ret = Type.getReturnType(desc);
        Type[] args = Type.getArgumentTypes(desc);
        java.util.List<Type> filtered = new java.util.ArrayList<Type>();
        for (int i=0;i<args.length;i++) if (!opaqueParamIndexes.contains(Integer.valueOf(i))) filtered.add(args[i]);
        Type[] newArgs = (Type[]) filtered.toArray(new Type[filtered.size()]);
        return Type.getMethodDescriptor(ret, newArgs);
    }

    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizedDescriptor).append('|');
        sb.append(sortedHash(opcodeHistogram.keySet())).append('|');
        sb.append(sortedHash(invokedSignatures)).append('|');
        sb.append(sortedHash(stringConstants)).append('|');
        return sha256(sb.toString());
    }

    private static String sortedHash(Collection<?> col) {
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (Object o : col) list.add(String.valueOf(o));
        java.util.Collections.sort(list);
        return join(list, ",");
    }

    private static String join(java.util.List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<parts.size();i++) { if (i>0) sb.append(sep); sb.append(parts.get(i)); }
        return sb.toString();
    }

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format(java.util.Locale.ROOT, "%02x", new Object[]{Byte.valueOf(b)}));
            return hex.toString();
        } catch (Exception e) {
            // Fallback (deterministic but weaker) if JCE unavailable in env
            return Integer.toHexString(in.hashCode());
        }
    }

    /** Look ahead from start up to a small window and collect until an early exit (return/throw). */
    private static Set<AbstractInsnNode> gatherEarlyExitBlock(AbstractInsnNode start) {
        if (start == null) return java.util.Collections.<AbstractInsnNode>emptySet();
        AbstractInsnNode cur = start;
        int steps = 0;
        java.util.List<AbstractInsnNode> collected = new java.util.ArrayList<AbstractInsnNode>();
        while (cur != null && steps < 12) {
            if (cur.getOpcode() >= 0) {
                collected.add(cur);
                int op = cur.getOpcode();
                if (isReturnOpcode(op) || op == ATHROW) return new java.util.LinkedHashSet<AbstractInsnNode>(collected);
            } else {
                collected.add(cur);
            }
            cur = cur.getNext();
            steps++;
        }
        return java.util.Collections.<AbstractInsnNode>emptySet();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER signals NormalizedMethod END
