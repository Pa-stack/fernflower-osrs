// >>> AUTOGEN: BYTECODEMAPPER CORE CompositeScorer BEGIN
package io.bytecodemapper.core.score;

/**
 * Deterministic composite scorer: sums weighted secondary signals strictly from options.
 * Java 8 compatible; no streams.
 */
public final class CompositeScorer {
    private CompositeScorer() {}

    // >>> AUTOGEN: BYTECODEMAPPER CORE CompositeScorer OPTIONS BEGIN
    /** Weights/toggles for composing secondary signals. */
    public static final class Options {
        public double weightCalls;
        public double weightMicropatterns;
        public double weightOpcode;
        public double weightStrings;
        public double weightFields;
        public boolean useNormalizedHistogram = true;

        public Options set(double wC, double wM, double wO, double wS, double wF, boolean useNorm) {
            this.weightCalls = wC; this.weightMicropatterns = wM; this.weightOpcode = wO;
            this.weightStrings = wS; this.weightFields = wF; this.useNormalizedHistogram = useNorm;
            return this;
        }
    }

    /** Compose total score from component scores honoring weights and toggles. */
    public static double total(double callsScore,
                               double microScore,
                               double opcodeScore,
                               double stringsScore,
                               double fieldsScore,
                               Options options) {
        double sCalls   = callsScore;
        double sMicro   = microScore;
        double sOpcode  = opcodeScore;
        double sStrings = stringsScore;
        double sFields  = fieldsScore;

        // Respect useNormalizedHistogram toggle by zeroing normalized-hist contribution if disabled.
        if (options != null && !options.useNormalizedHistogram) {
            sOpcode = 0.0; // assume opcodeScore corresponds to normalized histogram unless split upstream
        }

        double wCalls   = options != null ? options.weightCalls         : 0.45;
        double wMicro   = options != null ? options.weightMicropatterns : 0.25;
        double wOpcode  = options != null ? options.weightOpcode        : 0.15;
        double wStrings = options != null ? options.weightStrings       : 0.10;
        double wFields  = options != null ? options.weightFields        : 0.05;

        double total = wCalls * sCalls
                + wMicro   * sMicro
                + wOpcode  * sOpcode
                + wStrings * sStrings
                + wFields  * sFields;
        return total;
    }
    // <<< AUTOGEN: BYTECODEMAPPER CORE CompositeScorer OPTIONS END
}
// <<< AUTOGEN: BYTECODEMAPPER CORE CompositeScorer END
