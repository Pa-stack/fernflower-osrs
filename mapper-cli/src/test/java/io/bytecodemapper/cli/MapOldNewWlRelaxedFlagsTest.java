// >>> AUTOGEN: BYTECODEMAPPER TEST MapOldNewWlRelaxedFlagsTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class MapOldNewWlRelaxedFlagsTest {

    private static void runCli(String outTiny, String reportPath, String... extra) throws Exception {
        String[] base = new String[]{
                "mapOldNew",
                "--old","data/weeks/osrs-170.jar",
                "--new","data/weeks/osrs-171.jar",
                "--out", outTiny,
                "--deterministic",
                "--report", reportPath,
                "--maxMethods","200",
                "--debug-sample","16"
        };
        String[] args = new String[base.length + (extra==null?0:extra.length)];
        System.arraycopy(base, 0, args, 0, base.length);
        if (extra != null && extra.length > 0) System.arraycopy(extra, 0, args, base.length, extra.length);
        io.bytecodemapper.cli.Main.main(args);
    }

    @Test
    public void defaultsAppearInReport() throws Exception {
        Path tiny = Paths.get("build/test-wl/defaults/out.tiny");
        Path report = Paths.get("build/test-wl/defaults/report.json");
        Files.createDirectories(tiny.getParent());
        runCli(tiny.toString(), report.toString());
        assertTrue("report.json must exist", Files.isRegularFile(report));
        String json = new String(Files.readAllBytes(report), "UTF-8");
        // Default wl_relaxed_l1=2 and wl_size_band=0.10
        assertTrue("Expected wl_relaxed_l1 default 2", json.contains("\"wl_relaxed_l1\":2"));
        assertTrue("Expected wl_size_band default 0.10", json.contains("\"wl_size_band\":0.10"));
    }

    @Test
    public void overridesAppearInReport() throws Exception {
        Path tiny = Paths.get("build/test-wl/overrides/out.tiny");
        Path report = Paths.get("build/test-wl/overrides/report.json");
        Files.createDirectories(tiny.getParent());
        runCli(tiny.toString(), report.toString(),
                "--wl-relaxed-l1","3",
                "--wl-size-band","0.25");
        assertTrue("report.json must exist", Files.isRegularFile(report));
        String json = new String(Files.readAllBytes(report), "UTF-8");
        assertTrue("Expected wl_relaxed_l1 override 3", json.contains("\"wl_relaxed_l1\":3"));
        assertTrue("Expected wl_size_band override 0.25", json.contains("\"wl_size_band\":0.25"));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MapOldNewWlRelaxedFlagsTest END
