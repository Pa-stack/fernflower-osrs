// >>> AUTOGEN: BYTECODEMAPPER MicroScore BEGIN
package io.bytecodemapper.signals.score;

import io.bytecodemapper.signals.micro.MicroPattern;

import java.util.BitSet;

public final class MicroScore {
    private MicroScore(){}

    public static double jaccard(BitSet a, BitSet b) {
        BitSet inter = (BitSet) a.clone(); inter.and(b);
        BitSet uni = (BitSet) a.clone(); uni.or(b);
        int u = uni.cardinality();
        return u == 0 ? 0.0 : (double) inter.cardinality() / (double) u;
    }

    public static double cosineWeighted(BitSet a, BitSet b, double[] idf) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < MicroPattern.values().length; i++) {
            double wa = a.get(i) ? idf[i] : 0.0;
            double wb = b.get(i) ? idf[i] : 0.0; // same idf per dim
            dot += wa * wb;
            na += wa * wa;
            nb += wb * wb;
        }
        double den = Math.sqrt(na) * Math.sqrt(nb);
        return den == 0 ? 0.0 : dot / den;
    }

    /** Blend micropattern similarity. */
    public static double blended(BitSet a, BitSet b, double[] idf, double alpha_mp) {
        double j = jaccard(a, b);
        double c = cosineWeighted(a, b, idf);
        return alpha_mp * j + (1.0 - alpha_mp) * c;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER MicroScore END
