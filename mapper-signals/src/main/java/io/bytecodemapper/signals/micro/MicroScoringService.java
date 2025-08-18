// >>> AUTOGEN: BYTECODEMAPPER MicroScoringService BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.signals.idf.IdfStore;

import java.util.Arrays;
import java.util.BitSet;

/** Tiny service wrapper for micropattern similarity using an IDF vector. */
public final class MicroScoringService {
    private double[] idf = new double[IdfStore.DIM];

    public MicroScoringService() {
        Arrays.fill(this.idf, 1.0d);
    }

    public MicroScoringService setIdf(double[] idf) {
        if (idf == null || idf.length != IdfStore.DIM) throw new IllegalArgumentException("idf length " + IdfStore.DIM);
        this.idf = Arrays.copyOf(idf, idf.length);
        return this;
    }

    public double[] getIdf() { return Arrays.copyOf(idf, idf.length); }

    public double similarity(BitSet a, BitSet b, double alphaMp) {
        return MicroScore.blended(a, b, idf, alphaMp);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroScoringService END
