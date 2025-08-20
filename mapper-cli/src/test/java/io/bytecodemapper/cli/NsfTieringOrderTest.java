// >>> AUTOGEN: BYTECODEMAPPER TEST NSF TIERING BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;

public class NsfTieringOrderTest {
    @Test public void nsfTierOrderFlagIsRecognizedAndDeterministic() throws Exception {
        Path dir = Paths.get("build/test-nsf-tier");
        Files.createDirectories(dir);
        Path outA = dir.resolve("tier-a.tiny");
        Path outB = dir.resolve("tier-b.tiny");

        String tier = "exact,near,wl,wlrelaxed";
        io.bytecodemapper.cli.Main.main(new String[]{
            "mapOldNew",
            "--old","data/weeks/osrs-170.jar","--new","data/weeks/osrs-171.jar",
            "--out",outA.toString(),
            "--nsf-tier-order", tier,
            "--deterministic","--debug-sample","24","--maxMethods","300"
        });
        assertTrue(Files.size(outA) > 0);

        io.bytecodemapper.cli.Main.main(new String[]{
            "mapOldNew",
            "--old","data/weeks/osrs-170.jar","--new","data/weeks/osrs-171.jar",
            "--out",outB.toString(),
            "--nsf-tier-order", tier,
            "--deterministic","--debug-sample","24","--maxMethods","300"
        });
        assertEquals(Files.size(outA), Files.size(outB));
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST NSF TIERING END
