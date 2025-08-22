// >>> AUTOGEN: BYTECODEMAPPER MicroScoringService BEGIN
package io.bytecodemapper.signals.micro;

import java.util.Arrays;
import java.util.BitSet;

/** Tiny service wrapper for micropattern similarity using an IDF vector. */
public final class MicroScoringService {
    private static final int DIM = MicroPattern.values().length;
    private double[] idf = new double[DIM];

    public MicroScoringService() {
        Arrays.fill(this.idf, 1.0d);
    }

    public MicroScoringService setIdf(double[] idf) {
        if (idf == null || idf.length != DIM) throw new IllegalArgumentException("idf length " + DIM);
        this.idf = Arrays.copyOf(idf, idf.length);
        return this;
    }

    public double[] getIdf() { return Arrays.copyOf(idf, idf.length); }

    public double similarity(BitSet a, BitSet b, double alphaMp) {
        return MicroScore.blended(a, b, idf, alphaMp);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroScoringService END
