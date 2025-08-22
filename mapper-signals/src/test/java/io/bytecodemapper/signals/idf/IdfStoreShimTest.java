package io.bytecodemapper.signals.idf;

import org.junit.Test;
import static org.junit.Assert.*;

public class IdfStoreShimTest {
    @Test
    @SuppressWarnings("deprecation")
    public void defaultCtorAndSetLambdaDoNotBreakUpdate() {
        IdfStore s = new IdfStore().setLambda(0.9);
        // Should not throw
        s.update("t", 1, 0);
        double v = s.get("t", -1);
        assertTrue("value should be clamped to [0.5,3.0]", v >= 0.5 && v <= 3.0);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void computeIdfReturns17Ones() {
        double[] arr = new IdfStore().computeIdf();
        assertNotNull(arr);
        assertEquals(17, arr.length);
        for (double d : arr) assertEquals(1.0, d, 0.0);
        System.out.println("idf.compute.len=" + arr.length);
    }
}
