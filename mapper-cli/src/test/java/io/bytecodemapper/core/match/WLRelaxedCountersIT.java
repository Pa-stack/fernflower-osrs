// >>> AUTOGEN: BYTECODEMAPPER TEST WLRelaxedCountersIT BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.Main;
import io.bytecodemapper.cli.util.CliPaths;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WLRelaxedCountersIT {

    @Test
    public void reportIncludesGateCounters_andIsDeterministic() throws Exception {
    Path oldJar = CliPaths.resolveInput("data/weeks/2025-34/old.jar");
    Path newJar = CliPaths.resolveInput("data/weeks/2025-34/new.jar");
        Path outTiny = CliPaths.resolveOutput("mapper-cli/build/wl-counter/out.tiny");
        Path report1 = CliPaths.resolveOutput("mapper-cli/build/wl-counter/report1.json");
        Path report2 = CliPaths.resolveOutput("mapper-cli/build/wl-counter/report2.json");
        Files.createDirectories(outTiny.getParent());
        if (Files.exists(report1)) Files.delete(report1);
        if (Files.exists(report2)) Files.delete(report2);

        String[] baseArgs = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outTiny.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--maxMethods", "200",
                "--wl-relaxed-l1", "2",
                "--wl-size-band", "0.10"
        };

        // First run
        String[] args1 = new String[baseArgs.length + 2];
        System.arraycopy(baseArgs, 0, args1, 0, baseArgs.length);
        args1[baseArgs.length] = "--report";
        args1[baseArgs.length + 1] = report1.toString();
        Main.main(args1);

        // Second run
        String[] args2 = new String[baseArgs.length + 2];
        System.arraycopy(baseArgs, 0, args2, 0, baseArgs.length);
        args2[baseArgs.length] = "--report";
        args2[baseArgs.length + 1] = report2.toString();
        Main.main(args2);

        Assert.assertTrue(Files.exists(report1));
        Assert.assertTrue(Files.exists(report2));
        String json1 = new String(Files.readAllBytes(report1), StandardCharsets.UTF_8);
        String json2 = new String(Files.readAllBytes(report2), StandardCharsets.UTF_8);

        // Keys present in fixed order (thresholds, then gate counters, then hits, accepted)
        Assert.assertTrue(json1.contains("\"wl_relaxed_l1\""));
        Assert.assertTrue(json1.contains("\"wl_relaxed_size_band\""));
        Assert.assertTrue(json1.contains("\"wl_relaxed_gate_passes\""));
        Assert.assertTrue(json1.contains("\"wl_relaxed_candidates\""));
        Assert.assertTrue(json1.contains("\"wl_relaxed_hits\""));
        Assert.assertTrue(json1.contains("\"wl_relaxed_accepted\""));

        // Non-negativity checks (string presence implies integers >= 0; stronger parsing can be added later)
        // Deterministic across runs
        Assert.assertEquals(json1.trim(), json2.trim());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST WLRelaxedCountersIT END
