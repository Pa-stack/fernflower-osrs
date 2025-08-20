// >>> AUTOGEN: BYTECODEMAPPER TEST NormalizedDumpTest BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.util.NormalizedDumpWriter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NormalizedDumpTest {

    @Test
    public void writerCreatesFilesDeterministically() throws Exception {
        Path tmp = java.nio.file.Paths.get("mapper-cli/build/test-out/nsf-jsonl");
        java.nio.file.Files.createDirectories(tmp);
    Path oldJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("testData/bulk.jar");
    Path newJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("testData/bulk.jar");
        NormalizedDumpWriter.dumpJsonl(oldJar, newJar, tmp);
        assertTrue(Files.isRegularFile(tmp.resolve("old.jsonl")));
        assertTrue(Files.isRegularFile(tmp.resolve("new.jsonl")));
        // basic stability: two runs byte-identical
        byte[] a1 = Files.readAllBytes(tmp.resolve("old.jsonl"));
        byte[] b1 = Files.readAllBytes(tmp.resolve("new.jsonl"));
        NormalizedDumpWriter.dumpJsonl(oldJar, newJar, tmp);
        byte[] a2 = Files.readAllBytes(tmp.resolve("old.jsonl"));
        byte[] b2 = Files.readAllBytes(tmp.resolve("new.jsonl"));
        assertArrayEquals(a1, a2);
        assertArrayEquals(b1, b2);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST NormalizedDumpTest END
