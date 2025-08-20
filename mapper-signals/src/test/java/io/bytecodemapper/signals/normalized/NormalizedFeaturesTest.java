// >>> AUTOGEN: BYTECODEMAPPER TEST NormalizedFeaturesTest BEGIN
package io.bytecodemapper.signals.normalized;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class NormalizedFeaturesTest {

    @Test
    public void nsfFingerprintDeterministic() {
        // Minimal deterministic maps
        Map<String,Integer> op = new LinkedHashMap<String,Integer>();
        op.put("1", 2); op.put("2", 1);
        Map<String,Integer> ck = new LinkedHashMap<String,Integer>();
        ck.put("STATIC", 1);
        Map<String,Integer> sh = new LinkedHashMap<String,Integer>();
        sh.put("0", 3);
        NormalizedFeatures.TryCatchShape tc = new NormalizedFeatures.TryCatchShape(0, 0, 0);
        NormalizedFeatures.MinHash32 lits = new NormalizedFeatures.MinHash32(new int[]{1,2,3});
        NormalizedFeatures.TfIdfSketch strs = new NormalizedFeatures.TfIdfSketch(Collections.singletonMap("s", 1.0f));

    NormalizedMethod dummy = new NormalizedMethod("A", new org.objectweb.asm.tree.MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "m", "()V", null, null), Collections.<Integer>emptySet());
        long a = callFingerprint(dummy, op, ck, sh, tc, lits, strs);
        long b = callFingerprint(dummy, op, ck, sh, tc, lits, strs);
        assertEquals(a, b);
    }

    private static long callFingerprint(NormalizedMethod nm,
                                        Map<String,Integer> op,
                                        Map<String,Integer> ck,
                                        Map<String,Integer> sh,
                                        NormalizedFeatures.TryCatchShape tc,
                                        NormalizedFeatures.MinHash32 lits,
                                        NormalizedFeatures.TfIdfSketch strs) {
        try {
            java.lang.reflect.Method m = NormalizedMethod.class.getDeclaredMethod("buildFingerprintNSFv1", Map.class, Map.class, Map.class, NormalizedFeatures.TryCatchShape.class, NormalizedFeatures.MinHash32.class, NormalizedFeatures.TfIdfSketch.class);
            m.setAccessible(true);
            Object v = m.invoke(null, op, ck, sh, tc, lits, strs);
            return ((Long) v).longValue();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST NormalizedFeaturesTest END
