// >>> AUTOGEN: BYTECODEMAPPER ReducedCFG BEGIN
package io.bytecodemapper.core.cfg;

import it.unimi.dsi.fastutil.ints.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Deterministic reduced CFG:
 * - Basic blocks with stable IDs by first instruction index.
 * - Successors/Predecessors as sorted int arrays.
 * - Includes real exception edges (loose by default).
 * - Merges linear fallthrough chains (single-succ/single-pred, non-branching).
 */
public final class ReducedCFG implements Opcodes {

    public enum ExceptionEdgePolicy { STRICT, LOOSE }

    public static final class Block {
        public final int id;             // stable: first-insn index
        public final int startIdx;       // index in the method insn list
        public int endIdx;               // inclusive (mutable for merges)
        public final boolean isHandlerStart;

        private IntArrayList succs = new IntArrayList();
        private IntArrayList preds = new IntArrayList();

        Block(int id, int startIdx, int endIdx, boolean isHandlerStart) {
            this.id = id;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.isHandlerStart = isHandlerStart;
        }

        public int[] succs() { return toSortedDistinctArray(succs); }
        public int[] preds() { return toSortedDistinctArray(preds); }
    }

    private final MethodNode method;
    private final Int2ObjectMap<Block> blocks = new Int2ObjectOpenHashMap<Block>();
    private final IntArrayList rpoOrder = new IntArrayList(); // populated by computeRpo()

    private ReducedCFG(MethodNode method) {
        this.method = method;
    }

    public MethodNode method() { return method; }

    public Collection<Block> blocks() {
        // Return blocks ordered by ascending id for stability
        List<Block> out = new ArrayList<Block>(blocks.size());
        for (Block b : blocks.values()) out.add(b);
        Collections.sort(out, new Comparator<Block>() {
            public int compare(Block a, Block b) { return Integer.compare(a.id, b.id); }
        });
        return out;
    }

    public Block block(int id) { return blocks.get(id); }

    public int[] allBlockIds() {
        int[] ids = new int[blocks.size()];
        int i=0;
        for (Block b : blocks()) ids[i++] = b.id;
        return ids;
    }

    public int[] rpoOrder() { // computed lazily by Dominators
        return rpoOrder.toIntArray();
    }

    // ---- Build API ----

    public static ReducedCFG build(MethodNode mn) {
        return build(mn, ExceptionEdgePolicy.LOOSE);
    }

    public static ReducedCFG build(MethodNode mn, ExceptionEdgePolicy policy) {
        ReducedCFG cfg = new ReducedCFG(mn);
        cfg.buildBlocks(policy);
        cfg.dropUnreachable();
        cfg.mergeLinearChains();
        cfg.sortEdges();
        return cfg;
    }

    // ---- Internals ----

    private void buildBlocks(ExceptionEdgePolicy policy) {
        // Index instructions
        InsnList insns = method.instructions;
        Map<AbstractInsnNode,Integer> index = new IdentityHashMap<AbstractInsnNode, Integer>();
        int idx = 0;
        for (AbstractInsnNode p = insns.getFirst(); p != null; p = p.getNext()) {
            index.put(p, Integer.valueOf(idx++));
        }
        if (idx == 0) return; // empty method

        // Leaders: first insn, jump/switch targets, fallthrough after branch, exception handlers
        boolean[] isLeader = new boolean[idx];
        Arrays.fill(isLeader, false);
        AbstractInsnNode first = insns.getFirst();
        isLeader[index.get(first).intValue()] = true;

        Set<LabelNode> targetLabels = new HashSet<LabelNode>();
        for (AbstractInsnNode p = first; p != null; p = p.getNext()) {
            int op = p.getOpcode();
            if (op < 0) continue;
            if (p instanceof JumpInsnNode) {
                targetLabels.add(((JumpInsnNode)p).label);
                AbstractInsnNode q = p.getNext();
                if (q != null) isLeader[index.get(q).intValue()] = true; // fallthrough leader
            } else if (p instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode ts = (TableSwitchInsnNode)p;
                targetLabels.add(ts.dflt);
                targetLabels.addAll(ts.labels);
                AbstractInsnNode q = p.getNext();
                if (q != null) isLeader[index.get(q).intValue()] = true;
            } else if (p instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode ls = (LookupSwitchInsnNode)p;
                targetLabels.add(ls.dflt);
                targetLabels.addAll(ls.labels);
                AbstractInsnNode q = p.getNext();
                if (q != null) isLeader[index.get(q).intValue()] = true;
            } else if (isTerminal(op)) {
                AbstractInsnNode q = p.getNext();
                if (q != null) isLeader[index.get(q).intValue()] = true;
            }
        }
        for (LabelNode l : targetLabels) {
            Integer li = index.get(l);
            if (li != null) isLeader[li.intValue()] = true;
        }
        // Exception handler starts are leaders
        Set<Integer> handlerStarts = new HashSet<Integer>();
        if (method.tryCatchBlocks != null) {
            for (Object o : method.tryCatchBlocks) {
                TryCatchBlockNode t = (TryCatchBlockNode) o;
                Integer hi = index.get(t.handler);
                if (hi != null) {
                    isLeader[hi.intValue()] = true;
                    handlerStarts.add(hi);
                }
            }
        }

        // Create blocks by leader spans
        List<int[]> spans = new ArrayList<int[]>();
        for (int i = 0; i < idx; i++) {
            if (!isLeader[i]) continue;
            int start = i;
            int end = i;
            // span until the next leader or end
            int j = i+1;
            while (j < idx && !isLeader[j]) { end = j; j++; }
            spans.add(new int[]{start, end});
        }

        // Build Block objects, keyed by first-insn index (stable id)
        Map<Integer, Block> byStart = new HashMap<Integer, Block>();
        for (int[] sp : spans) {
            int start = sp[0], end = sp[1];
            boolean isHandler = handlerStarts.contains(Integer.valueOf(start));
            Block b = new Block(start, start, end, isHandler);
            byStart.put(Integer.valueOf(start), b);
            blocks.put(b.id, b);
        }

        // Build successor edges (control flow)
        for (Block b : blocks()) {
            AbstractInsnNode last = insnAt(insns, b.endIdx);
            int op = last == null ? -1 : last.getOpcode();

            if (last instanceof JumpInsnNode) {
                int targetId = blockIdOfLabel(byStart, index, ((JumpInsnNode) last).label);
                addSucc(b, targetId);
                if (op != GOTO) {
                    // conditional branch has fallthrough
                    Integer fall = nextBlockStartAfter(byStart, b.endIdx);
                    if (fall != null) addSucc(b, fall.intValue());
                }
            } else if (last instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode ts = (TableSwitchInsnNode) last;
                addSucc(b, blockIdOfLabel(byStart, index, ts.dflt));
                for (Object o : ts.labels) {
                    addSucc(b, blockIdOfLabel(byStart, index, (LabelNode) o));
                }
            } else if (last instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode ls = (LookupSwitchInsnNode) last;
                addSucc(b, blockIdOfLabel(byStart, index, ls.dflt));
                for (Object o : ls.labels) {
                    addSucc(b, blockIdOfLabel(byStart, index, (LabelNode) o));
                }
            } else if (!isTerminal(op)) {
                Integer fall = nextBlockStartAfter(byStart, b.endIdx);
                if (fall != null) addSucc(b, fall.intValue());
            }
        }

        // Exception edges (policy: LOOSE by default)
        addExceptionEdges(byStart, index, policy);
        // Build preds
        rebuildPreds();
    }

    private void addExceptionEdges(Map<Integer, Block> byStart, Map<AbstractInsnNode,Integer> index, ExceptionEdgePolicy policy) {
        if (method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty()) return;

        // Precompute block coverage for each try-catch
        for (Object o : method.tryCatchBlocks) {
            TryCatchBlockNode t = (TryCatchBlockNode) o;
            Integer s = index.get(t.start);
            Integer e = index.get(t.end);
            Integer h = index.get(t.handler);
            if (s == null || e == null || h == null) continue;

            // Blocks whose span intersects [s, e)
            for (Block b : blocks()) {
                if (rangeIntersects(b.startIdx, b.endIdx + 1, s.intValue(), e.intValue())) {
                    // STRICT policy could attempt thrown-type analysis; we keep conservative behavior here.
                    addSucc(b, h.intValue());
                }
            }
        }
    }

    private void dropUnreachable() {
        if (blocks.isEmpty()) return;
        // BFS/DFS from entry (smallest startIdx)
        int entryId = Integer.MAX_VALUE;
        for (Block b : blocks()) entryId = Math.min(entryId, b.id);
        IntOpenHashSet seen = new IntOpenHashSet();
        IntArrayList work = new IntArrayList();
        seen.add(entryId);
        work.add(entryId);
        while (!work.isEmpty()) {
            int id = work.removeInt(work.size()-1);
            Block b = blocks.get(id);
            for (int s : b.succs()) if (seen.add(s)) work.add(s);
        }
        // Remove blocks not seen
        IntArrayList toRemove = new IntArrayList();
        for (Block b : blocks()) {
            if (!seen.contains(b.id)) toRemove.add(b.id);
        }
        for (int i = 0; i < toRemove.size(); i++) {
            int id = toRemove.getInt(i);
            blocks.remove(id);
        }
        rebuildPreds();
    }

    private void mergeLinearChains() {
        // Merge only fallthrough chains: b has 1 succ s; s has 1 pred (b); and b's last insn is NOT a branch/switch/terminal.
        boolean changed;
        do {
            changed = false;
            // Iterate in ascending id order for determinism
            for (Block b : blocks()) {
                int[] succs = b.succs();
                if (succs.length != 1) continue;
                Block s = blocks.get(succs[0]);
                if (s == null) continue;
                if (s.preds().length != 1) continue;

                // Check b's last instruction is fallthrough-capable
                AbstractInsnNode last = method.instructions.get(b.endIdx);
                int op = last == null ? -1 : last.getOpcode();
                if (last instanceof JumpInsnNode || last instanceof TableSwitchInsnNode || last instanceof LookupSwitchInsnNode || isTerminal(op)) {
                    continue; // do not merge across explicit control transfers
                }
                // Do not merge handler starts to preserve handler identity
                if (b.isHandlerStart || s.isHandlerStart) continue;

                // Merge b and s into b: extend endIdx, redirect edges of s to b
                b.endIdx = s.endIdx;
                // Redirect successors of s to b (excluding b itself)
                IntArrayList newSuccs = new IntArrayList();
                for (int x : s.succs()) if (x != b.id) newSuccs.add(x);
                // Replace b.succs with merged successor list
                b.succs = newSuccs;

                // Remove s from graph
                blocks.remove(s.id);
                // Update preds of successors: rebuild later for simplicity
                rebuildPreds();

                changed = true;
                break; // restart iteration for determinism
            }
        } while (changed);
    }

    private void sortEdges() {
        for (Block b : blocks()) {
            b.succs = new IntArrayList(toSortedDistinctArray(b.succs));
        }
        rebuildPreds();
    }

    private void rebuildPreds() {
        // Clear preds
        for (Block b : blocks()) b.preds = new IntArrayList();
        // Fill preds from succs
        for (Block b : blocks()) {
            for (int s : b.succs()) {
                Block bb = blocks.get(s);
                if (bb != null) bb.preds.add(b.id);
            }
        }
        // Sort/dedupe
        for (Block b : blocks()) {
            b.preds = new IntArrayList(toSortedDistinctArray(b.preds));
        }
    }

    // ---- Helpers ----

    private static boolean isTerminal(int op) {
        switch (op) {
            case RETURN: case ARETURN: case IRETURN: case LRETURN: case FRETURN: case DRETURN:
            case ATHROW:
                return true;
            default:
                return false;
        }
    }

    private static int[] toSortedDistinctArray(IntArrayList list) {
        int[] a = list.toIntArray();
        Arrays.sort(a);
        int w = 0;
        for (int i=0;i<a.length;i++) {
            if (i==0 || a[i]!=a[i-1]) a[w++] = a[i];
        }
        return w==a.length ? a : Arrays.copyOf(a, w);
    }

    private static void addSucc(Block b, int targetId) {
        if (targetId < 0) return;
        b.succs.add(targetId);
    }

    private AbstractInsnNode insnAt(InsnList insns, int idx) {
        int i=0;
        for (AbstractInsnNode p = insns.getFirst(); p != null; p = p.getNext()) {
            if (i==idx) return p;
            i++;
        }
        return null;
    }

    private Integer nextBlockStartAfter(Map<Integer, Block> byStart, int insnIdx) {
        // Find the smallest block start strictly greater than insnIdx
        int best = Integer.MAX_VALUE;
        for (Integer s : byStart.keySet()) if (s.intValue() > insnIdx && s.intValue() < best) best = s.intValue();
        return best == Integer.MAX_VALUE ? null : Integer.valueOf(best);
    }

    private int blockIdOfLabel(Map<Integer, Block> byStart, Map<AbstractInsnNode,Integer> index, LabelNode l) {
        Integer li = index.get(l);
        if (li == null) return -1;
        Block b = byStart.get(li);
        return b != null ? b.id : -1;
    }

    private static boolean rangeIntersects(int aStart, int aEnd, int bStart, int bEnd) {
        // Intersect [aStart, aEnd) with [bStart, bEnd)
        return aStart < bEnd && bStart < aEnd;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER ReducedCFG END
