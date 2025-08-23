package io.bytecodemapper.core.df;

import io.bytecodemapper.core.cfg.ReducedCFG;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/** Guard regression: DF runner walk must not spin on idom self-loops. */
public class DFGuardsTest implements Opcodes {

    /** Build a tiny diamond CFG: entry -> {L2, L1} -> join. */
    private static MethodNode diamond() {
        MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_STATIC, "m", "()V", null, null);
        InsnList ins = mn.instructions;
        LabelNode L1 = new LabelNode();
        LabelNode L2 = new LabelNode();
        LabelNode L3 = new LabelNode(); // join
        ins.add(new InsnNode(ICONST_0));                 // 0
        ins.add(new JumpInsnNode(IFEQ, L1));             // 1 -> L1
        ins.add(L2);                                     // 2
        ins.add(new InsnNode(NOP));                      // 3
        ins.add(new JumpInsnNode(GOTO, L3));             // 4 -> L3
        ins.add(L1);                                     // 5
        ins.add(new InsnNode(NOP));                      // 6
        ins.add(L3);                                     // 7
        ins.add(new InsnNode(RETURN));                   // 8
        mn.maxStack = 1;
        mn.maxLocals = 0;
        return mn;
    }

    @Test(timeout = 5000)
    public void df_guard_breaks_self_loop_runner() {
        ReducedCFG cfg = ReducedCFG.build(diamond());
        int[] ids = cfg.allBlockIds();
        Assert.assertTrue("expect at least 3 blocks", ids.length >= 3);

        // Find entry, join (degIn>=2), and a non-entry predecessor of join.
        int entry = ids[0];
        int join = -1;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfg.blocks()) {
            if (b.preds().length >= 2) { join = b.id; break; }
        }
        Assert.assertTrue("join not found", join != -1);

        int pathPred = -1;
        for (int p : cfg.block(join).preds()) {
            if (p != entry) { pathPred = p; break; }
        }
        Assert.assertTrue("path predecessor not found", pathPred != -1);

        // idom function: idom(join)=entry; idom(pathPred)=pathPred (self-loop);
        // entry dominates itself; others default to entry.
        final int joinId = join;
        final int pathPredId = pathPred;
        final int entryId = entry;
        IntUnaryOperator idomFn = new IntUnaryOperator() {
            @Override public int applyAsInt(int x) {
                if (x == joinId) return entryId;
                if (x == pathPredId) return pathPredId; // <-- triggers DF runner no-progress guard
                if (x == entryId) return entryId;
                return entryId;
            }
        };

        Map<Integer,int[]> df = DF.computeWithIdomFunc(cfg, idomFn);
        Assert.assertNotNull(df);
        Assert.assertTrue("df map should have entries", df.size() >= 1);

        // Also make sure the TDF fixpoint converges quickly on the guarded DF.
        Map<Integer,int[]> tdf = DF.iterateToFixpoint(df);
        Assert.assertNotNull(tdf);
        Assert.assertEquals("TDF should have same key set size", df.size(), tdf.size());
    }
}
