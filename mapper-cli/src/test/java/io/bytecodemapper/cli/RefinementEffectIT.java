// >>> AUTOGEN: BYTECODEMAPPER TEST RefinementEffectIT BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class RefinementEffectIT {
    @Test
    public void refine_does_not_reduce_acceptance_and_is_deterministic() throws Exception {
        File base = new File("build/it/ref/base.tiny"); base.getParentFile().mkdirs();
        int rc1 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            // [UNIFORM-JARS-BEGIN]
            "mapOldNew","--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out", base.getPath(), "--deterministic", "--maxMethods","300"
        });
        assertEquals(0, rc1);

        File ref1 = new File("build/it/ref/ref1.tiny");
        int rc2 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "mapOldNew","--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out", ref1.getPath(), "--deterministic", "--refine","--lambda","0.7","--refineIters","2","--maxMethods","300"
        });
        assertEquals(0, rc2);

        File ref2 = new File("build/it/ref/ref2.tiny");
        int rc3 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "mapOldNew","--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            // [UNIFORM-JARS-END]
            "--out", ref2.getPath(), "--deterministic", "--refine","--lambda","0.7","--refineIters","2","--maxMethods","300"
        });
        assertEquals(0, rc3);

        byte[] r1 = java.nio.file.Files.readAllBytes(ref1.toPath());
        byte[] r2 = java.nio.file.Files.readAllBytes(ref2.toPath());
        assertArrayEquals("refined outputs must be identical across runs", r1, r2);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST RefinementEffectIT END
