// >>> AUTOGEN: BYTECODEMAPPER TEST ReducedCfgDomDfTest BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.hash.StableHash64;
import io.bytecodemapper.core.testutil.AsmSynth;
import org.junit.Test;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

import static org.junit.Assert.*;

public class ReducedCfgDomDfTest {

    private static String serializeDf(Map<Integer, int[]> df) {
        StringBuilder sb = new StringBuilder();
        List<Integer> keys = new ArrayList<Integer>(df.keySet());
        Collections.sort(keys);
        for (int k : keys) {
            sb.append(k).append(':');
            int[] v = df.get(k);
            for (int x : v) { sb.append(x).append(','); }
            sb.append('|');
        }
        return sb.toString();
    }

    @Test
    public void singleBlock() {
        MethodNode mn = AsmSynth.singleBlock();
        ReducedCFG cfg = ReducedCFG.build(mn);
        assertEquals(1, cfg.blocks().size());
        Dominators dom = Dominators.compute(cfg);
        for (Block b : cfg.blocks()) {
            assertEquals(b.id, dom.idom(b.id)); // entry idom = self
            assertEquals(0, dom.domDepth(b.id));
            assertArrayEquals(new int[0], DF.compute(cfg, dom).get(b.id));
        }
    }

    @Test
    public void diamondDf() {
    MethodNode mn = AsmSynth.diamondNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
        Map<Integer, int[]> df = DF.compute(cfg, dom);

        // Find join block = the one with 2 preds and is target of both branches
        int join = -1;
        for (Block b : cfg.blocks()) if (b.preds().length >= 2) { join = b.id; break; }
        assertTrue(join != -1);

        // All predecessors of the join should have DF containing the join
        Block joinBlock = cfg.block(join);
        for (int p : joinBlock.preds()) {
            int[] d = df.get(p);
            Arrays.sort(d);
            assertTrue("DF("+p+") should contain join "+join, Arrays.binarySearch(d, join) >= 0);
        }
    }

    @Test
    public void simpleLoop() {
    MethodNode mn = AsmSynth.loopSimpleNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
        Map<Integer,int[]> df = DF.compute(cfg, dom);

        // Relaxed check: at least one node has DF containing the header
        // Identify header as block with pred from a later block (back-edge)
        int header = -1;
        for (Block b : cfg.blocks()) {
            for (int p : b.preds()) {
                if (p > b.id) { header = b.id; break; }
            }
            if (header != -1) break;
        }
        assertTrue(header != -1);
        boolean found = false;
        for (int k : df.keySet()) for (int x : df.get(k)) if (x == header) found = true;
        assertTrue(found);
    }

    @Test
    public void nestedLoops() {
    MethodNode mn = AsmSynth.loopNestedNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
        Map<Integer,int[]> df = DF.compute(cfg, dom);
        assertNotNull(df);
        // At least two nodes should have non-empty DF (join points)
        int nonEmpty=0;
        for (int k : df.keySet()) if (df.get(k).length>0) nonEmpty++;
        assertTrue(nonEmpty >= 2);
    }

    @Test
    public void tryCatchAddsHandlerEdge() {
        MethodNode mn = AsmSynth.tryCatch();
        ReducedCFG cfg = ReducedCFG.build(mn);
        boolean hasHandler = false;
        for (Block b : cfg.blocks()) {
            for (int s : b.succs()) {
                Block h = cfg.block(s);
                if (h != null && h.isHandlerStart) hasHandler = true;
            }
        }
        assertTrue(hasHandler);
    }

    @Test
    public void tableSwitchEdges() {
    MethodNode mn = AsmSynth.tableSwitchNoFold();
        ReducedCFG cfg = ReducedCFG.build(mn);
        // Expect at least 3 successors from the switch block (2 cases + default)
        int maxSucc = 0;
        for (Block b : cfg.blocks()) maxSucc = Math.max(maxSucc, b.succs().length);
        assertTrue(maxSucc >= 3);
    }

    @Test
    public void dfDeterminismHashTwice() {
        MethodNode mn = AsmSynth.diamond();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);

        Map<Integer,int[]> df1 = DF.compute(cfg, dom);
        Map<Integer,int[]> df2 = DF.compute(cfg, dom);

        String s1 = serializeDf(df1);
        String s2 = serializeDf(df2);

        long h1 = StableHash64.hashUtf8(s1);
        long h2 = StableHash64.hashUtf8(s2);

        System.out.println("DF hash run1=" + h1 + " run2=" + h2);
        assertEquals(h1, h2);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST ReducedCfgDomDfTest END
