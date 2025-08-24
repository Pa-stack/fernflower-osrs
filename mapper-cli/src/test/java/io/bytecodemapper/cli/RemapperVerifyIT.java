// >>> AUTOGEN: BYTECODEMAPPER TEST RemapperVerifyIT BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;

public class RemapperVerifyIT {

    @Test public void tiny_and_asm_remap_deterministic_and_nonempty() throws Exception {
        File map = new File("build/it/rv/map.tiny"); map.getParentFile().mkdirs();
        int mk = io.bytecodemapper.cli.Router.dispatch(new String[]{
            // [UNIFORM-JARS-BEGIN]
            "mapOldNew","--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--out", map.getPath(), "--deterministic", "--maxMethods","200"
        });
        assertEquals(0, mk);
        assertTrue(map.isFile() && map.length() > 0);

        File outA = new File("build/it/rv/remap-tiny-a.jar");
        File outB = new File("build/it/rv/remap-tiny-b.jar");
        int rc1 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "applyMappings","--inJar","data/weeks/2025-34/new.jar","--mappings",map.getPath(),"--out",outA.getPath(),"--verifyRemap","--deterministic"
        });
        int rc2 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "applyMappings","--inJar","data/weeks/2025-34/new.jar","--mappings",map.getPath(),"--out",outB.getPath(),"--verifyRemap","--deterministic"
        });
        assertEquals(0, rc1); assertEquals(0, rc2);
        assertTrue(outA.isFile() && outA.length()>0);
        assertTrue(outB.isFile() && outB.length()>0);
        byte[] a = java.nio.file.Files.readAllBytes(outA.toPath());
        byte[] b = java.nio.file.Files.readAllBytes(outB.toPath());
        assertArrayEquals("TinyRemapper outputs must be deterministic", a, b);

        File outAsmA = new File("build/it/rv/remap-asm-a.jar");
        File outAsmB = new File("build/it/rv/remap-asm-b.jar");
        int rc3 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "applyMappings","--inJar","data/weeks/2025-34/new.jar","--mappings",map.getPath(),"--out",outAsmA.getPath(),"--remapper=asm","--verifyRemap","--deterministic"
        });
        int rc4 = io.bytecodemapper.cli.Router.dispatch(new String[]{
            "applyMappings","--inJar","data/weeks/2025-34/new.jar","--mappings",map.getPath(),"--out",outAsmB.getPath(),"--remapper=asm","--verifyRemap","--deterministic"
            // [UNIFORM-JARS-END]
        });
        assertEquals(0, rc3); assertEquals(0, rc4);
        assertTrue(outAsmA.isFile() && outAsmA.length()>0);
        assertTrue(outAsmB.isFile() && outAsmB.length()>0);
        byte[] aa = java.nio.file.Files.readAllBytes(outAsmA.toPath());
        byte[] bb = java.nio.file.Files.readAllBytes(outAsmB.toPath());
        assertArrayEquals("ASM outputs must be deterministic", aa, bb);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST RemapperVerifyIT END
