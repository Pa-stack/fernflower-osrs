// >>> AUTOGEN: BYTECODEMAPPER TEST MethodMatcher Provenance BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import static org.junit.Assert.*;

public class MethodMatcherProvenanceTest {
    @Test public void provenanceLoggedCanonicalVsSurrogate() throws Exception {
        java.io.File out1 = new java.io.File("build/test-provenance/out1.tiny");
        java.io.File out2 = new java.io.File("build/test-provenance/out2.tiny");
        if (out1.getParentFile()!=null) out1.getParentFile().mkdirs();
        if (out2.getParentFile()!=null) out2.getParentFile().mkdirs();

        String[] common = new String[]{
            "mapOldNew",
            "--old","data/weeks/2025-34/old.jar","--new","data/weeks/2025-34/new.jar",
            "--deterministic","--debug-stats","--debug-sample","16","--maxMethods","200"
        };

        // Capture stdout
        java.io.PrintStream old = System.out;
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(1<<20);
        java.io.PrintStream ps = new java.io.PrintStream(bout,true,"UTF-8");
        System.setOut(ps);
        try {
            io.bytecodemapper.cli.Main.main(concat(common, new String[]{"--out", out1.getPath(), "--use-nsf64","canonical"}));
        } finally {
            System.setOut(old);
            ps.flush(); ps.close();
        }
        String s1 = new String(bout.toByteArray(), "UTF-8");
        // canonical path should include fp_mode=nsf64 when present
        boolean hasCanonical = s1.contains("fp_mode=nsf64");
        assertTrue("Expected canonical fp_mode in debug-stats output", hasCanonical);

        // Run surrogate mode and capture again
        old = System.out; bout = new java.io.ByteArrayOutputStream(1<<20); ps = new java.io.PrintStream(bout,true,"UTF-8");
        System.setOut(ps);
        try {
            io.bytecodemapper.cli.Main.main(concat(common, new String[]{"--out", out2.getPath(), "--use-nsf64","surrogate"}));
        } finally {
            System.setOut(old);
            ps.flush(); ps.close();
        }
        String s2 = new String(bout.toByteArray(), "UTF-8");
        boolean hasSurrogate = s2.contains("fp_mode=nsf_surrogate") || s2.contains("top_fp_mode=nsf_surrogate");
        assertTrue("Expected surrogate fp_mode in debug-stats output", hasSurrogate);
    }

    private static String[] concat(String[] a, String[] b){
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST MethodMatcher Provenance END
