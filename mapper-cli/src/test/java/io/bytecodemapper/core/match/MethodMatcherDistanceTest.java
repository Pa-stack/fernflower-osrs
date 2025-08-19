// >>> AUTOGEN: BYTECODEMAPPER TEST WL_DISTANCE BEGIN
package io.bytecodemapper.core.match;

import org.junit.Test;
import static org.junit.Assert.*;

public class MethodMatcherDistanceTest {
    @Test public void wlDistanceZeroForIdentical() {
        assertEquals(0, invoke("a|b|b", "a|b|b"));
    }
    @Test public void wlDistanceOneForSingleTokenDiff() {
        assertEquals(1, invoke("a|b", "a|b|b"));
    }
    @Test public void wlDistanceTwoForTwoTokenDiffs() {
        assertEquals(2, invoke("a|b", "a|c|b|c"));
    }
    private static int invoke(String a, String b) {
        try {
            java.lang.reflect.Method m = MethodMatcher.class.getDeclaredMethod("wlMultisetDistance", String.class, String.class);
            m.setAccessible(true);
            return ((Integer)m.invoke(null, a, b)).intValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST WL_DISTANCE END
