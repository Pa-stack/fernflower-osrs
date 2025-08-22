// >>> AUTOGEN: BYTECODEMAPPER TEST DEBUG STATS BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class DebugStatsTelemetryTest {
    @Test public void debugStatsEmitsDecisionLines() throws Exception {
        java.io.File out = new java.io.File("build/test-debug-stats/out.tiny");
        if (out.getParentFile()!=null) out.getParentFile().mkdirs();
        // Capture stdout while running mapOldNew
        java.io.PrintStream old = System.out;
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(1<<20);
        java.io.PrintStream ps = new java.io.PrintStream(bout,true,"UTF-8");
        System.setOut(ps);
        try {
            io.bytecodemapper.cli.Main.main(new String[]{
                "mapOldNew",
                "--old","data/weeks/osrs-170.jar","--new","data/weeks/osrs-171.jar",
                "--out", out.getPath(),
                "--deterministic","--debug-stats","--debug-sample","16","--maxMethods","200"
            });
        } finally {
            System.setOut(old);
            ps.flush(); ps.close();
        }
    String s = new String(bout.toByteArray(), "UTF-8");
    boolean hasDecision = s.contains("[match]") || s.contains("reason=");
    assertTrue("Expected at least one telemetry line in --debug-stats output", hasDecision);
    // Assert presence of key per-signal tokens to ensure telemetry includes subscores
    assertTrue("calls token missing", s.contains("calls="));
    assertTrue("micro token missing", s.contains("micro="));
    // accept either historical 'hist=' or newer 'norm=' token
    assertTrue("norm/hist token missing",  s.contains("norm=") || s.contains("hist="));
    assertTrue("str token missing",   s.contains("str="));
        java.nio.file.Path log = java.nio.file.Paths.get("build/test-debug-stats/telemetry.txt");
        java.nio.file.Files.createDirectories(log.getParent());
        java.nio.file.Files.write(log, s.getBytes("UTF-8"));
        assertTrue(java.nio.file.Files.size(log) > 0);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST DEBUG STATS END
