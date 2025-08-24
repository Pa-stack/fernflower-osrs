// >>> AUTOGEN: BYTECODEMAPPER TEST ReportCandidateStatsSmokeTest BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.util.CliPaths;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReportCandidateStatsSmokeTest {

    @Test
    public void mapOldNew_emitsCandidateStatsReport_andIsDeterministic() throws Exception {
    // [UNIFORM-JARS-BEGIN] force tests to use the latest decompiled week fixtures
    Path oldJar = CliPaths.resolveInput("data/weeks/2025-34/old.jar");
    Path newJar = CliPaths.resolveInput("data/weeks/2025-34/new.jar");
    // [UNIFORM-JARS-END]
        Path outTiny = CliPaths.resolveOutput("mapper-cli/build/test-report.tiny");
        Path report1 = CliPaths.resolveOutput("mapper-cli/build/report1.json");
        Path report2 = CliPaths.resolveOutput("mapper-cli/build/report2.json");
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
                "--maxMethods", "200"
        };

        // First run with report1
        String[] args1 = new String[baseArgs.length + 2];
        System.arraycopy(baseArgs, 0, args1, 0, baseArgs.length);
        args1[baseArgs.length] = "--report";
        args1[baseArgs.length + 1] = report1.toString();
        io.bytecodemapper.cli.Main.main(args1);
        Assert.assertTrue("report1.json should exist", Files.exists(report1));

        // Second run with report2
        String[] args2 = new String[baseArgs.length + 2];
        System.arraycopy(baseArgs, 0, args2, 0, baseArgs.length);
        args2[baseArgs.length] = "--report";
        args2[baseArgs.length + 1] = report2.toString();
        io.bytecodemapper.cli.Main.main(args2);
        Assert.assertTrue("report2.json should exist", Files.exists(report2));

    // Validate keys present and thresholds included
        String json1 = new String(Files.readAllBytes(report1), StandardCharsets.UTF_8);
        Assert.assertTrue(json1.contains("\"candidate_stats\""));
        Assert.assertTrue(json1.contains("\"cand_count_exact_median\""));
        Assert.assertTrue(json1.contains("\"cand_count_exact_p95\""));
        Assert.assertTrue(json1.contains("\"cand_count_near_median\""));
        Assert.assertTrue(json1.contains("\"cand_count_near_p95\""));
    Assert.assertTrue(json1.contains("\"wl_relaxed_l1\""));
    Assert.assertTrue(json1.contains("\"wl_relaxed_size_band\""));
    Assert.assertTrue(json1.contains("\"wl_relaxed_hits\""));

        // Determinism: contents identical across runs
        String json2 = new String(Files.readAllBytes(report2), StandardCharsets.UTF_8);
    Assert.assertEquals("report JSON must be deterministic", json1.trim(), json2.trim());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST ReportCandidateStatsSmokeTest END
