// >>> AUTOGEN: BYTECODEMAPPER MicroScoringService BEGIN
package io.bytecodemapper.signals.micro;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

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

    // --- Back-compat static shim API expected by CLI legacy code ---
    /** Fixed ABI bit width (frozen order: 17). */
    @Deprecated
    public static int abiBits() { return 17; }

    /** No-op IDF over bitsets: returns 17×1.0 ignoring input masks. */
    @Deprecated
    public static double[] idfOnBits(List<Integer> masks) {
        double[] d = new double[17];
        Arrays.fill(d, 1.0);
        return d;
    }

    /** Score with default alpha=0.60 using Jaccard/Cosine blend on integer masks. */
    @Deprecated
    public static double score(int aMask, int bMask) { return score(aMask, bMask, 0.60); }

    /** Score with provided alpha using Jaccard/Cosine blend; ignores bitIdf by default. */
    @Deprecated
    public static double score(int aMask, int bMask, double alpha) { return score(aMask, bMask, alpha, null); }

    /** Score with provided alpha and optional bit IDF (ignored, safe default behavior). */
    @Deprecated
    public static double score(int aMask, int bMask, double alpha, double[] bitIdf) {
        final int inter = Integer.bitCount(aMask & bMask);
        final int uni   = Integer.bitCount(aMask | bMask);
        final int ca    = Integer.bitCount(aMask);
        final int cb    = Integer.bitCount(bMask);
        final double jacc = (uni==0)? 1.0 : ((double) inter) / ((double) uni);
        final double cos  = (ca==0 || cb==0)? 0.0 : (inter / (Math.sqrt((double)ca) * Math.sqrt((double)cb)));
        double s = alpha * jacc + (1.0 - alpha) * cos;
        if (s < 0.0) s = 0.0; else if (s > 1.0) s = 1.0;
        return s;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroScoringService END
