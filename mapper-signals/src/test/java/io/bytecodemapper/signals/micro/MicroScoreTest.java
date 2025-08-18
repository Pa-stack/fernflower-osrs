// >>> AUTOGEN: BYTECODEMAPPER TEST MicroScoreTest BEGIN
package io.bytecodemapper.signals.micro;

import io.bytecodemapper.signals.idf.IdfStore;
import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.*;

public class MicroScoreTest {

    @Test
    public void blended_basic() {
        BitSet a = new BitSet(17); a.set(0); a.set(1); a.set(2);
        BitSet b = new BitSet(17); b.set(1); b.set(2); b.set(3);
        double[] idf = new double[17];
        for (int i=0;i<idf.length;i++) idf[i] = 1.0;

        double j = MicroScore.jaccard(a, b); // |{1,2}| / |{0,1,2,3}| = 2/4 = 0.5
        assertEquals(0.5, j, 1e-9);

        double c = MicroScore.cosineWeighted(a, b, idf);
        // vectors are [1,1,1,0] and [0,1,1,1], cosine = (1+1)/(sqrt(3)*sqrt(3)) = 2/3
        assertEquals(2.0/3.0, c, 1e-9);

        double s = MicroScore.blended(a, b, idf, 0.6);
        assertEquals(0.6*0.5 + 0.4*(2.0/3.0), s, 1e-9);
    }

    @Test
    public void cosine_zero_when_one_empty() {
        BitSet a = new BitSet(17);
        BitSet b = new BitSet(17); b.set(2);
        double[] idf = new double[17]; for (int i=0;i<idf.length;i++) idf[i]=1.0;
        assertEquals(0.0, MicroScore.cosineWeighted(a, b, idf), 1e-9);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MicroScoreTest END
