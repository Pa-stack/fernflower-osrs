// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGatesTelemetryTest BEGIN
package io.bytecodemapper.core.match;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlatteningGatesTelemetryTest {
    @Test
    public void resultTelemetryFieldsPresent() {
        MethodMatcher.MethodMatchResult r = new MethodMatcher.MethodMatchResult();
        // Ensure fields exist and are mutable ints
        r.flatteningDetected += 0;
        r.nearBeforeGates += 0;
        r.nearAfterGates += 0;
        assertEquals(0, r.flatteningDetected);
        assertEquals(0, r.nearBeforeGates);
        assertEquals(0, r.nearAfterGates);
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST FlatteningGatesTelemetryTest END
