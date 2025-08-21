// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGateTelemetryTest BEGIN
package io.bytecodemapper.core.match;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlatteningGateTelemetryTest {
    @Test
    public void telemetryCountersExist() {
        MethodMatcher.MethodMatchResult r = new MethodMatcher.MethodMatchResult();
        r.flatteningDetected++;
        r.nearBeforeGates += 2;
        r.nearAfterGates += 1;
        assertTrue(r.flatteningDetected >= 1);
        assertEquals(2, r.nearBeforeGates);
        assertEquals(1, r.nearAfterGates);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGateTelemetryTest END
