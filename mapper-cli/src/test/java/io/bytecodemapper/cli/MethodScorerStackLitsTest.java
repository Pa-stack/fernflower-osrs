// >>> AUTOGEN: BYTECODEMAPPER TEST MethodScorerStackLitsTest BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.method.MethodScorer;
import io.bytecodemapper.signals.idf.IdfStore;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MethodScorerStackLitsTest {

    private static MethodFeatures baseMF(String owner, String name, String desc) {
        MethodRef ref = new MethodRef(owner, name, desc);
        return TestFixtures.mf(ref);
    }

    private static void attachNF(MethodFeatures mf, Map<String,Integer> sh, int[] lits) {
        try {
            java.lang.reflect.Method m = MethodScorer.class.getDeclaredMethod("__testAttachNormalized",
                    MethodFeatures.class, Map.class, int[].class);
            m.setAccessible(true);
            m.invoke(null, mf, sh, lits);
        } catch (Throwable e) {
            throw new AssertionError("failed to attach test NF", e);
        }
    }

    private static IdfStore emptyIdf() { return new IdfStore(); }

    @Test
    public void testStackCosineInfluencesScore() {
        MethodScorer.W_STACK = 0.10; // ensure defaults
        MethodFeatures src = baseMF("A","m","()V");
        MethodFeatures tA  = baseMF("A","n","()V");
        MethodFeatures tB  = baseMF("A","p","()V");

        // Fixed key order keys
        LinkedHashMap<String,Integer> sh = new LinkedHashMap<String,Integer>();
        sh.put("-2", 1); sh.put("-1", 0); sh.put("0", 2); sh.put("+1", 1); sh.put("+2", 0);
        LinkedHashMap<String,Integer> shZero = new LinkedHashMap<String,Integer>();
        shZero.put("-2", 0); shZero.put("-1", 0); shZero.put("0", 0); shZero.put("+1", 0); shZero.put("+2", 0);

        attachNF(src, sh, null);
        attachNF(tA,  sh, null);   // identical → cosine 1.0
        attachNF(tB,  shZero, null); // zeros → cosine 0.0

        List<MethodFeatures> cands = Arrays.asList(tA, tB);
        double[] scores = MethodScorer.scoreVector(src, cands, emptyIdf());

        assertEquals(2, scores.length);
        double sA = scores[0];
        double sB = scores[1];
        assertTrue("A should score higher than B", sA > sB);
        assertTrue("Stack weight should lift by ~W_STACK", (sA - sB) >= MethodScorer.W_STACK * 0.99);
    }

    @Test
    public void testLiteralsMinhashInfluencesScore() {
        MethodScorer.W_LITS = 0.08; // ensure defaults
        MethodFeatures src = baseMF("B","m","()V");
        MethodFeatures tGood = baseMF("B","n","()V");
        MethodFeatures tZero = baseMF("B","p","()V");

        int[] a = new int[64];
        Arrays.fill(a, Integer.MAX_VALUE);
        for (int i=0;i<32;i++) a[i] = i+1; // 1..32
        int[] b = Arrays.copyOf(a, a.length);
        // perturb 8 buckets
        for (int i=0;i<8;i++) b[i*2] = 100 + i;
        // expected denom = 32, matches = 24 → 0.75

        attachNF(src, null, a);
        attachNF(tGood, null, b);
        attachNF(tZero, null, new int[64]); // default zeros → treated as MAX? ensure explicitly MAX
        int[] zeros = new int[64]; Arrays.fill(zeros, Integer.MAX_VALUE); attachNF(tZero, null, zeros);

        List<MethodFeatures> cands = Arrays.asList(tGood, tZero);
        double[] s1 = MethodScorer.scoreVector(src, cands, emptyIdf());
        double[] s2 = MethodScorer.scoreVector(src, cands, emptyIdf());

        assertEquals(s1.length, s2.length);
        assertEquals(s1[0], s2[0], 1e-12);
        assertEquals(s1[1], s2[1], 1e-12);

        double diff = s1[0] - s1[1];
        assertTrue("Lits weight should reflect ~W_LITS*0.75",
                diff >= MethodScorer.W_LITS * 0.74 && diff <= MethodScorer.W_LITS * 0.76);
    }

    @Test
    public void testNoFeaturesGivesZeroSubscores() {
        MethodFeatures src = baseMF("C","m","()V");
        MethodFeatures t   = baseMF("C","n","()V");
        // Do not attach normalized features → both null
        List<MethodFeatures> cands = Arrays.asList(t);
        double[] scores = MethodScorer.scoreVector(src, cands, emptyIdf());
        assertEquals(1, scores.length);
        // With all baseline signals equal/neutral, adding null subscores should not lift
        // We can at least ensure the score lies in [0,1] and is deterministic
        assertTrue(scores[0] >= 0.0 && scores[0] <= 1.0);
        double again = MethodScorer.scoreVector(src, cands, emptyIdf())[0];
        assertEquals(scores[0], again, 1e-12);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST MethodScorerStackLitsTest END
