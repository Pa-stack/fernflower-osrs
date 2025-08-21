// >>> AUTOGEN: BYTECODEMAPPER TEST Phase4FlagsParseTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class Phase4FlagsParseTest {
    @Test
    public void flagsAccepted() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("tiny", ".out");
        tmp.deleteOnExit();
        String[] args = new String[]{
                "mapOldNew",
                "--old","data/weeks/osrs-170.jar",
                "--new","data/weeks/osrs-171.jar",
                "--out", tmp.getAbsolutePath(),
                "--deterministic",
                "--maxMethods","10",
                "--nsf-near","3",
                "--stack-cos","0.50"
        };
        try {
            Main.main(args);
        } catch (Throwable t) {
            fail("flags should be accepted without parse errors: " + t.getMessage());
        }
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST Phase4FlagsParseTest END
