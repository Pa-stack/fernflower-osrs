// >>> AUTOGEN: BYTECODEMAPPER TEST NonFlattenedNearSkipIT BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.Main;
import io.bytecodemapper.cli.util.CliPaths;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NonFlattenedNearSkipIT {

    private static void run(String[] args) throws Exception { Main.main(args); }

    @Test
    public void widenedNearNotUsedWhenNotFlattened() throws Exception {
    Path oldJar = CliPaths.resolveInput("testData/bulk.jar");
    Path newJar = CliPaths.resolveInput("testData/bulk.jar");
        Path out = CliPaths.resolveOutput("mapper-cli/build/near-skip/out.tiny");
        Path rep = CliPaths.resolveOutput("mapper-cli/build/near-skip/report.json");
        Files.createDirectories(out.getParent());

        String[] args = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", out.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--nsf-near", "2", // request widening, but run should not be flattened → should not count
                "--report", rep.toString()
        };
        run(args);

        Assert.assertTrue(Files.exists(out));
        Assert.assertTrue(Files.exists(rep));
        String json = new String(Files.readAllBytes(rep), StandardCharsets.UTF_8);
    // On this controlled non-flattened sample, counters must be zero
        Assert.assertTrue("near_before_gates should be 0 when no flattening", json.contains("\"near_before_gates\":0"));
        Assert.assertTrue("near_after_gates should be 0 when no flattening", json.contains("\"near_after_gates\":0"));
    Assert.assertTrue("flattening_detected should be 0 on non-flattened pair", json.contains("\"flattening_detected\":0"));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST NonFlattenedNearSkipIT END
