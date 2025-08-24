// >>> AUTOGEN: BYTECODEMAPPER TEST AcceptanceMarginMicroIT BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class AcceptanceMarginMicroIT {

    @Test
    public void marginJustBelow_causesAbstentionTelemetry() throws Exception {
        java.io.PrintStream old = System.out;
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(1<<20);
        java.io.PrintStream ps = new java.io.PrintStream(bout,true,"UTF-8");
        System.setOut(ps);
        try {
            io.bytecodemapper.cli.Main.main(new String[]{
                "mapOldNew",
                "--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
                "--out","build/test-acc-micro/out.tiny",
                "--deterministic",
                "--tauAcceptMethods","0.60",
                "--marginMethods","0.0499",
                "--debug-stats","--debug-sample","16","--maxMethods","200"
            });
        } finally {
            System.setOut(old);
            ps.flush(); ps.close();
        }
        String s = new String(bout.toByteArray(), "UTF-8");
        // Expect some abstentions logged when margin is just under 0.05 (behavioral, not exact count)
        boolean hasAbstain = s.contains("reason=low_margin") || s.contains("abstain");
        assertTrue("Expected abstention due to low margin just below threshold", hasAbstain);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST AcceptanceMarginMicroIT END
