// >>> AUTOGEN: BYTECODEMAPPER TEST WL_STABILITY BEGIN
package io.bytecodemapper.core;

import org.junit.Test;
import static org.junit.Assert.*;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.testutil.AsmSynth;
import io.bytecodemapper.core.wl.WLRefinement;

import java.util.Map;

public class WLStabilityTest {

    @Test
    public void wl_has_same_signature_under_noop_reordering() {
        ReducedCFG cfg1 = ReducedCFG.build(AsmSynth.singleBlockTwoNopsA());
        ReducedCFG cfg2 = ReducedCFG.build(AsmSynth.singleBlockTwoNopsB());

        Dominators d1 = Dominators.compute(cfg1);
        Dominators d2 = Dominators.compute(cfg2);

        Map<Integer,int[]> df1 = DF.compute(cfg1, d1);
        Map<Integer,int[]> idf1 = DF.iterateToFixpoint(df1);
        Map<Integer,int[]> df2 = DF.compute(cfg2, d2);
        Map<Integer,int[]> idf2 = DF.iterateToFixpoint(df2);

        WLRefinement.MethodSignature s1 = WLRefinement.computeSignature(cfg1, d1, df1, idf1, WLRefinement.DEFAULT_K);
        WLRefinement.MethodSignature s2 = WLRefinement.computeSignature(cfg2, d2, df2, idf2, WLRefinement.DEFAULT_K);
        assertEquals("WL signature must be stable under harmless reordering (hash)", s1.hash, s2.hash);
        assertEquals(s1.blockCount, s2.blockCount);
        assertEquals(s1.loopCount, s2.loopCount);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST WL_STABILITY END
