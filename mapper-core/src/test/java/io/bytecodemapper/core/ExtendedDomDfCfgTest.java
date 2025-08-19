// >>> AUTOGEN: BYTECODEMAPPER CORE TEST ExtendedDomDfCfgTest BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.testutil.AsmSynth;
import org.junit.Test;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static org.junit.Assert.*;

public class ExtendedDomDfCfgTest {

    @Test(timeout = 5000)
    public void diamond_hasJoinAndFrontier() {
        MethodNode mn = AsmSynth.diamondNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
        Map<Integer, int[]> df = DF.compute(cfg, dom);

        // expect at least 3 blocks (entry, branch, join)
        assertTrue(cfg.blocks().size() >= 3);

        // find join: a node with indegree >= 2
        int join = -1;
        for (ReducedCFG.Block b : cfg.blocks()) {
            if (b.preds().length >= 2) { join = b.id; break; }
        }
        assertTrue("diamond join not found", join != -1);

        // Cytron DF property: each predecessor of the join has the join in its DF
        ReducedCFG.Block joinBlock = cfg.block(join);
        for (int p : joinBlock.preds()) {
            int[] d = df.get(p);
            java.util.Arrays.sort(d);
            assertTrue("DF("+p+") should contain join "+join, java.util.Arrays.binarySearch(d, join) >= 0);
        }

        // Sanity: at least one block has a non-empty DF
        boolean anyNonEmpty = false;
        for (int[] arr : df.values()) { if (arr.length > 0) { anyNonEmpty = true; break; } }
        assertTrue(anyNonEmpty);
    }

    @Test(timeout = 5000)
    public void loops_haveBackEdgeAndNonEmptyTdf() {
        MethodNode mn = AsmSynth.loopNestedNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
        Map<Integer, int[]> df = DF.compute(cfg, dom);
        Map<Integer, int[]> tdf = DF.iterateToFixpoint(df);

        boolean hasBackEdge = false;
        for (ReducedCFG.Block b : cfg.blocks()) {
            for (int s : b.succs()) {
                if (dom.dominates(s, b.id)) hasBackEdge = true;
            }
        }
        assertTrue("no back-edge detected", hasBackEdge);
        // nested loops should produce some non-empty transitive DFs
        int tdfNonEmpty = 0;
        for (int[] arr : tdf.values()) if (arr.length > 0) tdfNonEmpty++;
        assertTrue(tdfNonEmpty >= 1);
    }

    @Test(timeout = 5000)
    public void tryCatchAndSwitch_dontExplodeAndHaveStableCounts() {
        MethodNode tryCatch = AsmSynth.tryCatch(); // use existing helper
        MethodNode sw = AsmSynth.tableSwitchNoFold();

        ReducedCFG cfg1 = ReducedCFG.build(tryCatch);
        ReducedCFG cfg2 = ReducedCFG.build(sw);

        Dominators d1 = Dominators.compute(cfg1);
        Dominators d2 = Dominators.compute(cfg2);

        Map<Integer, int[]> df1 = DF.compute(cfg1, d1);
        Map<Integer, int[]> df2 = DF.compute(cfg2, d2);

        // sanity: graphs have at least entry/exit-ish blocks
        assertTrue(cfg1.blocks().size() >= 2);
        assertTrue(cfg2.blocks().size() >= 2);

        // DF maps contain entries for all blocks
        assertEquals(cfg1.blocks().size(), df1.size());
        assertEquals(cfg2.blocks().size(), df2.size());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CORE TEST ExtendedDomDfCfgTest END
