package io.bytecodemapper.core.match;

import org.junit.Test;
import static org.junit.Assert.*;

public class MethodMatcherRelaxedFilterTest {
    private static int dist(String a, String b) {
        try {
            java.lang.reflect.Method m = MethodMatcher.class.getDeclaredMethod("wlMultisetDistance", String.class, String.class);
            m.setAccessible(true);
            return ((Integer)m.invoke(null, a, b)).intValue();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test public void distanceZeroIncluded() { assertEquals(0, dist("a|b|b", "a|b|b")); }
    @Test public void distanceOneIncluded()  { assertEquals(1, dist("a|b", "a|b|b")); }
    @Test public void distanceTwoExcluded()  { assertEquals(2, dist("a|b", "a|c|b|c")); }
}
