// >>> AUTOGEN: BYTECODEMAPPER TEST IdfStoreTest BEGIN
package io.bytecodemapper.signals.idf;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

public class IdfStoreTest {

    @Test
    public void ema_and_persist_roundtrip() throws Exception {
        IdfStore s = new IdfStore();
        s.setLambda(0.9);
        int[] df = new int[IdfStore.DIM];
        Arrays.fill(df, 10);
        s.updateWeek(1000, df);
        double[] idf1 = s.computeIdf();
        for (double v : idf1) {
            assertTrue("clamped lower bound", v >= 0.5);
            assertTrue("clamped upper bound", v <= 3.0);
        }
        File f = new File("build/test-idf.properties");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        s.save(f);
        IdfStore s2 = IdfStore.load(f);
        double[] idf2 = s2.getIdfVector();
        assertEquals(idf1.length, idf2.length);
        for (int i=0;i<idf1.length;i++) assertEquals(idf1[i], idf2[i], 0.0001);
        // cleanup
        f.delete();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST IdfStoreTest END
