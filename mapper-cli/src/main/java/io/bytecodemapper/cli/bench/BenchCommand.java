// >>> AUTOGEN: BYTECODEMAPPER CLI BenchCommand BEGIN
package io.bytecodemapper.cli.bench;

import io.bytecodemapper.cli.util.CliPaths;
import io.bytecodemapper.cli.orch.Orchestrator;
import io.bytecodemapper.cli.orch.OrchestratorOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class BenchCommand {
    private BenchCommand(){}

    public static int run(Map<String,String> args) {
        try {
            // >>> AUTOGEN: BYTECODEMAPPER CLI BenchCommand INDIR HARDEN BEGIN
            Path inDir  = CliPaths.resolveInput(nonEmpty(args.get("--in"), "missing --in"));
            inDir = inDir.toAbsolutePath().normalize();
            if (!Files.isDirectory(inDir)) {
                System.err.println("[bench] Input dir not found: " + inDir);
                System.err.println("[bench] Tip: stage jars under 'data/weeks' at repo root or 'mapper-cli/data/weeks'.");
                return 2;
            }
            Path out    = CliPaths.resolveOutput(nonEmpty(args.get("--out"), "missing --out"));
            // <<< AUTOGEN: BYTECODEMAPPER CLI BenchCommand INDIR HARDEN END
            String ablateCsv = args.get("--ablate"); // e.g. "calls,micro,opcode,strings,fields,norm"
            Set<String> ablate = parseAblate(ablateCsv);

            // Build pairs from directory (supports your osrs-<num>.jar naming)
            List<BenchPairs.BenchPair> pairs = BenchPairs.buildFromDirectory(inDir);

            // Prepare orchestrator opts (deterministic by default for bench)
            OrchestratorOptions base = OrchestratorOptions.defaults(
                CliPaths.resolveOutput("mapper-cli/build/cache"),
                CliPaths.resolveOutput("mapper-cli/build/idf.properties"));

            // Optional: honor idf/cache flags if the user passes them through Bench
            String cacheDir = args.get("--cacheDir");
            if (cacheDir != null) base = OrchestratorOptions.defaults(
                CliPaths.resolveOutput(cacheDir), base.idfPath);
            String idf = args.get("--idf");
            if (idf != null) base = OrchestratorOptions.defaults(
                base.cacheDir, CliPaths.resolveInput(idf));

            // Apply ablations (zero out signals in scoring)
            applyAblations(base, ablate);

            List<BenchMetrics> results = new ArrayList<BenchMetrics>(pairs.size());
            Orchestrator orch = new Orchestrator();

            long benchStart = System.nanoTime();
            long maxUsedBytes = 0L;

            // Per-pair run
            for (int i = 0; i < pairs.size(); i++) {
                BenchPairs.BenchPair p = pairs.get(i);

                long t0 = System.nanoTime();
                // track memory after run for peak; before value is unnecessary here

                Orchestrator.BenchPairResult r = orch.mapPairForBench(p.oldJar, p.newJar, base);

                long t1 = System.nanoTime();
                long memAfter = usedBytes();
                if (memAfter > maxUsedBytes) maxUsedBytes = memAfter;

                BenchMetrics m = new BenchMetrics();
                m.tag = p.tag;
                m.oldJar = relativize(inDir, p.oldJar);
                m.newJar = relativize(inDir, p.newJar);
                m.acceptedMethods = r.acceptedMethods;
                m.abstainedMethods = r.abstainedMethods;
                m.acceptedClasses = r.acceptedClasses;
                m.elapsedMs = (t1 - t0) / 1_000_000.0;
                m.usedMB = memAfter / (1024.0 * 1024.0);

                // Churn vs previous pair (Jaccard on methods in shared middle jar)
                if (i > 0) {
                    BenchPairs.BenchPair prev = pairs.get(i - 1);
                    // previous: (prev.old -> prev.new) ; current: (p.old -> p.new)
                    // Shared middle jar is prev.new == p.old (by construction)
                    Set<String> middleTargetsPrev = orch.getNewSideMethodIds(prev.tag); // methods in prev.new that were matched
                    Set<String> middleSourcesCur  = orch.getOldSideMethodIds(p.tag);   // methods in p.old that were matched
                    m.churnJaccard = jaccard(middleTargetsPrev, middleSourcesCur);
                } else {
                    m.churnJaccard = null;
                }

                // Oscillation proxy over triple (coverage flip across middle jar)
                if (i > 0 && i + 1 < pairs.size()) {
                    BenchPairs.BenchPair prev = pairs.get(i - 1);
                    BenchPairs.BenchPair next = pairs.get(i + 1);
                    // middle = p (its old == prev.new, its new == next.old)
                    Set<String> coverageIn  = orch.getNewSideMethodIds(prev.tag); // B targets
                    Set<String> coverageOut = orch.getOldSideMethodIds(next.tag); // B sources
                    m.osc3Coverage = symmetricDiffRate(coverageIn, coverageOut);
                } else {
                    m.osc3Coverage = null;
                }

                // ambiguous-pair F1: requires ground truth; emit null + counts
                m.ambiguousPairF1 = null;
                m.ambiguousCount  = r.ambiguousCount;

                results.add(m);
            }

            double benchElapsed = (System.nanoTime() - benchStart) / 1_000_000.0;

            // Write JSON
            Files.createDirectories(out.toAbsolutePath().getParent());
            try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                BenchJson.write(w, results, benchElapsed, maxUsedBytes / (1024.0 * 1024.0), ablate);
            }

            System.out.println("Bench written: " + out.toAbsolutePath());
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private static Set<String> parseAblate(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.<String>emptySet();
        Set<String> s = new HashSet<String>();
        for (String t : csv.split(",")) {
            String x = t.trim().toLowerCase(java.util.Locale.ROOT);
            if (!x.isEmpty()) s.add(x);
        }
        return s;
    }

    private static void applyAblations(OrchestratorOptions o, Set<String> abl) {
        // Known toggles: calls, micro, opcode, strings, fields, norm
        // OrchestratorOptions currently does not carry these weights; hook up here later if needed.
        // This placeholder keeps CLI stable.
    if (abl.contains("norm")) o.useNormalizedHistogram = false;
    if (abl.contains("calls"))  o.weightCalls = 0.0;
    if (abl.contains("micro"))  o.weightMicropatterns = 0.0;
    if (abl.contains("opcode")) o.weightOpcode = 0.0;
    if (abl.contains("strings")) o.weightStrings = 0.0;
    if (abl.contains("fields"))  o.weightFields = 0.0;
    }

    private static String nonEmpty(String v, String msg) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(msg);
        return v;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a == null || b == null) return Double.NaN;
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int inter = 0;
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger  = a.size() <= b.size() ? b : a;
        for (String s : smaller) if (larger.contains(s)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 1.0 : (double) inter / (double) union;
    }

    private static double symmetricDiffRate(Set<String> a, Set<String> b) {
        if (a == null || b == null) return Double.NaN;
        int diff = 0;
        for (String s : a) if (!b.contains(s)) diff++;
        for (String s : b) if (!a.contains(s)) diff++;
        int denom = Math.max(1, a.size() + b.size());
        return (double) diff / (double) denom;
    }

    private static long usedBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static String relativize(Path base, Path p) {
        try {
            return base.toAbsolutePath().relativize(p.toAbsolutePath()).toString().replace('\\','/');
        } catch (Exception e) {
            return p.toString().replace('\\','/');
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI BenchCommand END
