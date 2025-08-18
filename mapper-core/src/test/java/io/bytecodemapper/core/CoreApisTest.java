// >>> AUTOGEN: BYTECODEMAPPER CoreApisTest BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.wl.WLRefinement;
import org.junit.Test;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

import static org.junit.Assert.assertNotNull;

public class CoreApisTest {
    @Test
    public void apiSurfacesExist() {
        MethodNode mn = new MethodNode();
        ReducedCFG cfg = ReducedCFG.build(mn);
        Dominators dom = Dominators.compute(cfg);
    Map<Integer, int[]> df = DF.compute(cfg, dom);
    Map<Integer, int[]> tdf = DF.iterateToFixpoint(df);
        Map<Integer, Long> labels = WLRefinement.refineLabels(
                Arrays.asList(1,2,3),
                new HashMap<Integer, List<Integer>>(),
                new HashMap<Integer, List<Integer>>(),
                new HashMap<Integer, Long>(),
                3);
        assertNotNull(cfg);
        assertNotNull(dom);
        assertNotNull(df);
        assertNotNull(tdf);
        assertNotNull(labels);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CoreApisTest END
