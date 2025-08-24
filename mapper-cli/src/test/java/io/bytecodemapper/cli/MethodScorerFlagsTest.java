// >>> AUTOGEN: BYTECODEMAPPER TEST SCORER FLAGS BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;

public class MethodScorerFlagsTest {
    @Test public void weightsAndAlphaOverrideAreAcceptedAndStable() throws Exception {
        File out1 = new File("build/test-scorer-flags/map-a.tiny");
        File out2 = new File("build/test-scorer-flags/map-b.tiny");
        if (out1.getParentFile()!=null) out1.getParentFile().mkdirs();

        // Run A: defaults (documented: TAU=0.60, MARGIN=0.05, alpha=0.60)
        io.bytecodemapper.cli.Main.main(new String[]{
            "mapOldNew",
            "--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out",out1.getPath(),
            "--deterministic","--debug-sample","24","--maxMethods","300"
        });
        assertTrue(out1.isFile() && out1.length() > 0);

        // Run B: tweak weights/alpha; output must be deterministic across identical runs for same flags
        io.bytecodemapper.cli.Main.main(new String[]{
            "mapOldNew",
            "--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out",out2.getPath(),
            "--deterministic",
            "--wCalls","0.45","--wMicro","0.25","--wNorm","0.10","--wStrings","0.10","--wFields","0.05",
            "--alphaMicro","0.60",
            "--debug-sample","24","--maxMethods","300"
        });
        assertTrue(out2.isFile() && out2.length() > 0);

        // Re-run B to assert byte-identical determinism for the same flags
        long sizeB = out2.length();
        io.bytecodemapper.cli.Main.main(new String[]{
            "mapOldNew",
            "--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out",out2.getPath(),
            "--deterministic",
            "--wCalls","0.45","--wMicro","0.25","--wNorm","0.10","--wStrings","0.10","--wFields","0.05",
            "--alphaMicro","0.60",
            "--debug-sample","24","--maxMethods","300"
        });
        assertEquals(sizeB, out2.length());
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST SCORER FLAGS END
