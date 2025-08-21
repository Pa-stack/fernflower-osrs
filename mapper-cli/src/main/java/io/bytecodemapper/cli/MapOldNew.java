// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.MethodFeatureExtractor;
import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.util.CliPaths;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 BEGIN
// (Writer used through orchestrator path; no direct import needed here.)
// <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 END
// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH IMPORTS BEGIN
import io.bytecodemapper.cli.orch.Orchestrator;
import io.bytecodemapper.cli.orch.OrchestratorOptions;
// <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH IMPORTS END
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;

final class MapOldNew {

    static void run(String[] args) throws Exception {
        File oldJar = null, newJar = null; File out = null;
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldJar = new File(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newJar = new File(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) out = new File(args[++i]);
        }
        if (oldJar == null || newJar == null || out == null) {
            throw new IllegalArgumentException("Usage: mapOldNew --old <old.jar> --new <new.jar> --out <out.tiny>");
        }

        // Resolve IO
        Path oldPath = CliPaths.resolveInput(oldJar.getPath());
        Path newPath = CliPaths.resolveInput(newJar.getPath());
        Path outPath = CliPaths.resolveOutput(out.getPath());
        oldJar = oldPath.toFile(); newJar = newPath.toFile(); out = outPath.toFile();
        if (!oldJar.isFile()) throw new FileNotFoundException("old jar not found: " + oldJar);
        if (!newJar.isFile()) throw new FileNotFoundException("new jar not found: " + newJar);
        if (out.getParentFile()!=null) out.getParentFile().mkdirs();

        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH FLAGS BEGIN
        boolean deterministic = false;
        String cacheDirStr = "mapper-cli/build/cache";
        String idfPathStr  = "mapper-cli/build/idf.properties";
    String dumpNormalizedDir = null; // --dump-normalized-features[=dir]
    String reportPathStr = null; // --report <path>
        for (int i=0;i<args.length;i++) {
            if ("--deterministic".equals(args[i])) { deterministic = true; }
            else if ("--cacheDir".equals(args[i]) && i+1<args.length) { cacheDirStr = args[++i]; }
            else if ("--idf".equals(args[i]) && i+1<args.length) { idfPathStr = args[++i]; }
            else if (args[i].startsWith("--dump-normalized-features")) {
                // forms: --dump-normalized-features or --dump-normalized-features=dir
                String a = args[i];
                int eq = a.indexOf('=');
                if (eq > 0 && eq < a.length()-1) dumpNormalizedDir = a.substring(eq+1);
                // if flag provided without value, we'll assign default later
            } else if ("--report".equals(args[i]) && i+1<args.length) {
                reportPathStr = args[++i];
            }
        }
        java.nio.file.Path cacheDir = io.bytecodemapper.cli.util.CliPaths.resolveOutput(cacheDirStr);
        java.nio.file.Path idfPath  = io.bytecodemapper.cli.util.CliPaths.resolveOutput(idfPathStr);
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH FLAGS END

        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG FLAGS SCOPE BEGIN
        // Parse debug flags only for mapOldNew
    boolean debugNormalized = false;
        String debugNormalizedPath = null;
        int debugSample = 50;
    boolean debugStats = false;
    int maxMethods = 0; // test-only throttle; 0 = unlimited
    // NSF tiering flag
    String nsfTierOrder = "exact,near,wl,wlrelaxed";
    // nsf64 rollout flag
    io.bytecodemapper.cli.flags.UseNsf64Mode useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.CANONICAL;
    // WL-relaxed gate flags
    Integer wlRelaxedL1 = null; // e.g., 0..N, default stays matcher default
    Double wlSizeBand = null;   // 0..1 proportion, default stays matcher default
        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew METHOD TAU FLAGS BEGIN
        double tauAcceptMethods = 0.60;
        double marginMethods = 0.05;
    // Refinement flags (optional)
    boolean refine = false; double lambda = 0.7; int refineIters = 5;
    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew INCLUDE IDENTITY BEGIN
    boolean includeIdentity = false; // unused when orchestrator is active
    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEMO RENAME OVERLAY BEGIN
    int demoCount = 0;            // unused when orchestrator is active
    String demoPrefix = null;     // unused when orchestrator is active

    // Scoring weight flags (optional)
    Double wCallsFlag = null, wMicroFlag = null, wNormFlag = null, wStrFlag = null, wFieldsFlag = null, alphaMicroFlag = null;
    for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--debug-normalized".equals(a)) {
                debugNormalized = true;
                // optional path value next
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    debugNormalizedPath = args[++i];
                }
            } else if ("--debug-sample".equals(a)) {
                if (i + 1 < args.length) {
                    try { debugSample = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
                }
            } else if ("--debug-stats".equals(a)) {
                debugStats = true;
            } else if ("--tauAcceptMethods".equals(a) && i+1<args.length) {
                try { tauAcceptMethods = Double.parseDouble(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--marginMethods".equals(a) && i+1<args.length) {
                try { marginMethods = Double.parseDouble(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--refine".equals(a)) {
                refine = true;
            } else if ("--lambda".equals(a) && i+1<args.length) {
                try { lambda = Double.parseDouble(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--refineIters".equals(a) && i+1<args.length) {
                try { refineIters = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--maxMethods".equals(a) && i+1<args.length) {
                try { maxMethods = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--nsf-tier-order".equals(a) && i+1<args.length) {
                nsfTierOrder = args[++i];
            } else if ("--use-nsf64".equals(a) && i+1<args.length) {
                useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.parse(args[++i]);
            } else if (a.startsWith("--use-nsf64=")) {
                String val = a.substring("--use-nsf64=".length());
                useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.parse(val);
            } else if ("--wlRelaxedL1".equals(a) && i+1<args.length) {
                try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(args[++i])); } catch (NumberFormatException ignore) {}
            } else if (a.startsWith("--wlRelaxedL1=")) {
                try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(a.substring("--wlRelaxedL1=".length()))); } catch (NumberFormatException ignore) {}
            } else if ("--wl-relaxed-l1".equals(a) && i+1<args.length) {
                try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(args[++i])); } catch (NumberFormatException ignore) {}
            } else if (a.startsWith("--wl-relaxed-l1=")) {
                try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(a.substring("--wl-relaxed-l1=".length()))); } catch (NumberFormatException ignore) {}
            } else if ("--wlSizeBand".equals(a) && i+1<args.length) {
                try { wlSizeBand = Double.valueOf(Double.parseDouble(args[++i])); } catch (NumberFormatException ignore) {}
            } else if (a.startsWith("--wlSizeBand=")) {
                try { wlSizeBand = Double.valueOf(Double.parseDouble(a.substring("--wlSizeBand=".length()))); } catch (NumberFormatException ignore) {}
            } else if ("--wl-size-band".equals(a) && i+1<args.length) {
                try { wlSizeBand = Double.valueOf(Double.parseDouble(args[++i])); } catch (NumberFormatException ignore) {}
            } else if (a.startsWith("--wl-size-band=")) {
                try { wlSizeBand = Double.valueOf(Double.parseDouble(a.substring("--wl-size-band=".length()))); } catch (NumberFormatException ignore) {}
            } else if ("--includeIdentity".equals(a)) {
                includeIdentity = true;
            } else if ("--demoRemapCount".equals(a) && i+1<args.length) {
                try { demoCount = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--demoRemapPrefix".equals(a) && i+1<args.length) {
                demoPrefix = args[++i];
            } else if ("--wCalls".equals(a) && i+1<args.length) {
                try { wCallsFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--wMicro".equals(a) && i+1<args.length) {
                try { wMicroFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--wNorm".equals(a) && i+1<args.length) {
                try { wNormFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--wStrings".equals(a) && i+1<args.length) {
                try { wStrFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--wFields".equals(a) && i+1<args.length) {
                try { wFieldsFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            } else if ("--alphaMicro".equals(a) && i+1<args.length) {
                try { alphaMicroFlag = Double.valueOf(args[++i]); } catch (NumberFormatException ignore) {}
            }
        }
        // Apply method matching thresholds (global static for this run)
        io.bytecodemapper.cli.method.MethodScorer.setTauAccept(tauAcceptMethods);
        io.bytecodemapper.cli.method.MethodScorer.setMinMargin(marginMethods);
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew METHOD TAU FLAGS END
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew INCLUDE IDENTITY END
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEMO RENAME OVERLAY END
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG FLAGS SCOPE END

        // Touch legacy flags to avoid unused warnings (kept for forward/back compat in autogen region)
        if (includeIdentity || demoCount != 0 || (demoPrefix != null && demoPrefix.length() == 0)) {
            // no-op
        }

        // Read classes, sort deterministically by internal name
        final List<ClassNode> oldClasses = new ArrayList<ClassNode>();
        final List<ClassNode> newClasses = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
        scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});
        Collections.sort(oldClasses, new Comparator<ClassNode>() { public int compare(ClassNode a, ClassNode b){ return a.name.compareTo(b.name);} });
        Collections.sort(newClasses, new Comparator<ClassNode>() { public int compare(ClassNode a, ClassNode b){ return a.name.compareTo(b.name);} });

        // Build MethodFeatures only when debug dump is requested, and cap to a small sample
        Map<MethodRef, MethodFeatures> oldMf = null;
        Map<MethodRef, MethodFeatures> newMf = null;
        if (debugNormalized) {
            oldMf = new LinkedHashMap<MethodRef, MethodFeatures>();
            newMf = new LinkedHashMap<MethodRef, MethodFeatures>();
            MethodFeatureExtractor extractor = new MethodFeatureExtractor();

            int oldCount = 0;
            for (ClassNode cn : oldClasses) {
                if (oldCount >= debugSample) break;
                for (MethodNode mn : sortMethodsFiltered(cn)) {
                    if (oldCount >= debugSample) break;
                    MethodFeatures f = extractor.extractForOld(cn, mn, new MethodFeatureExtractor.ClassOwnerMapper() {
                        public String mapOldOwnerToNew(String o) { return o; }
                    });
                    oldMf.put(f.ref, f);
                    oldCount++;
                }
            }
            int newCount = 0;
            for (ClassNode cn : newClasses) {
                if (newCount >= debugSample) break;
                for (MethodNode mn : sortMethodsFiltered(cn)) {
                    if (newCount >= debugSample) break;
                    MethodFeatures f = extractor.extractForNew(cn, mn);
                    newMf.put(f.ref, f);
                    newCount++;
                }
            }
        }

        // Debug dump (scoped to this command)
        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG DUMP CALL BEGIN
    if (debugNormalized) {
        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG PATH RESOLUTION BEGIN
        java.nio.file.Path dbgOutPath = io.bytecodemapper.cli.util.CliPaths.resolveOutput(
            debugNormalizedPath != null ? debugNormalizedPath : "mapper-cli/build/normalized_debug.txt");
        java.nio.file.Files.createDirectories(dbgOutPath.getParent());
        io.bytecodemapper.cli.util.DebugNormalizedDump.writeSample(
            oldJar, newJar, dbgOutPath, debugSample, oldMf, newMf);
        System.out.println("Wrote normalized debug to: " + dbgOutPath);
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG PATH RESOLUTION END
        // Release references to reduce peak memory before orchestrator runs
        oldMf = null; newMf = null;
    }
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG DUMP CALL END

    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH INVOKE BEGIN
    // Orchestrated end-to-end run (deterministic ordering, persistent caches, IDF handling)
    // Note: refine/lambda/iters not parsed in this command yet; use defaults compatible with OrchestratorOptions
    OrchestratorOptions o = new OrchestratorOptions(
        deterministic, cacheDir, idfPath,
        refine, lambda, refineIters,
    debugStats, debugNormalized, debugSample,
    maxMethods
    );
    // Apply scoring weight overrides if provided
    if (wCallsFlag != null) o.weightCalls = wCallsFlag.doubleValue();
    if (wMicroFlag != null) o.weightMicropatterns = wMicroFlag.doubleValue();
    if (wNormFlag != null) { o.weightOpcode = wNormFlag.doubleValue(); o.useNormalizedHistogram = true; }
    if (wStrFlag != null) o.weightStrings = wStrFlag.doubleValue();
    if (wFieldsFlag != null) o.weightFields = wFieldsFlag.doubleValue();
    if (alphaMicroFlag != null) o.alphaMicropattern = alphaMicroFlag.doubleValue();

    // Apply nsf64 tiering and rollout mode before matching
    io.bytecodemapper.core.match.MethodMatcher.setNsftierOrder(nsfTierOrder);
    io.bytecodemapper.core.match.MethodMatcher.setUseNsf64Mode(useNsf64Mode);
    if (wlRelaxedL1 != null) io.bytecodemapper.core.match.MethodMatcher.setWlRelaxedL1(wlRelaxedL1.intValue());
    if (wlSizeBand != null) io.bytecodemapper.core.match.MethodMatcher.setWlSizeBand(wlSizeBand.doubleValue());

    Orchestrator orch = new Orchestrator();
    Orchestrator.Result r = orch.run(oldPath, newPath, o);

    // Optional: dump per-method normalized features as JSONL deterministically
    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DUMP NSF JSONL CALL BEGIN
    if (dumpNormalizedDir != null) {
        java.nio.file.Path dir = io.bytecodemapper.cli.util.CliPaths.resolveOutput(
            dumpNormalizedDir.length() > 0 ? dumpNormalizedDir : "mapper-cli/build/nsf-jsonl");
        java.nio.file.Files.createDirectories(dir);
        io.bytecodemapper.cli.util.NormalizedDumpWriter.dumpJsonl(oldPath, newPath, dir);
        System.out.println("Wrote normalized-features JSONL to: " + dir);
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DUMP NSF JSONL CALL END

    // Write Tiny v2 deterministically using orchestrator output
    io.bytecodemapper.io.tiny.TinyV2Writer.writeTiny2(outPath, r.classMap, r.methods, r.fields);
    System.out.println("Wrote Tiny v2 to: " + outPath);
    // Optional: write report JSON with candidate stats
    if (reportPathStr != null && reportPathStr.length() > 0) {
        java.nio.file.Path rp = io.bytecodemapper.cli.util.CliPaths.resolveOutput(reportPathStr);
        io.bytecodemapper.cli.orch.Orchestrator.writeReportJson(rp, r);
        System.out.println("Wrote report JSON to: " + rp);
    }
    return;
    // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew ORCH INVOKE END

    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 BEGIN
    // Replaced by orchestrator path above. This block intentionally left minimal.
    // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 END
    }

    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew PROGRAMMATIC BEGIN
    public static final class Result {
        public final int acceptedCount; public final int abstainedCount;
        public Result(int a, int b){ this.acceptedCount=a; this.abstainedCount=b; }
    }

    public static Result runProgrammatic(String oldJar, String newJar, String outTiny, boolean deterministic, String[] extraArgs) throws Exception {
        // Resolve IO via existing helpers
        java.nio.file.Path oldPath = io.bytecodemapper.cli.util.CliPaths.resolveInput(oldJar);
        java.nio.file.Path newPath = io.bytecodemapper.cli.util.CliPaths.resolveInput(newJar);
        java.nio.file.Path outPath = io.bytecodemapper.cli.util.CliPaths.resolveOutput(outTiny);
        if (outPath.getParent()!=null) java.nio.file.Files.createDirectories(outPath.getParent());

        // Deterministic orchestrator options
        io.bytecodemapper.cli.orch.OrchestratorOptions o = io.bytecodemapper.cli.orch.OrchestratorOptions.defaults(
                io.bytecodemapper.cli.util.CliPaths.resolveOutput("mapper-cli/build/cache"),
                io.bytecodemapper.cli.util.CliPaths.resolveOutput("mapper-cli/build/idf.properties"));
    // orchestrator defaults to deterministic=true; flag preserved
        // honor extraArgs for test throttling (e.g., --maxMethods N)
        if (extraArgs != null) {
            for (int i=0;i<extraArgs.length;i++) {
                String a = extraArgs[i];
                if ("--maxMethods".equals(a) && i+1<extraArgs.length) {
                    try { o = new io.bytecodemapper.cli.orch.OrchestratorOptions(
                        o.deterministic, o.cacheDir, o.idfPath, o.refine, o.lambda, o.refineIters,
                        o.debugStats, o.debugNormalized, o.debugNormalizedSample, Integer.parseInt(extraArgs[++i])); } catch (NumberFormatException ignore) {}
                }
            }
        }
        io.bytecodemapper.cli.orch.Orchestrator orch = new io.bytecodemapper.cli.orch.Orchestrator();
        io.bytecodemapper.cli.orch.Orchestrator.Result r = orch.run(oldPath, newPath, o);
        // Write tiny output
        io.bytecodemapper.io.tiny.TinyV2Writer.writeTiny2(outPath, r.classMap, r.methods, r.fields);

    int accepted = r != null && r.methods != null ? r.methods.size() : 0;
    // Approximate abstained as old-total minus accepted; acceptable for manifest bench metrics
    int abstained = r != null ? Math.max(0, r.methodsOld - accepted) : 0;
        return new Result(accepted, abstained);
    }
    // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew PROGRAMMATIC END

    // Deterministic filtered method list (skip abstract/native)
    private static List<MethodNode> sortMethodsFiltered(ClassNode cn) {
        List<MethodNode> ms = new ArrayList<MethodNode>(cn.methods);
        Iterator<MethodNode> it = ms.iterator();
        while (it.hasNext()) {
            MethodNode m = it.next();
            int acc = m.access;
            boolean isAbstract = (acc & Opcodes.ACC_ABSTRACT) != 0;
            boolean isNative   = (acc & Opcodes.ACC_NATIVE) != 0;
            if (isAbstract || isNative) it.remove();
        }
        Collections.sort(ms, new Comparator<MethodNode>() {
            public int compare(MethodNode a, MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });
        return ms;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew END
