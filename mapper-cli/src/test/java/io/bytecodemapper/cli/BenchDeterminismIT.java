// >>> AUTOGEN: BYTECODEMAPPER TEST BenchDeterminismIT BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;

public class BenchDeterminismIT {
    @Test public void bench_metrics_are_byte_identical_across_runs() throws Exception {
        File manifest = new File("mapper-cli/src/test/resources/bench/pairs.json");
        File m1 = new File("build/it/bench/metrics1.json"); m1.getParentFile().mkdirs();
        File m2 = new File("build/it/bench/metrics2.json");
        int r1 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "bench","--manifest",manifest.getPath(),"--outDir","build/it/bench/maps1","--metricsOut",m1.getPath(),"--deterministic"
        });
        int r2 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "bench","--manifest",manifest.getPath(),"--outDir","build/it/bench/maps2","--metricsOut",m2.getPath(),"--deterministic"
        });
        assertEquals(0, r1); assertEquals(0, r2);
        byte[] a = Files.readAllBytes(m1.toPath());
        byte[] b = Files.readAllBytes(m2.toPath());
        assertArrayEquals("bench metrics must be identical", a, b);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST BenchDeterminismIT END
