// >>> AUTOGEN: BYTECODEMAPPER signals NormalizedMethod BEGIN
package io.bytecodemapper.signals.normalized;

import io.bytecodemapper.core.hash.StableHash64;
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
 *  - fingerprint: SHA-256 over norma lizedDescriptor + sorted opcode set + invoked + strings
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
    // CODEGEN-BEGIN: nsfv2-state
    // Filtered instruction array (after unwrap/exclusions) used for NSFv2 analysis
    private AbstractInsnNode[] filteredInsns = new AbstractInsnNode[0];
    // Try/catch blocks after unwrap (original handler list may be cleared on unwrap)
    private List<TryCatchBlockNode> filteredTryCatch = java.util.Collections.emptyList();
    // CODEGEN-END: nsfv2-state

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

        // CODEGEN-BEGIN: nsfv2-state
        // Snapshot filtered instructions and try/catch for NSFv2 computations
        if (working.instructions != null) {
            ArrayList<AbstractInsnNode> list = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode insn : working.instructions.toArray()) {
                if (!toExclude.contains(insn)) list.add(insn);
            }
            this.filteredInsns = list.toArray(new AbstractInsnNode[list.size()]);
        } else {
            this.filteredInsns = new AbstractInsnNode[0];
        }
        this.filteredTryCatch = (working.tryCatchBlocks == null)
                ? java.util.Collections.<TryCatchBlockNode>emptyList()
                : new ArrayList<TryCatchBlockNode>(working.tryCatchBlocks);
        // CODEGEN-END: nsfv2-state

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
    /** New: produce NormalizedFeatures (deterministic). */
    public NormalizedFeatures extract() {
        Map<String,Integer> opcodeBag = buildOpcodeBag();             // existing normalized bucket
        Map<String,Integer> callKinds  = buildCallKinds();            // VIRT/STATIC/INTERFACE/CTOR
        Map<String,Integer> stackHist  = buildStackDeltaHistogram();  // -2..+2 buckets (coarse)
        NormalizedFeatures.TryCatchShape tc =
            new NormalizedFeatures.TryCatchShape(tryDepth(), tryFanout(), tryCatchTypesHash());
        NormalizedFeatures.MinHash32 lits = new NormalizedFeatures.MinHash32(buildLiteralsSketch());
        NormalizedFeatures.TfIdfSketch strs = new NormalizedFeatures.TfIdfSketch(buildStringsTf());
    // CODEGEN-BEGIN: nsfv2-wire
    // Build NSFv2 payload and hash deterministically
    final String payload = buildNsfPayloadV2();
    final long nsf64 = StableHash64.hashUtf8(payload);
    // CODEGEN-END: nsfv2-wire
        return new NormalizedFeatures(opcodeBag, callKinds, stackHist, tc, lits, strs, nsf64);
    }

    /** NSFv1: stable 64-bit over sorted, compact tuples. */
    @SuppressWarnings("unused")
    private static long buildFingerprintNSFv1(Map<String,Integer> op,
                                              Map<String,Integer> ck,
                                              Map<String,Integer> sh,
                                              NormalizedFeatures.TryCatchShape tc,
                                              NormalizedFeatures.MinHash32 lits,
                                              NormalizedFeatures.TfIdfSketch strs) {
        // Build deterministic lines: TAG|key|value (sorted lexicographically)
        ArrayList<String> lines = new ArrayList<String>();
        addSorted(lines, "OP", op);
        addSorted(lines, "CK", ck);
        addSorted(lines, "SH", sh);
        lines.add("TC|" + tc.depth + "|" + tc.fanout + "|" + tc.catchTypeHash);
        if (lits != null && lits.sketch != null) {
            StringBuilder sb = new StringBuilder("LH|");
            for (int i=0;i<lits.sketch.length;i++) { if (i>0) sb.append(','); sb.append(lits.sketch[i]); }
            lines.add(sb.toString());
        }
        if (strs != null && strs.tf != null) {
            // only top-N (e.g., by tf) to stabilize
            ArrayList<Map.Entry<String,Float>> es = new ArrayList<Map.Entry<String,Float>>(strs.tf.entrySet());
            Collections.sort(es, new Comparator<Map.Entry<String,Float>>() {
                public int compare(Map.Entry<String,Float> a, Map.Entry<String,Float> b) {
                    int c = Float.compare(b.getValue(), a.getValue()); if (c!=0) return c;
                    return a.getKey().compareTo(b.getKey());
                }});
            int N = Math.min(16, es.size());
            for (int i=0;i<N;i++) {
                Map.Entry<String,Float> e = es.get(i);
                lines.add("ST|" + e.getKey() + "|" + String.format(java.util.Locale.ROOT, "%.6f", e.getValue()));
            }
        }
        Collections.sort(lines);
        String payload = "NSFv1\n" + join(lines, "\n");
        return StableHash64.hashUtf8(payload);
    }

    @SuppressWarnings("unused")
    private static void addSorted(ArrayList<String> out, String tag, Map<String,? extends Object> m) {
        ArrayList<String> keys = new ArrayList<String>(m.keySet());
        Collections.sort(keys);
        for (String k : keys) out.add(tag + "|" + k + "|" + String.valueOf(m.get(k)));
    }

    // Stubs referencing existing logic—delegate to normalized fields we already compute
    private Map<String,Integer> buildOpcodeBag(){ return normalizedOpcodeBag(); }
    private Map<String,Integer> buildCallKinds(){ return normalizedCallKinds(); }
    private Map<String,Integer> buildStackDeltaHistogram(){ return normalizedStackDeltaHistogram(); }
    private int tryDepth(){ return normalizedTryDepth(); }
    private int tryFanout(){ return normalizedTryFanout(); }
    private int tryCatchTypesHash(){ return normalizedCatchTypesHash(); }
    private int[] buildLiteralsSketch(){ return normalizedLiteralsMinHash64(); }
    private Map<String,Float> buildStringsTf(){ return normalizedStringsTf(); }

    // Implementations using existing collected data for determinism (coarse but stable)
    private Map<String,Integer> normalizedOpcodeBag() {
        LinkedHashMap<String,Integer> bag = new LinkedHashMap<String,Integer>();
        for (Map.Entry<Integer,Integer> e : this.opcodeHistogram.entrySet()) {
            int op = e.getKey().intValue();
            String name = String.valueOf(op); // stable without asm-util dependency
            Integer cur = bag.get(name);
            bag.put(name, Integer.valueOf((cur==null?0:cur.intValue()) + e.getValue().intValue()));
        }
        return bag;
    }

    private Map<String,Integer> normalizedCallKinds() {
        LinkedHashMap<String,Integer> kinds = new LinkedHashMap<String,Integer>();
        // from opcode histogram
        int virt = getOpCount(org.objectweb.asm.Opcodes.INVOKEVIRTUAL);
        int stat = getOpCount(org.objectweb.asm.Opcodes.INVOKESTATIC);
        int itf  = getOpCount(org.objectweb.asm.Opcodes.INVOKEINTERFACE);
        int ctor = 0;
        for (String s : this.invokedSignatures) { if (s.indexOf(".<init>(") >= 0) ctor++; }
        if (virt != 0) kinds.put("VIRT", Integer.valueOf(virt));
        if (stat != 0) kinds.put("STATIC", Integer.valueOf(stat));
        if (itf  != 0) kinds.put("INTERFACE", Integer.valueOf(itf));
        if (ctor != 0) kinds.put("CTOR", Integer.valueOf(ctor));
        return kinds;
    }

    private int getOpCount(int opcode) {
        Integer v = this.opcodeHistogram.get(Integer.valueOf(opcode));
        return (v==null?0:v.intValue());
    }

    private LinkedHashMap<String,Integer> normalizedStackDeltaHistogram() {
        // CODEGEN-BEGIN: nsfv2-core
        // Coarse stack-delta histogram over filtered instructions, clamped to [-2..+2]
        final String[] keys = new String[]{"-2","-1","0","+1","+2"};
        LinkedHashMap<String,Integer> hist = new LinkedHashMap<String,Integer>();
        for (String k : keys) hist.put(k, Integer.valueOf(0));
        if (filteredInsns == null) return hist;

        for (AbstractInsnNode insn : filteredInsns) {
            int op = insn.getOpcode();
            if (op < 0) continue; // skip pseudo-insns
            int delta = stackDeltaSlots(insn);
            if (delta < -2) delta = -2; else if (delta > 2) delta = 2;
            switch (delta) {
                case -2: hist.put("-2", Integer.valueOf(hist.get("-2").intValue()+1)); break;
                case -1: hist.put("-1", Integer.valueOf(hist.get("-1").intValue()+1)); break;
                case 0:  hist.put("0",  Integer.valueOf(hist.get("0").intValue()+1)); break;
                case 1:  hist.put("+1", Integer.valueOf(hist.get("+1").intValue()+1)); break;
                case 2:  hist.put("+2", Integer.valueOf(hist.get("+2").intValue()+1)); break;
            }
        }
        return hist;
        // CODEGEN-END: nsfv2-core
    }

    private int normalizedTryDepth() {
        // CODEGEN-BEGIN: nsfv2-core
        if (filteredTryCatch == null || filteredTryCatch.isEmpty() || filteredInsns == null || filteredInsns.length==0) return 0;
        Map<LabelNode,Integer> labelIndex = buildLabelIndex();
        // Build events for sweep-line depth computation
        ArrayList<int[]> events = new ArrayList<int[]>(); // [index, +1/-1]
        for (TryCatchBlockNode t : filteredTryCatch) {
            Integer s = labelIndex.get(t.start);
            Integer e = labelIndex.get(t.end);
            if (s == null || e == null) continue;
            events.add(new int[]{s.intValue(), +1});
            events.add(new int[]{e.intValue(), -1});
        }
        if (events.isEmpty()) return 0;
        Collections.sort(events, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) { int c = Integer.compare(a[0], b[0]); if (c!=0) return c; return Integer.compare(a[1], b[1]); }
        });
        int depth = 0, maxDepth = 0;
        for (int[] ev : events) { depth += ev[1]; if (depth > maxDepth) maxDepth = depth; }
        return maxDepth;
        // CODEGEN-END: nsfv2-core
    }
    private int normalizedTryFanout() {
        // CODEGEN-BEGIN: nsfv2-core
        if (filteredTryCatch == null || filteredTryCatch.isEmpty()) return 0;
        // Group by (start,end) to count multi-catch fanout
        Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
        for (TryCatchBlockNode t : filteredTryCatch) {
            String key = System.identityHashCode(t.start) + ":" + System.identityHashCode(t.end);
            Integer cur = counts.get(key);
            counts.put(key, Integer.valueOf(cur==null?1:cur.intValue()+1));
        }
        int max = 0; for (Integer v : counts.values()) if (v!=null && v.intValue()>max) max = v.intValue();
        return max;
        // CODEGEN-END: nsfv2-core
    }
    private int normalizedCatchTypesHash() {
        // CODEGEN-BEGIN: nsfv2-core
        if (filteredTryCatch == null || filteredTryCatch.isEmpty()) return 0;
        ArrayList<String> types = new ArrayList<String>();
        for (TryCatchBlockNode t : filteredTryCatch) {
            if (t.type != null) types.add(t.type);
        }
        if (types.isEmpty()) return 0;
        Collections.sort(types);
        String joined = join(types, ",");
        long h = StableHash64.hashUtf8(joined);
        return (int)(h ^ (h >>> 32));
        // CODEGEN-END: nsfv2-core
    }



    // CODEGEN-BEGIN: nsfv2-core
    // 3) Numeric-literal MinHash (64 buckets)
    private int[] normalizedLiteralsMinHash64() {
        if (filteredInsns == null || filteredInsns.length == 0) return null;
        final int BUCKETS = 64;
        int[] sketch = new int[BUCKETS];
        Arrays.fill(sketch, Integer.MAX_VALUE);
        boolean saw = false;
        for (AbstractInsnNode insn : filteredInsns) {
            int op = insn.getOpcode();
            if (op < 0) continue;
            if (op == BIPUSH || op == SIPUSH) {
                if (insn instanceof IntInsnNode) {
                    int v = ((IntInsnNode) insn).operand;
                    if (v >= -1 && v <= 5) continue; // ignore JVM small ints noise
                    saw |= updateSketch(sketch, String.valueOf(v));
                }
            } else if (insn instanceof LdcInsnNode) {
                Object c = ((LdcInsnNode) insn).cst;
                if (c instanceof Integer) {
                    int v = ((Integer) c).intValue();
                    if (v >= -1 && v <= 5) continue; // ignore
                    saw |= updateSketch(sketch, String.valueOf(v));
                } else if (c instanceof Long) {
                    saw |= updateSketch(sketch, String.valueOf(((Long) c).longValue()));
                } else if (c instanceof Float) {
                    saw |= updateSketch(sketch, Float.toString(((Float) c).floatValue()));
                } else if (c instanceof Double) {
                    saw |= updateSketch(sketch, Double.toString(((Double) c).doubleValue()));
                }
            }
        }
        if (!saw) return null;
        return sketch;
    }

    private boolean updateSketch(int[] sketch, String canonical) {
        long h64 = StableHash64.hashUtf8(canonical);
        int b = (int)(h64 & 63L);
        int v = (int)(h64 ^ (h64 >>> 32));
        if (v < sketch[b]) { sketch[b] = v; return true; }
        return true; // saw literal even if not min
    }

    // 4) Invoke-kind encoding (counts of VIRT/STATIC/INTERFACE/CTOR)
    private int[] invokeKindCounts() { // length=4, order: VIRT, STATIC, INTERFACE, CTOR
        int virt = getOpCount(INVOKEVIRTUAL);
        int stat = getOpCount(INVOKESTATIC);
        int itf  = getOpCount(INVOKEINTERFACE);
        int ctor = 0;
        for (String sig : this.invokedSignatures) if (sig.indexOf(".<init>(") >= 0) ctor++;
        return new int[]{virt, stat, itf, ctor};
    }

    // 5) Build NSFv2 payload (sorted pieces; include version header)
    private String buildNsfPayloadV2() {
        StringBuilder out = new StringBuilder();
        out.append("NSFv2\n");
        // Descriptor
        out.append("D|").append(this.normalizedDescriptor).append('\n');
        // Sorted opcode keys (as integers turned into strings)
        ArrayList<String> opcodeKeys = new ArrayList<String>();
        for (Integer k : this.opcodeHistogram.keySet()) opcodeKeys.add(String.valueOf(k));
        Collections.sort(opcodeKeys);
        out.append("O|").append(join(opcodeKeys, ",")).append('\n');
        // Sorted invoked signatures
        ArrayList<String> invokes = new ArrayList<String>(this.invokedSignatures);
        Collections.sort(invokes);
        out.append("S|").append(join(invokes, ",")).append('\n');
        // Sorted strings
        ArrayList<String> strs = new ArrayList<String>(this.stringConstants);
        Collections.sort(strs);
        out.append("T|").append(join(strs, ",")).append('\n');
        // Stack histogram in fixed order
        LinkedHashMap<String,Integer> sh = normalizedStackDeltaHistogram();
        out.append("H|")
           .append("-2:").append(String.valueOf(sh.get("-2")))
           .append(',').append("-1:").append(String.valueOf(sh.get("-1")))
           .append(',').append("0:").append(String.valueOf(sh.get("0")))
           .append(',').append("+1:").append(String.valueOf(sh.get("+1")))
           .append(',').append("+2:").append(String.valueOf(sh.get("+2")))
           .append('\n');
        // Try shape triple
        out.append("Y|").append(normalizedTryDepth()).append('|').append(normalizedTryFanout()).append('|').append(normalizedCatchTypesHash()).append('\n');
        // Literals sketch
        int[] sketch = normalizedLiteralsMinHash64();
        if (sketch == null) {
            out.append("L|∅\n");
        } else {
            out.append("L|");
            for (int i=0;i<sketch.length;i++) { if (i>0) out.append(','); out.append(sketch[i]); }
            out.append('\n');
        }
        // Invoke-kind counts
        int[] kinds = invokeKindCounts();
        out.append("K|").append(kinds[0]).append(',').append(kinds[1]).append(',').append(kinds[2]).append(',').append(kinds[3]);
        return out.toString();
    }
    // CODEGEN-END: nsfv2-core

    // Helper: approximate stack delta in slots (coarse, covers common opcodes deterministically)
    private static int stackDeltaSlots(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        switch (op) {
            // Constants push (+1 or +2 for long/double)
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case BIPUSH:
            case SIPUSH:
            case LDC: return +1;
            // Loads push
            case ILOAD: case LLOAD: case FLOAD: case DLOAD: case ALOAD: return +1;
            // Stores pop
            case ISTORE: case LSTORE: case FSTORE: case DSTORE: case ASTORE: return -1;
            // Stack ops
            case POP: return -1;
            case POP2: return -2;
            case DUP: return +1;
            case DUP_X1: return +1;
            case DUP_X2: return +1;
            case DUP2: return +2; // approximate
            case DUP2_X1: return +1; // approximate
            case DUP2_X2: return +1; // approximate
            case SWAP: return 0;
            // Arithmetic (binary ops pop2 push1 => -1)
            case IADD: case ISUB: case IMUL: case IDIV: case IREM:
            case FADD: case FSUB: case FMUL: case FDIV: case FREM:
            case LADD: case LSUB: case LMUL: case LDIV: case LREM:
            case DADD: case DSUB: case DMUL: case DDIV: case DREM:
                return -1;
            case INEG: case FNEG: case LNEG: case DNEG: return 0;
            // Conversions (pop1 push1)
            case I2F: case I2L: case I2D: case F2I: case F2L: case F2D:
            case L2I: case L2F: case L2D: case D2I: case D2F: case D2L:
            case I2B: case I2C: case I2S: return 0;
            // Comparisons/if
            case IFEQ: case IFNE: case IFLT: case IFGE: case IFGT: case IFLE:
            case IF_ICMPEQ: case IF_ICMPNE: case IF_ICMPLT: case IF_ICMPGE: case IF_ICMPGT: case IF_ICMPLE:
            case IF_ACMPEQ: case IF_ACMPNE:
            case TABLESWITCH: case LOOKUPSWITCH:
                return -1; // consume condition value(s)
            case GOTO: return 0;
            // Returns
            case RETURN: return 0;
            case IRETURN: case FRETURN: case ARETURN: return -1;
            case LRETURN: case DRETURN: return -2;
            case ATHROW: return -1;
            // Field access
            case GETSTATIC: return +1;
            case PUTSTATIC: return -1;
            case GETFIELD: return 0; // pop obj, push value => 0 approx
            case PUTFIELD: return -2; // pop obj + value
            // Array
            case NEWARRAY: case ANEWARRAY: case ARRAYLENGTH: return 0; // approx
            case AALOAD: case IALOAD: case LALOAD: case FALOAD: case DALOAD: case BALOAD: case CALOAD: case SALOAD: return -1;
            case AASTORE: case IASTORE: case LASTORE: case FASTORE: case DASTORE: case BASTORE: case CASTORE: case SASTORE: return -3;
            // Object
            case NEW: return +1;
            case CHECKCAST: case INSTANCEOF: return 0;
            case MONITORENTER: case MONITOREXIT: return -1;
            // Method calls (approx: args-returns; we can't parse desc here reliably without node)
            case INVOKEVIRTUAL: case INVOKESTATIC: case INVOKEINTERFACE: case INVOKESPECIAL:
                // Without signature, a coarse 0 keeps histogram meaningful
                return 0;
            default:
                return 0;
        }
    }

    private Map<LabelNode,Integer> buildLabelIndex() {
        LinkedHashMap<LabelNode,Integer> map = new LinkedHashMap<LabelNode,Integer>();
        for (int i=0;i<filteredInsns.length;i++) {
            AbstractInsnNode n = filteredInsns[i];
            if (n instanceof LabelNode) map.put((LabelNode)n, Integer.valueOf(i));
        }
        return map;
    }

    private Map<String,Float> normalizedStringsTf() {
        LinkedHashMap<String,Float> tf = new LinkedHashMap<String,Float>();
        for (String s : this.stringConstants) tf.put(s, Float.valueOf(1.0f));
        return tf;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER signals NormalizedMethod END
