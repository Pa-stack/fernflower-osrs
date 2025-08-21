// CODEGEN-BEGIN: DumpDeterminismIT
package io.bytecodemapper.cli;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class DumpDeterminismIT {
    @Test
    public void dumpStableAcrossRuns() throws Exception {
        Path dir = java.nio.file.Paths.get("mapper-cli/build/test-out/nsf-jsonl-it");
        java.nio.file.Files.createDirectories(dir);
        Path oldJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("testData/bulk.jar");
        Path newJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("testData/bulk.jar");
        io.bytecodemapper.cli.util.NormalizedDumpWriter.dumpJsonl(oldJar, newJar, dir);
        byte[] a1 = Files.readAllBytes(dir.resolve("old.jsonl"));
        byte[] b1 = Files.readAllBytes(dir.resolve("new.jsonl"));
        io.bytecodemapper.cli.util.NormalizedDumpWriter.dumpJsonl(oldJar, newJar, dir);
        byte[] a2 = Files.readAllBytes(dir.resolve("old.jsonl"));
        byte[] b2 = Files.readAllBytes(dir.resolve("new.jsonl"));
        assertArrayEquals(a1, a2);
        assertArrayEquals(b1, b2);
    }
}
// CODEGEN-END: DumpDeterminismIT
