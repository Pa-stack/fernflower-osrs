// >>> AUTOGEN: BYTECODEMAPPER TEST WLRelaxedDefaultsReportTest BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.Main;
import io.bytecodemapper.cli.util.CliPaths;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WLRelaxedDefaultsReportTest {

    private static void run(String[] args) throws Exception { Main.main(args); }

    private static String normThresholds(String json) {
        String s = json;
        s = s.replaceAll("\\\"wl_relaxed_l1\\\"\\s*:\\s*\\d+", "\"wl_relaxed_l1\":X");
        s = s.replaceAll("\\\"wl_relaxed_size_band\\\"\\s*:\\s*[0-9]+\\.[0-9]+", "\"wl_relaxed_size_band\":X");
        // Counters may vary with thresholds; normalize them too
        s = s.replaceAll("\\\"wl_relaxed_gate_passes\\\"\\s*:\\s*\\d+", "\"wl_relaxed_gate_passes\":X");
        s = s.replaceAll("\\\"wl_relaxed_candidates\\\"\\s*:\\s*\\d+", "\"wl_relaxed_candidates\":X");
        s = s.replaceAll("\\\"wl_relaxed_hits\\\"\\s*:\\s*\\d+", "\"wl_relaxed_hits\":X");
        s = s.replaceAll("\\\"wl_relaxed_accepted\\\"\\s*:\\s*\\d+", "\"wl_relaxed_accepted\":X");
        return s.trim();
    }

    @Test
    public void defaultsVsOverrides_thresholdsEcho_andDeterministic() throws Exception {
        Path oldJar = CliPaths.resolveInput("data/weeks/2025-34/old.jar");
        Path newJar = CliPaths.resolveInput("data/weeks/2025-34/new.jar");

        // Run A: no flags (defaults)
        Path outA = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/a/out.tiny");
        Path repA = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/a/report.json");
        Files.createDirectories(outA.getParent());
        String[] base = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outA.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--maxMethods", "200",
                "--report", repA.toString()
        };
        run(base);
        Assert.assertTrue(Files.exists(outA));
        Assert.assertTrue(Files.exists(repA));
        String jsonA = new String(Files.readAllBytes(repA), StandardCharsets.UTF_8);
        Assert.assertTrue("report echoes default wl_relaxed_l1=2", jsonA.contains("\"wl_relaxed_l1\":2"));
        Assert.assertTrue("report echoes default wl_relaxed_size_band=0.10", jsonA.contains("\"wl_relaxed_size_band\":0.10"));

        // Determinism within run A
        Path outA2 = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/a2/out.tiny");
        Path repA2 = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/a2/report.json");
        String[] runA2 = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outA2.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--maxMethods", "200",
                "--report", repA2.toString()
        };
        run(runA2);
        Assert.assertEquals(new String(Files.readAllBytes(outA), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(outA2), StandardCharsets.UTF_8));
        Assert.assertEquals(new String(Files.readAllBytes(repA), StandardCharsets.UTF_8).trim(),
                new String(Files.readAllBytes(repA2), StandardCharsets.UTF_8).trim());

        // Run B: overrides
        Path outB = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/b/out.tiny");
        Path repB = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/b/report.json");
        String[] argsB = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outB.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--maxMethods", "200",
                "--wl-relaxed-l1", "3",
                "--wl-size-band", "0.05",
                "--report", repB.toString()
        };
        run(argsB);
        Assert.assertTrue(Files.exists(outB));
        Assert.assertTrue(Files.exists(repB));
        String jsonB = new String(Files.readAllBytes(repB), StandardCharsets.UTF_8);
        Assert.assertTrue("report echoes override wl_relaxed_l1=3", jsonB.contains("\"wl_relaxed_l1\":3"));
        Assert.assertTrue("report echoes override wl_relaxed_size_band=0.05", jsonB.contains("\"wl_relaxed_size_band\":0.05"));

        // Determinism within run B
        Path outB2 = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/b2/out.tiny");
        Path repB2 = CliPaths.resolveOutput("mapper-cli/build/wl-defaults/b2/report.json");
        String[] runB2 = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outB2.toString(),
                "--deterministic",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--maxMethods", "200",
                "--wl-relaxed-l1", "3",
                "--wl-size-band", "0.05",
                "--report", repB2.toString()
        };
        run(runB2);
        Assert.assertEquals(new String(Files.readAllBytes(outB), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(outB2), StandardCharsets.UTF_8));
        Assert.assertEquals(new String(Files.readAllBytes(repB), StandardCharsets.UTF_8).trim(),
                new String(Files.readAllBytes(repB2), StandardCharsets.UTF_8).trim());

        // Reports otherwise identical modulo thresholds
        Assert.assertEquals(normThresholds(jsonA), normThresholds(jsonB));
        // And mapping unchanged across runs (tiny equal)
        Assert.assertEquals(new String(Files.readAllBytes(outA), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(outB), StandardCharsets.UTF_8));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST WLRelaxedDefaultsReportTest END
