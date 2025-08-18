// >>> AUTOGEN: BYTECODEMAPPER BitOrderTest BEGIN
package io.bytecodemapper.signals.micro;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BitOrderTest {
    @Test
    public void frozenOrderHas17() {
        assertEquals(17, MicroPattern.values().length);
        // Spot-check indices
        assertEquals("NoParams", MicroPattern.values()[0].name());
        assertEquals("ArrayWriter", MicroPattern.values()[16].name());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER BitOrderTest END
