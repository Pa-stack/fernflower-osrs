// >>> AUTOGEN: BYTECODEMAPPER TEST WLTokenBagDeterminismTest BEGIN
package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.testutil.AsmSynth;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.tree.MethodNode;

public class WLTokenBagDeterminismTest {

    @Test
    public void identicalGraphsDifferentInsertionOrders_produceSameBag() {
        MethodNode a = AsmSynth.singleBlockTwoNopsA();
        MethodNode b = AsmSynth.singleBlockTwoNopsB();

        ReducedCFG cfga = ReducedCFG.build(a);
        ReducedCFG cfgb = ReducedCFG.build(b);
        Dominators doma = Dominators.compute(cfga);
        Dominators domb = Dominators.compute(cfgb);

    Long2IntSortedMap bagA = WLRefinement.tokenBagFinal(cfga, doma, WLRefinement.DEFAULT_K, true);
    Long2IntSortedMap bagB = WLRefinement.tokenBagFinal(cfgb, domb, WLRefinement.DEFAULT_K, true);

        Assert.assertEquals("Token bags must match for identical CFGs regardless of insertion order", bagA, bagB);
    }

    @Test
    public void minorRefactor_changesBagBySmallL1() {
        MethodNode m0 = AsmSynth.diamond();
        MethodNode m1 = AsmSynth.diamondConst1();

        ReducedCFG cfg0 = ReducedCFG.build(m0);
        ReducedCFG cfg1 = ReducedCFG.build(m1);
        Dominators dom0 = Dominators.compute(cfg0);
        Dominators dom1 = Dominators.compute(cfg1);

    Long2IntSortedMap bag0 = WLRefinement.tokenBagFinal(cfg0, dom0, WLRefinement.DEFAULT_K, true);
    Long2IntSortedMap bag1 = WLRefinement.tokenBagFinal(cfg1, dom1, WLRefinement.DEFAULT_K, true);

        int l1 = l1Distance(bag0, bag1);
        // Typical small refactor difference: allow up to 2
        Assert.assertTrue("Expected small L1 distance (<=2), was " + l1, l1 <= 2);
    }

    private static int l1Distance(Long2IntSortedMap a, Long2IntSortedMap b) {
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ia = a.long2IntEntrySet().iterator();
        java.util.Iterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> ib = b.long2IntEntrySet().iterator();
        long ka = Long.MIN_VALUE, kb = Long.MIN_VALUE;
        int va = 0, vb = 0;
        boolean ha = ia.hasNext(), hb = ib.hasNext();
        if (ha) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ia.next(); ka = e.getLongKey(); va = e.getIntValue(); }
        if (hb) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ib.next(); kb = e.getLongKey(); vb = e.getIntValue(); }
        int sum = 0;
        while (ha || hb) {
            if (!hb || (ha && ka < kb)) {
                sum += java.lang.Math.abs(va);
                ha = ia.hasNext();
                if (ha) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ia.next(); ka = e.getLongKey(); va = e.getIntValue(); }
            } else if (!ha || (hb && kb < ka)) {
                sum += java.lang.Math.abs(vb);
                hb = ib.hasNext();
                if (hb) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ib.next(); kb = e.getLongKey(); vb = e.getIntValue(); }
            } else { // ka == kb
                sum += java.lang.Math.abs(va - vb);
                ha = ia.hasNext();
                hb = ib.hasNext();
                if (ha) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ia.next(); ka = e.getLongKey(); va = e.getIntValue(); }
                if (hb) { it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e = ib.next(); kb = e.getLongKey(); vb = e.getIntValue(); }
            }
        }
        return sum;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST WLTokenBagDeterminismTest END
