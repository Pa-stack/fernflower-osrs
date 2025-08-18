// >>> AUTOGEN: BYTECODEMAPPER Normalizer BEGIN
package io.bytecodemapper.core.normalize;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Minimal, surgical normalizer that operates on ASM Tree (Java 8 compatible).
 *
 * Passes (guarded by Options flags):
 *  - stripOpaquePredicates:
 *      * ICONST_0/1 + IFEQ/IFNE (if(true)/if(false)) => remove or turn into GOTO
 *      * constant-constant IF_ICMP* (ICONST_* / BIPUSH / SIPUSH / LDC int) => fold
 *      * constant key TABLESWITCH/LOOKUPSWITCH => replace with GOTO chosen label
 *  - removeTrivialRuntimeExceptionWrappers:
 *      * Pattern: NEW java/lang/RuntimeException ; DUP ; [optional LDC String] ;
 *        INVOKESPECIAL java/lang/RuntimeException.<init> ; ATHROW
 *        Typically used as trivial guards; remove the throw sequence block.
 *  - detectFlattening:
 *      * Early dispatcher switch with many cases and high GOTO ratio => set bypassDFTDF
 *
 * NOTE: This class MUTATES the given MethodNode in place for simplicity and determinism.
 */
public final class Normalizer implements Opcodes {

    private Normalizer(){}

    /** Normalization options (all enabled by default). */
    public static final class Options {
        public boolean normalizeOpaque = true;
        public boolean removeTrivialRuntimeWrapper = true;
        public boolean detectFlattening = true;

        public static Options defaults() { return new Options(); }
    }

    /** Normalization result. */
    public static final class Result {
        public final MethodNode method;
        public final boolean bypassDFTDF;
        public final Stats stats;

        Result(MethodNode method, boolean bypass, Stats stats) {
            this.method = method;
            this.bypassDFTDF = bypass;
            this.stats = stats;
        }
    }

    /** Simple stats for tests/debug. */
    public static final class Stats {
        public int opaqueBranchesRemoved;
        public int switchesFolded;
        public int runtimeWrappersRemoved;
        @Override public String toString() {
            return "Stats{opaque=" + opaqueBranchesRemoved + ", switch=" + switchesFolded + ", wrappers=" + runtimeWrappersRemoved + "}";
        }
    }

    /** Normalize the given method in place and return a result wrapper. */
    public static Result normalize(MethodNode mn, Options opt) {
        if (mn == null) return new Result(null, false, new Stats());
        Stats stats = new Stats();

        boolean bypass = false;
        if (opt.normalizeOpaque) {
            stats.opaqueBranchesRemoved += stripOpaquePredicates(mn);
            stats.switchesFolded += foldConstantSwitches(mn);
        }
        if (opt.removeTrivialRuntimeWrapper) {
            stats.runtimeWrappersRemoved += removeTrivialRuntimeExceptionWrappers(mn);
        }
        if (opt.detectFlattening) {
            bypass = detectFlattening(mn);
        }
        return new Result(mn, bypass, stats);
    }

    // ---- Passes ----

    /** Remove obvious if(true)/if(false) and const-const IF_ICMP* branches. */
    private static int stripOpaquePredicates(MethodNode mn) {
        int removed = 0;
        InsnList insns = mn.instructions;
        // Work on a copy of references for stable iteration
        List<AbstractInsnNode> nodes = toList(insns);
        for (int i = 0; i < nodes.size(); i++) {
            AbstractInsnNode n = nodes.get(i);
            if (!(n instanceof JumpInsnNode)) continue;
            JumpInsnNode j = (JumpInsnNode) n;
            int op = j.getOpcode();

            // Single-operand int condition: IFEQ/IFNE on ICONST_0/1
            if ((op == IFEQ || op == IFNE) && i > 0) {
                Integer v = intConstValue(nodes.get(i - 1));
                if (v != null) {
                    boolean taken = (op == IFNE) ? (v != 0) : (v == 0);
                    // rewrite: if(always true) -> GOTO target; else remove the jump
                    if (taken) {
                        // replace condition with GOTO target, remove the const (leave stack clean)
                        InsnList repl = new InsnList();
                        repl.add(new JumpInsnNode(GOTO, j.label));
                        insns.insert(n, repl);
                        insns.remove(n);
                        // remove the constant push (dead)
                        insns.remove(nodes.get(i - 1));
                    } else {
                        // condition never taken: remove jump and the constant push
                        insns.remove(n);
                        insns.remove(nodes.get(i - 1));
                    }
                    removed++;
                    continue;
                }
            }

            // Two-operand comparisons: IF_ICMP*
            if ((op >= IF_ICMPEQ && op <= IF_ICMPLE) && i > 1) {
                Integer a = intConstValue(nodes.get(i - 2));
                Integer b = intConstValue(nodes.get(i - 1));
                if (a != null && b != null) {
                    boolean taken = evalIfIcmp(op, a, b);
                    if (taken) {
                        // drop the two constants, replace with unconditional GOTO
                        insns.insert(n, new JumpInsnNode(GOTO, j.label));
                        insns.remove(n);
                    } else {
                        // drop the two constants and the jump
                        insns.remove(n);
                    }
                    insns.remove(nodes.get(i - 1));
                    insns.remove(nodes.get(i - 2));
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Replace constant TABLESWITCH/LOOKUPSWITCH with GOTO chosen label. */
    private static int foldConstantSwitches(MethodNode mn) {
        int folded = 0;
        InsnList insns = mn.instructions;
        List<AbstractInsnNode> nodes = toList(insns);
        for (int i = 0; i < nodes.size(); i++) {
            AbstractInsnNode n = nodes.get(i);
            if (n instanceof TableSwitchInsnNode) {
                Integer key = (i > 0 ? intConstValue(nodes.get(i - 1)) : null);
                if (key != null) {
                    TableSwitchInsnNode ts = (TableSwitchInsnNode) n;
                    LabelNode target;
                    if (key < ts.min || key > ts.max) {
                        target = ts.dflt;
                    } else {
                        int idx = key - ts.min;
                        target = ts.labels.get(idx);
                    }
                    // Replace: remove const, replace switch with GOTO target
                    insns.insert(n, new JumpInsnNode(GOTO, target));
                    insns.remove(n);
                    insns.remove(nodes.get(i - 1));
                    folded++;
                }
            } else if (n instanceof LookupSwitchInsnNode) {
                Integer key = (i > 0 ? intConstValue(nodes.get(i - 1)) : null);
                if (key != null) {
                    LookupSwitchInsnNode ls = (LookupSwitchInsnNode) n;
                    LabelNode target = ls.dflt;
                    for (int k = 0; k < ls.keys.size(); k++) {
                        Integer kk = (Integer) ls.keys.get(k);
                        if (kk != null && kk.intValue() == key.intValue()) {
                            target = ls.labels.get(k);
                            break;
                        }
                    }
                    insns.insert(n, new JumpInsnNode(GOTO, target));
                    insns.remove(n);
                    insns.remove(nodes.get(i - 1));
                    folded++;
                }
            }
        }
        return folded;
    }

    /** Remove exact NEW-<init>-ATHROW sequence for RuntimeException guard blocks. */
    private static int removeTrivialRuntimeExceptionWrappers(MethodNode mn) {
        int removed = 0;
        InsnList insns = mn.instructions;
        List<AbstractInsnNode> nodes = toList(insns);
        for (int i = 0; i < nodes.size(); i++) {
            AbstractInsnNode a = nodes.get(i);
            if (!(a instanceof TypeInsnNode)) continue;
            TypeInsnNode t = (TypeInsnNode) a;
            if (t.getOpcode() != NEW || !"java/lang/RuntimeException".equals(t.desc)) continue;

            // Expected: NEW ; DUP ; [optional LDC String] ; INVOKESPECIAL RuntimeException.<init> ; ATHROW
            if (i + 2 >= nodes.size()) continue;
            AbstractInsnNode b = nodes.get(i + 1);
            if (!(b instanceof InsnNode) || b.getOpcode() != DUP) continue;

            int j = i + 2;
            boolean hadMessage = false;
            if (j < nodes.size() && nodes.get(j) instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) nodes.get(j);
                if (ldc.cst instanceof String) { hadMessage = true; j++; }
            }
            if (j >= nodes.size()) continue;
            AbstractInsnNode c = nodes.get(j);
            if (!(c instanceof MethodInsnNode)) continue;
            MethodInsnNode init = (MethodInsnNode) c;
            if (init.getOpcode() != INVOKESPECIAL || !"java/lang/RuntimeException".equals(init.owner) || !"<init>".equals(init.name)) continue;

            if (j + 1 >= nodes.size()) continue;
            AbstractInsnNode d = nodes.get(j + 1);
            if (!(d instanceof InsnNode) || d.getOpcode() != ATHROW) continue;

            // Remove the block
            insns.remove(a);
            insns.remove(b);
            if (hadMessage) insns.remove(nodes.get(i + 2));
            insns.remove(c);
            insns.remove(d);
            removed++;
            // refresh snapshot to stay safe for subsequent scans
            nodes = toList(insns);
            i = -1;
        }
        return removed;
    }

    /** Heuristic flattening detector. */
    private static boolean detectFlattening(MethodNode mn) {
        InsnList in = mn.instructions;
        int total = 0, gotos = 0;
        boolean earlyBigSwitch = false;

        int idx = 0;
        for (AbstractInsnNode p = in.getFirst(); p != null; p = p.getNext()) {
            total++;
            if (p.getOpcode() == GOTO) gotos++;
            if (idx <= 8 && (p instanceof TableSwitchInsnNode || p instanceof LookupSwitchInsnNode)) {
                int cases = (p instanceof TableSwitchInsnNode)
                        ? (((TableSwitchInsnNode) p).labels.size())
                        : (((LookupSwitchInsnNode) p).labels.size());
                if (cases >= 8) earlyBigSwitch = true;
            }
            idx++;
        }
        double gotoRatio = (total == 0) ? 0.0 : (gotos * 1.0 / total);
        return earlyBigSwitch && gotoRatio >= 0.5;
    }

    // ---- utilities ----

    private static List<AbstractInsnNode> toList(InsnList insns) {
        ArrayList<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>(insns.size());
        for (AbstractInsnNode p = insns.getFirst(); p != null; p = p.getNext()) out.add(p);
        return out;
    }

    private static Integer intConstValue(AbstractInsnNode n) {
        if (n instanceof InsnNode) {
            switch (n.getOpcode()) {
                case ICONST_M1: return -1;
                case ICONST_0: return 0;
                case ICONST_1: return 1;
                case ICONST_2: return 2;
                case ICONST_3: return 3;
                case ICONST_4: return 4;
                case ICONST_5: return 5;
                default: break;
            }
        } else if (n instanceof IntInsnNode) {
            IntInsnNode ii = (IntInsnNode) n;
            if (ii.getOpcode() == BIPUSH || ii.getOpcode() == SIPUSH) return ii.operand;
        } else if (n instanceof LdcInsnNode) {
            Object c = ((LdcInsnNode) n).cst;
            if (c instanceof Integer) return (Integer) c;
        }
        return null;
    }

    private static boolean evalIfIcmp(int opcode, int a, int b) {
        switch (opcode) {
            case IF_ICMPEQ: return a == b;
            case IF_ICMPNE: return a != b;
            case IF_ICMPLT: return a < b;
            case IF_ICMPGE: return a >= b;
            case IF_ICMPGT: return a > b;
            case IF_ICMPLE: return a <= b;
            default: return false;
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER Normalizer END
