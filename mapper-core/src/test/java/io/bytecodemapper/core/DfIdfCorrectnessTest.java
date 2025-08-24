// >>> AUTOGEN: BYTECODEMAPPER CORE TEST DF IDF BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.testutil.AsmSynth;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class DfIdfCorrectnessTest {
    @Test(timeout = 20000)
    public void dominanceFrontierMergesAtJoin() {
        // if-else diamond with join
        ReducedCFG g = ReducedCFG.build(AsmSynth.diamondNoFold());
        Dominators dom = Dominators.compute(g);
        Map<Integer,int[]> df = DF.compute(g, dom);
        // Find join (block with >=2 preds)
        int join = -1;
        for (ReducedCFG.Block b : g.blocks()) if (b.preds().length >= 2) { join = b.id; break; }
        assertTrue(join != -1);
        // For each pred p of join, check join in DF(p)
        int[] preds = g.block(join).preds();
        for (int p : preds) {
            int[] set = df.get(p);
            boolean has = false;
            for (int x : set) if (x == join) { has = true; break; }
            assertTrue("DF("+p+") should contain join "+join, has);
        }
    }

    @Test(timeout = 20000)
    public void iteratedFrontierPropagates() {
        ReducedCFG g = ReducedCFG.build(AsmSynth.loopNestedNoFold());
        Dominators dom = Dominators.compute(g);
        Map<Integer,int[]> df = DF.compute(g, dom);
        Map<Integer,int[]> idf = DF.iterateToFixpoint(df);
        // IDF should not be all empty for a nested-loop graph
        boolean anyNonEmpty = false;
        for (int k : idf.keySet()) if (idf.get(k).length > 0) { anyNonEmpty = true; break; }
        assertTrue(anyNonEmpty);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER CORE TEST DF IDF END
