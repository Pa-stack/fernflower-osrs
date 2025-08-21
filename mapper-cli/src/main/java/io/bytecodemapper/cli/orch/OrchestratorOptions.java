// >>> AUTOGEN: BYTECODEMAPPER CLI OrchestratorOptions BEGIN
package io.bytecodemapper.cli.orch;

import java.nio.file.Path;

public final class OrchestratorOptions {
    public final boolean deterministic;
    public final Path cacheDir;        // default build/cache
    public final Path idfPath;         // default build/idf.properties
    public final boolean refine;       // reuse methodMatch flags if set
    public final double lambda;        // for refinement (0.6..0.8)
    public final int refineIters;      // iterations
    public final boolean debugStats;   // phase summaries
    public final boolean debugNormalized; // dump normalized sample in mapOldNew (already exists)
    public final int debugNormalizedSample;
    public final int maxMethods; // 0 = unlimited; test-only throttle
    // Per-run WL-relaxed thresholds
    public int wlRelaxedL1 = 2;
    public double wlSizeBand = 0.10;
    // Phase 4: flattening-aware widening + gates
    public int nsfNearBudgetWhenFlattened = 2; // widen near-tier Hamming budget when flattening
    public double stackCosineThreshold = 0.60; // stack-hist cosine threshold gate

    // >>> AUTOGEN: BYTECODEMAPPER CLI OrchestratorOptions ABLATE BEGIN
    // Weights already exist; add ablation/toggles if missing.
    public boolean useNormalizedHistogram = true;
    public double weightCalls = 0.45;
    public double weightMicropatterns = 0.25;
    public double weightOpcode = 0.15;
    public double weightStrings = 0.10;
    public double weightFields = 0.05;
    public double alphaMicropattern = 0.60; // blend inside micro
    public double tauAccept = 0.60; // acceptance threshold
    // bench ablations are realized by zeroing weights and/or toggles above.

    // Defaults remain: calls=0.45, micro=0.25, opcode=0.15, strings=0.10, fields=0.05.
    // Ensure your scoring code consults these weights & toggles.
    // <<< AUTOGEN: BYTECODEMAPPER CLI OrchestratorOptions ABLATE END

    public OrchestratorOptions(
            boolean deterministic,
            Path cacheDir,
            Path idfPath,
            boolean refine,
            double lambda,
            int refineIters,
            boolean debugStats,
            boolean debugNormalized,
            int debugNormalizedSample,
            int maxMethods) {
        this.deterministic = deterministic;
        this.cacheDir = cacheDir;
        this.idfPath = idfPath;
        this.refine = refine;
        this.lambda = lambda;
        this.refineIters = refineIters;
        this.debugStats = debugStats;
        this.debugNormalized = debugNormalized;
        this.debugNormalizedSample = debugNormalizedSample;
        this.maxMethods = maxMethods;
    }

    public static OrchestratorOptions defaults(Path cacheDir, Path idfPath) {
    OrchestratorOptions o = new OrchestratorOptions(
                true, cacheDir, idfPath, // deterministic default ON in CI
                false, 0.7, 5,           // refinement defaults
                false, false, 50,        // debug defaults
                0                        // no cap by default
    );
    // scoring defaults
    o.useNormalizedHistogram = true;
    o.weightCalls = 0.45; o.weightMicropatterns = 0.25; o.weightOpcode = 0.15; o.weightStrings = 0.10; o.weightFields = 0.05;
    o.alphaMicropattern = 0.60; o.tauAccept = 0.60;
    // WL-relaxed defaults per run
    o.wlRelaxedL1 = 2;
    o.wlSizeBand = 0.10;
    // Phase 4 defaults
    o.nsfNearBudgetWhenFlattened = 2;
    o.stackCosineThreshold = 0.60;
    return o;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI OrchestratorOptions END
