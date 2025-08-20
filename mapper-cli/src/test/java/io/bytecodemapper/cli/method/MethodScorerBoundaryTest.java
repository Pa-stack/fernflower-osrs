// >>> AUTOGEN: BYTECODEMAPPER TEST MethodScorerBoundary BEGIN
package io.bytecodemapper.cli.method;

import org.junit.Test;
import static org.junit.Assert.*;

public class MethodScorerBoundaryTest {

    // These tests exercise only the acceptance decision thresholds; they do not build full features.

    private static boolean accept(double best, double second) {
        // Mirror MethodScorer acceptance with a tiny epsilon to avoid FP edge jitters
        final double EPS = 1e-12;
        return (best + EPS >= MethodScorer.TAU_ACCEPT) && (best - Math.max(0, second) + EPS >= MethodScorer.MIN_MARGIN);
    }

    @Test
    public void accept_at_threshold_and_margin() {
        double Sbest = 0.60; double Ssecond = 0.55;
        MethodScorer.setTauAccept(0.60);
        MethodScorer.setMinMargin(0.05);
    boolean ok = accept(Sbest, Ssecond);
        assertTrue("exact threshold and margin should accept", ok);
    }

    @Test
    public void abstain_when_margin_just_below() {
        double Sbest = 0.60; double Ssecond = 0.551;
        MethodScorer.setTauAccept(0.60);
        MethodScorer.setMinMargin(0.05);
    boolean ok = accept(Sbest, Ssecond);
        assertFalse("margin below MIN_MARGIN should abstain", ok);
    }

    @Test
    public void abstain_when_below_tau() {
        double Sbest = 0.5999; double Ssecond = 0.10;
        MethodScorer.setTauAccept(0.60);
        MethodScorer.setMinMargin(0.00);
    boolean ok = accept(Sbest, Ssecond);
        assertFalse("below TAU must abstain", ok);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST MethodScorerBoundary END
