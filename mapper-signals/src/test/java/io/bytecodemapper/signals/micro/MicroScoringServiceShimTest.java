package io.bytecodemapper.signals.micro;

import org.junit.Test;
import static org.junit.Assert.*;

public class MicroScoringServiceShimTest {
    @Test
    @SuppressWarnings("deprecation")
    public void staticScoreMatchesBlend() {
        int a = 0b10101; // bits: 0,2,4 (3 bits)
        int b = 0b00111; // bits: 0,1,2 (3 bits)
        int inter = Integer.bitCount(a & b); // 2 (bits 0,2)
        int uni   = Integer.bitCount(a | b); // 4
        int ca    = Integer.bitCount(a);     // 3
        int cb    = Integer.bitCount(b);     // 3
        double jacc = (uni==0)? 1.0 : ((double) inter) / ((double) uni); // 0.5
        double cos  = (ca==0||cb==0)? 0.0 : (inter / (Math.sqrt((double)ca) * Math.sqrt((double)cb))); // 2/(sqrt(3)*sqrt(3))
        double alpha = 0.60;
        double expected = alpha * jacc + (1.0 - alpha) * cos;
        double got = MicroScoringService.score(a, b, alpha);
        assertEquals(expected, got, 1e-9);
        System.out.println("micro.shim.ok");
    }
}
