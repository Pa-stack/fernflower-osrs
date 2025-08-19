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

    public OrchestratorOptions(
            boolean deterministic,
            Path cacheDir,
            Path idfPath,
            boolean refine,
            double lambda,
            int refineIters,
            boolean debugStats,
            boolean debugNormalized,
            int debugNormalizedSample) {
        this.deterministic = deterministic;
        this.cacheDir = cacheDir;
        this.idfPath = idfPath;
        this.refine = refine;
        this.lambda = lambda;
        this.refineIters = refineIters;
        this.debugStats = debugStats;
        this.debugNormalized = debugNormalized;
        this.debugNormalizedSample = debugNormalizedSample;
    }

    public static OrchestratorOptions defaults(Path cacheDir, Path idfPath) {
        return new OrchestratorOptions(
                true, cacheDir, idfPath, // deterministic default ON in CI
                false, 0.7, 5,           // refinement defaults
                false, false, 50         // debug defaults
        );
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI OrchestratorOptions END
