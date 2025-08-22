package io.bytecodemapper.cli;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DocsPresenceTest {
    private static File repoRoot() {
        File cur = new File(".").getAbsoluteFile();
        while (cur != null) {
            File g = new File(cur, "gradlew.bat");
            File s = new File(cur, "settings.gradle");
            if (g.isFile() && s.isFile()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return new File(".").getAbsoluteFile();
    }

    private static String read(String rel) throws Exception {
        Path p = new File(repoRoot(), rel).toPath().normalize();
        byte[] bytes = Files.readAllBytes(p);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    public void docsExistAndContainFrozenConstants() throws Exception {
        List<String> paths = Arrays.asList(
                "docs/adr/adr-01-type-evidence-matching.md",
                "docs/adr/adr-02-normalizedmethod.md",
                "docs/adr/adr-03-multi-signal-scoring.md",
                "docs/adr/adr-04-isorank-refinement.md",
                "docs/adr/adr-05-field-matching.md",
                "docs/runbook.md",
                "docs/determinism.md",
                "docs/bench-plan.md",
                "docs/scoring.md",
                "docs/bitset.md"
        );

        List<String> contents = new ArrayList<String>(paths.size());
        for (String p : paths) {
            // existence
            File f = new File(repoRoot(), p);
            assertTrue("missing: " + p, f.isFile());
            System.out.println("docs.ok:" + new File(p).getName());
            // read
            contents.add(read(p));
        }
        // sort for determinism prior to scanning
        Collections.sort(contents);

        String all = join(contents, "\n\n----\n\n");

        // Exact literals to be present
        assertContains(all, "TAU_ACCEPT=0.60");
        System.out.println("docs.const:TAU_ACCEPT=0.60");

        assertContains(all, "WEIGHTS_METHOD={calls:0.45, micro:0.25, opcode:0.15, strings:0.10}");
        System.out.println("docs.const:WEIGHTS_METHOD={calls:0.45, micro:0.25, opcode:0.15, strings:0.10}");

        assertContains(all, "REFINE_BETA=0.70");
        assertContains(all, "CAPS=[-0.05,+0.10]");
        assertContains(all, "FREEZE=0.80");
        System.out.println("docs.const:REFINE_BETA=0.70 CAPS=[-0.05,+0.10] FREEZE=0.80");

        assertContains(all, "EMA_LAMBDA=0.90");
        assertContains(all, "IDF_CLAMP=[0.5,3.0]");
        assertContains(all, "ROUND=4dp");
        System.out.println("docs.const:EMA_LAMBDA=0.90 IDF_CLAMP=[0.5,3.0] ROUND=4dp");

        assertContains(all, "MICRO_BITS=17");
        System.out.println("docs.const:MICRO_BITS=17");

        // Additional constants required to appear verbatim
        assertContains(all, "0.40, 0.30, 0.20, 0.10");
        assertContains(all, "ALPHA_MICRO=0.60");
        assertContains(all, "MARGIN=0.05");
        assertContains(all, "MAX_ITERS=10");
        assertContains(all, "EPS=1e-3");
        assertContains(all, "StableHash64 note: FNV-1a 64-bit fixed seed");

    // Cache key formula literal (must match verbatim)
    assertContains(all, "sha256(oldJarSHA256+\"\\n\"+newJarSHA256+\"\\n\"+algorithmVersion+\"\\n\"+canonicalConfigJson+\"\\n\"+javaVersion+\"\\n\")");

        // Pipeline order must appear verbatim (as code block in docs)
        assertContains(all, "Normalize → ReducedCFG → Dominators → DF/TDF → WL → Class match →\nMethod candidates → Multi-signal scoring + filters →\nCall-graph refinement (optional) → Field matching → Tiny v2 write → (optional) remap");

        // Bench CLI line presence
        assertContains(all, ":mapper-cli: bench --in data/weeks --out build/bench.json [--ablate calls,micro,opcode,strings,fields,refine]");
    }

    private static void assertContains(String haystack, String needle) {
    // Normalize Windows CRLF to LF to make multi-line literal checks robust across platforms
    String norm = haystack.replace("\r\n", "\n");
    assertTrue("missing literal: " + needle, norm.contains(needle));
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
