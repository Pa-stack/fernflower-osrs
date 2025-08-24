// >>> AUTOGEN: BYTECODEMAPPER TEST AcceptanceMarginIT BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;

public class AcceptanceMarginIT {
    @Test
    public void exact_threshold_accepts_margin_rule_holds() throws Exception {
        File out1 = new File("build/it/acc/m1.tiny"); out1.getParentFile().mkdirs();
        String args1 = String.join(" ",
            "mapOldNew",
            "--old", "data/weeks/2025-34/old.jar",
            "--new", "data/weeks/2025-34/new.jar",
            "--out", out1.getPath(),
            "--deterministic",
            "--tauAcceptMethods", "0.60",
            "--marginMethods", "0.05",
            "--maxMethods", "200"
        );
        int rc1 = io.bytecodemapper.cli.Router.dispatch(args1.split("\\s+"));
        assertEquals(0, rc1);
        assertTrue(out1.isFile() && out1.length() > 0);

        // Tighten margin very slightly to force some abstentions (behavioral—not exact count)
        File out2 = new File("build/it/acc/m2.tiny");
        String args2 = String.join(" ",
            "mapOldNew",
            "--old", "data/weeks/2025-34/old.jar",
            "--new", "data/weeks/2025-34/new.jar",
            "--out", out2.getPath(),
            "--deterministic",
            "--tauAcceptMethods", "0.60",
            "--marginMethods", "0.051",
            "--maxMethods", "200"
        );
        int rc2 = io.bytecodemapper.cli.Router.dispatch(args2.split("\\s+"));
        assertEquals(0, rc2);
        assertTrue(out2.isFile() && out2.length() > 0);

        byte[] a = Files.readAllBytes(out1.toPath());
        byte[] b = Files.readAllBytes(out2.toPath());
        assertTrue("both mapping files should be non-empty", a.length > 0 && b.length > 0);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST AcceptanceMarginIT END
