// >>> AUTOGEN: BYTECODEMAPPER TEST MapOldNewAcceptanceMarginTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MapOldNewAcceptanceMarginTest {

    private static Path runOnce(double tau, double margin, String outName) throws Exception {
        File cwd = new File(".").getCanonicalFile();
        File repoRoot = cwd.getName().equals("mapper-cli") && cwd.getParentFile() != null ? cwd.getParentFile() : cwd;
        Path outMappings = new File(repoRoot, "mapper-cli/build/" + outName).toPath();
        Files.createDirectories(outMappings.getParent());
        Files.deleteIfExists(outMappings);
    String[] args = new String[] {
                "mapOldNew",
                "--old", "testData/jars/old.jar",
                "--new", "testData/jars/new.jar",
                "--out", outMappings.toString(),
                "--tauAcceptMethods", String.valueOf(tau),
                "--marginMethods", String.valueOf(margin),
        "--deterministic",
        "--maxMethods", "250"
        };
        Main.main(args);
        return outMappings;
    }

    private static int countLines(Path p) throws Exception {
        int n = 0; for (String s : Files.readAllLines(p)) if (!s.isEmpty() && s.charAt(0) == 'm' && s.startsWith("m\t")) n++;
        return n;
    }

    @Test
    public void stricterThresholdsReduceAcceptances() throws Exception {
        Path p1 = runOnce(0.50, 0.01, "acc-m1.tiny");
        Path p2 = runOnce(0.70, 0.10, "acc-m2.tiny");
        assertTrue(Files.isRegularFile(p1));
        assertTrue(Files.isRegularFile(p2));
        int m1 = countLines(p1);
        int m2 = countLines(p2);
        // With stricter thresholds, acceptances should not increase
        assertTrue("m2 (stricter) should be <= m1", m2 <= m1);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MapOldNewAcceptanceMarginTest END
