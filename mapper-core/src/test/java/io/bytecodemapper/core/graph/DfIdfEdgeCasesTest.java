// >>> AUTOGEN: BYTECODEMAPPER TEST DF_IDF_EDGE_CASES BEGIN
package io.bytecodemapper.core.graph;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.testutil.AsmSynth;

public class DfIdfEdgeCasesTest {

    @Test
    public void dfIdf_handles_loop() {
        // Use non-folding synthetic loop to ensure CFG retains back-edge
        ReducedCFG cfg = ReducedCFG.build(AsmSynth.loopSimpleNoFold());
        Dominators dom = Dominators.compute(cfg);
        Map<Integer,int[]> df = DF.compute(cfg, dom);
        Map<Integer,int[]> idf = DF.iterateToFixpoint(df);

        // Sanity: some DF/IDF should be non-empty due to loop structure
        boolean nonEmpty = false;
        for (int b : df.keySet()) {
            if ((df.get(b) != null && df.get(b).length > 0) || (idf.get(b) != null && idf.get(b).length > 0)) {
                nonEmpty = true; break;
            }
        }
        assertTrue("Expected non-empty DF/IDF in presence of a loop", nonEmpty);
    }

    @Test
    public void dfIdf_handles_trycatch_and_switch() {
        // try/catch: handler edge should exist; DF should compute without throwing
        ReducedCFG cfgTry = ReducedCFG.build(AsmSynth.tryCatch());
        boolean hasHandler = false;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfgTry.blocks()) {
            for (int s : b.succs()) {
                io.bytecodemapper.core.cfg.ReducedCFG.Block bb = cfgTry.block(s);
                if (bb != null && bb.isHandlerStart) { hasHandler = true; break; }
            }
            if (hasHandler) break;
        }
        assertTrue("Expected an exception handler successor edge", hasHandler);
        Dominators dt = Dominators.compute(cfgTry);
        Map<Integer,int[]> dft = DF.compute(cfgTry, dt);
        assertNotNull(dft);

        // table switch: expect >=3 outgoing edges from switch block; DF should compute
        ReducedCFG cfgSw = ReducedCFG.build(AsmSynth.tableSwitchNoFold());
        int maxSucc = 0;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfgSw.blocks()) maxSucc = Math.max(maxSucc, b.succs().length);
        assertTrue("Expected at least 3 successors from a tableswitch block", maxSucc >= 3);
        Dominators ds = Dominators.compute(cfgSw);
        Map<Integer,int[]> dfs = DF.compute(cfgSw, ds);
        assertNotNull(dfs);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST DF_IDF_EDGE_CASES END
