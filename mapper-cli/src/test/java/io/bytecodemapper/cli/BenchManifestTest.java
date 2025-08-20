// >>> AUTOGEN: BYTECODEMAPPER BenchManifestTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class BenchManifestTest {
    @Test
    public void manifestBenchProducesMetrics() throws Exception {
        File manifest = new File("mapper-cli/src/test/resources/bench/pairs.json");
        File metrics = new File("build/bench/metrics-test.json");
        String[] args = new String[] { "--manifest", manifest.getPath(), "--outDir", "build/bench/maps", "--metricsOut", metrics.getPath(), "--deterministic" };
        int code = io.bytecodemapper.cli.Bench.run(args);
        assertEquals(0, code);
        assertTrue("metrics json missing", metrics.isFile() && metrics.length() > 0);
        String s = new String(java.nio.file.Files.readAllBytes(metrics.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(s.contains("\"pairs\""));
        assertTrue(s.contains("\"totalAccepted\""));
        assertTrue(s.contains("\"totalAbstained\""));
        assertTrue(s.contains("\"items\""));
        // determinism: run again and compare bytes
        File metrics2 = new File("build/bench/metrics-test2.json");
        args = new String[] { "--manifest", manifest.getPath(), "--outDir", "build/bench/maps2", "--metricsOut", metrics2.getPath(), "--deterministic" };
        int code2 = io.bytecodemapper.cli.Bench.run(args);
        assertEquals(0, code2);
        byte[] a = java.nio.file.Files.readAllBytes(metrics.toPath());
        byte[] b = java.nio.file.Files.readAllBytes(metrics2.toPath());
        assertArrayEquals("metrics should be byte-identical", a, b);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER BenchManifestTest END
