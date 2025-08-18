// >>> AUTOGEN: BYTECODEMAPPER MicroScore BEGIN
package io.bytecodemapper.signals.micro;

import java.util.BitSet;

/** Micropattern similarity utilities. */
public final class MicroScore {
    private MicroScore(){}

    /** Jaccard over bits a ∩ b / a ∪ b. Returns 0.0 if both empty. */
    public static double jaccard(BitSet a, BitSet b) {
        if (a == null || b == null) return 0.0;
        BitSet inter = (BitSet) a.clone(); inter.and(b);
        BitSet union = (BitSet) a.clone(); union.or(b);
        int u = union.cardinality();
        if (u == 0) return 0.0;
        return inter.cardinality() * 1.0 / u;
    }

    /**
     * Cosine over IDF-weighted 17D binary vectors.
     * x_i = idf[i] if bit set in a else 0; y_i = idf[i] if bit set in b else 0.
     */
    public static double cosineWeighted(BitSet a, BitSet b, double[] idf) {
        if (a == null || b == null || idf == null) return 0.0;
        double dot = 0.0, nx2 = 0.0, ny2 = 0.0;
        // intersection contributes idf^2 to dot
        BitSet inter = (BitSet) a.clone(); inter.and(b);
        for (int i = inter.nextSetBit(0); i >= 0; i = inter.nextSetBit(i+1)) {
            double w = idfAt(idf, i);
            dot += w * w;
        }
        for (int i = a.nextSetBit(0); i >= 0; i = a.nextSetBit(i+1)) {
            double w = idfAt(idf, i);
            nx2 += w * w;
        }
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i+1)) {
            double w = idfAt(idf, i);
            ny2 += w * w;
        }
        if (nx2 == 0.0 || ny2 == 0.0) return 0.0;
        return dot / (Math.sqrt(nx2) * Math.sqrt(ny2));
    }

    private static double idfAt(double[] idf, int i) {
        return (i >= 0 && i < idf.length) ? idf[i] : 0.0;
    }

    /** Blended micropattern similarity: alpha*Jaccard + (1-alpha)*CosineWeighted. */
    public static double blended(BitSet a, BitSet b, double[] idf, double alphaMp) {
        double alpha = Math.max(0.0, Math.min(1.0, alphaMp));
        double j = jaccard(a, b);
        double c = cosineWeighted(a, b, idf);
        return alpha * j + (1.0 - alpha) * c;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroScore END
