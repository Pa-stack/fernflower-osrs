// >>> AUTOGEN: BYTECODEMAPPER TEST MapOldNewSmokeTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MapOldNewSmokeTest {

    @Test
    public void runsMapOldNewAndWritesDebugDump() throws Exception {
    // Determine repo root (parent of mapper-cli when running inside the module)
    File cwd = new File(".").getCanonicalFile();
    File repoRoot = cwd.getName().equals("mapper-cli") && cwd.getParentFile() != null ? cwd.getParentFile() : cwd;

    // Expected output locations (resolveOutput anchors under repoRoot/mapper-cli for relatives)
    Path outMappings = new File(repoRoot, "mapper-cli/build/test-smoke-mappings.tiny").toPath();
    Path debugDump = new File(repoRoot, "mapper-cli/build/test-normalized_debug.txt").toPath();
        Files.createDirectories(outMappings.getParent());
        Files.createDirectories(debugDump.getParent());
        Files.deleteIfExists(outMappings);
        Files.deleteIfExists(debugDump);

        // Run CLI via Main.main
        String[] args = new String[] {
                "mapOldNew",
        "--old", "testData/jars/old.jar",
        "--new", "testData/jars/new.jar",
        "--out", "build/test-smoke-mappings.tiny",
        "--debug-normalized", "build/test-normalized_debug.txt",
                "--debug-sample", "5",
                "--maxMethods", "200",
                "--deterministic"
        };
        Main.main(args);

        // Assert mappings and debug dump exist and are non-empty
        assertTrue("mappings written", Files.isRegularFile(outMappings));
        assertTrue("mappings non-empty", Files.size(outMappings) > 0);
        assertTrue("debug dump written", Files.isRegularFile(debugDump));
        assertTrue("debug dump non-empty", Files.size(debugDump) > 0);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MapOldNewSmokeTest END
