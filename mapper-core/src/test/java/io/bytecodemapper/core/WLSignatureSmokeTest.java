// >>> AUTOGEN: BYTECODEMAPPER CORE TEST WL SIGNATURE BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.testutil.AsmSynth;
import io.bytecodemapper.core.wl.WLRefinement;
import org.junit.Test;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static org.junit.Assert.*;

public class WLSignatureSmokeTest {
    @Test
    public void wlStableUnderNoopReorderings() {
        MethodNode a = AsmSynth.singleBlockTwoNopsA();
        MethodNode b = AsmSynth.singleBlockTwoNopsB();

        ReducedCFG cfga = ReducedCFG.build(a);
        ReducedCFG cfgb = ReducedCFG.build(b);
        Dominators doma = Dominators.compute(cfga);
        Dominators domb = Dominators.compute(cfgb);

        Map<Integer,int[]> dfa  = DF.compute(cfga, doma);
        Map<Integer,int[]> tdfa = DF.iterateToFixpoint(dfa);
        Map<Integer,int[]> dfb  = DF.compute(cfgb, domb);
        Map<Integer,int[]> tdfb = DF.iterateToFixpoint(dfb);

        WLRefinement.MethodSignature sa = WLRefinement.computeSignature(cfga, doma, dfa, tdfa, 4);
        WLRefinement.MethodSignature sb = WLRefinement.computeSignature(cfgb, domb, dfb, tdfb, 4);
        assertEquals(sa.hash, sb.hash);
    }

    @Test
    public void wlDiffersOnExtraBranch() {
        MethodNode base = AsmSynth.singleBlock();
        MethodNode branchy = AsmSynth.diamondNoFold();

        ReducedCFG cfg0 = ReducedCFG.build(base);
        ReducedCFG cfg1 = ReducedCFG.build(branchy);
        Dominators dom0 = Dominators.compute(cfg0);
        Dominators dom1 = Dominators.compute(cfg1);

        Map<Integer,int[]> df0  = DF.compute(cfg0, dom0);
        Map<Integer,int[]> tdf0 = DF.iterateToFixpoint(df0);
        Map<Integer,int[]> df1  = DF.compute(cfg1, dom1);
        Map<Integer,int[]> tdf1 = DF.iterateToFixpoint(df1);

        WLRefinement.MethodSignature s0 = WLRefinement.computeSignature(cfg0, dom0, df0, tdf0, 4);
        WLRefinement.MethodSignature s1 = WLRefinement.computeSignature(cfg1, dom1, df1, tdf1, 4);
        assertNotEquals(s0.hash, s1.hash);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER CORE TEST WL SIGNATURE END
