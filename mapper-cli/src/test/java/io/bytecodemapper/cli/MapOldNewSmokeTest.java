// >>> AUTOGEN: BYTECODEMAPPER TEST MapOldNewSmokeTest HARDEN BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import io.bytecodemapper.cli.util.CliPaths;

public class MapOldNewSmokeTest {

    @Test
    public void mapOldNew_producesMappingsAndDebugDump_cwdIndependent() throws Exception {
    File outDir = CliPaths.resolveOutput("build/smoke/mapoldnew").toFile();
    File outMap = new File(outDir, "smoke-mappings.tiny");
    File outDbg = new File(outDir, "normalized-debug.txt");
        outDir.mkdirs();

        // Use fixture jars relative to repo, not cwd
    File oldJar = CliPaths.resolveInput("data/weeks/osrs-170.jar").toFile();
    File newJar = CliPaths.resolveInput("data/weeks/osrs-171.jar").toFile();
        assertTrue("Missing test jar: " + oldJar, oldJar.isFile());
        assertTrue("Missing test jar: " + newJar, newJar.isFile());

        String[] args = new String[] {
            "mapOldNew",
            "--old", oldJar.getPath(),
            "--new", newJar.getPath(),
            "--out", outMap.getPath(),
            "--deterministic",
            "--debug-normalized", outDbg.getPath(),
            "--debug-sample", "16",          // bounded output for CI
            "--maxMethods", "250"            // throttle for CI stability, optional
        };

        // Execute via Main; mapOldNew path does not call System.exit
        Main.main(args);

        assertTrue("mappings.tiny missing", outMap.isFile() && outMap.length() > 0);
        assertTrue("normalized debug missing", outDbg.isFile() && outDbg.length() > 0);

        // Determinism: re-run and compare bytes
        File outMap2 = new File(outDir, "smoke-mappings-2.tiny");
        File outDbg2 = new File(outDir, "normalized-debug-2.txt");

        String[] args2 = new String[] {
            "mapOldNew",
            "--old", oldJar.getPath(),
            "--new", newJar.getPath(),
            "--out", outMap2.getPath(),
            "--deterministic",
            "--debug-normalized", outDbg2.getPath(),
            "--debug-sample", "16",
            "--maxMethods", "250"
        };
        Main.main(args2);

        byte[] a = Files.readAllBytes(outMap.toPath());
        byte[] b = Files.readAllBytes(outMap2.toPath());
        assertTrue("mappings.tiny not identical across runs", Arrays.equals(a, b));

    byte[] da = Files.readAllBytes(outDbg.toPath());
    byte[] db = Files.readAllBytes(outDbg2.toPath());
    assertTrue("normalized debug not identical across runs", Arrays.equals(da, db));
    }

    // no helper methods
}
// >>> AUTOGEN: BYTECODEMAPPER TEST MapOldNewSmokeTest HARDEN END
