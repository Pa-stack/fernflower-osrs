// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGateFastFailEquivalenceTest BEGIN
package io.bytecodemapper.core.match;

import io.bytecodemapper.cli.Main;
import io.bytecodemapper.cli.util.CliPaths;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlatteningGateFastFailEquivalenceTest {

    private static void run(String[] args) throws Exception { Main.main(args); }

        @BeforeClass
        public static void capWL() {
                if (System.getProperty("mapper.wl.max.blocks") == null) System.setProperty("mapper.wl.max.blocks", "400");
                if (System.getProperty("mapper.wl.cache.size") == null) System.setProperty("mapper.wl.cache.size", "4096");
                if (System.getProperty("mapper.wl.watchdog.ms") == null) System.setProperty("mapper.wl.watchdog.ms", "2000");
                if (System.getProperty("mapper.cand.watchdog.ms") == null) System.setProperty("mapper.cand.watchdog.ms", "8000");
        }

        private static int extractInt(String json, String key) {
                int i = json.indexOf(key);
                if (i < 0) return -1;
                i += key.length();
                int n = 0;
                boolean any = false;
                while (i < json.length()) {
                        char c = json.charAt(i++);
                        if (c >= '0' && c <= '9') { n = n * 10 + (c - '0'); any = true; }
                        else if (any) break;
                }
                return any ? n : -1;
        }

        // Increased timeout to accommodate full 2025-34 fixtures on Windows CI
        @Test(timeout = 360000)
        public void flattenedPair_deterministic_and_fastfail_visible() throws Exception {
                Path oldJar = CliPaths.resolveInput("data/weeks/2025-34/old.jar");
                Path newJar = CliPaths.resolveInput("data/weeks/2025-34/new.jar");

        // Run 1 (normal fast-fail flags)
        Path out1 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff1/out.tiny");
        Path rep1 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff1/report.json");
        Files.createDirectories(out1.getParent());
        String[] a1 = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", out1.toString(),
                "--deterministic",
                "--no-refine",
                "--maxMethods", "400",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--nsf-near", "2",
                "--stack-cos", "0.60",
                "--report", rep1.toString()
        };
        run(a1);

        // Run 2 identical
        Path out2 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff2/out.tiny");
        Path rep2 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff2/report.json");
        String[] a2 = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", out2.toString(),
                "--deterministic",
                "--no-refine",
                "--maxMethods", "400",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--nsf-near", "2",
                "--stack-cos", "0.60",
                "--report", rep2.toString()
        };
        run(a2);

        // Byte-identical checks
        Assert.assertEquals(new String(Files.readAllBytes(out1), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(out2), StandardCharsets.UTF_8));
        Assert.assertEquals(new String(Files.readAllBytes(rep1), StandardCharsets.UTF_8).trim(),
                new String(Files.readAllBytes(rep2), StandardCharsets.UTF_8).trim());

        String js1 = new String(Files.readAllBytes(rep1), StandardCharsets.UTF_8);
        Assert.assertTrue(js1.contains("\"flattening_detected\":"));
        // when flattened present, counters should show activity
        // We don't assert exact numbers; just basic presence of activity fields
        Assert.assertTrue(js1.contains("\"near_before_gates\":"));

        // Run 3 with strict cosine forcing stack filter
        Path out3 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff3/out.tiny");
        Path rep3 = CliPaths.resolveOutput("mapper-cli/build/flat-ff/ff3/report.json");
        String[] a3 = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", out3.toString(),
                "--deterministic",
                "--no-refine",
                "--maxMethods", "400",
                "--cacheDir", CliPaths.resolveOutput("mapper-cli/build/cache").toString(),
                "--idf", CliPaths.resolveOutput("mapper-cli/build/idf.properties").toString(),
                "--nsf-near", "2",
                "--stack-cos", "1.00",
                "--report", rep3.toString()
        };
        run(a3);
        String js3 = new String(Files.readAllBytes(rep3), StandardCharsets.UTF_8);
        Assert.assertTrue(js3.contains("\"near_before_gates\":"));
        Assert.assertTrue(js3.contains("\"near_after_gates\":"));

        int before1 = extractInt(js1, "\"near_before_gates\":");
        int after1 = extractInt(js1, "\"near_after_gates\":");
        int before3 = extractInt(js3, "\"near_before_gates\":");
        int after3 = extractInt(js3, "\"near_after_gates\":");

        // Basic monotonic constraints
        Assert.assertTrue("after1 <= before1", after1 <= before1);
        Assert.assertTrue("after3 <= before3", after3 <= before3);
        // With stricter stack-cos, expect not more survivors
        Assert.assertTrue("after3 <= after1", after3 <= after1);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGateFastFailEquivalenceTest END
