// >>> AUTOGEN: BYTECODEMAPPER GreedyAssignTest BEGIN
package io.bytecodemapper.core.assign;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class GreedyAssignTest {
    @Test
    public void simpleAssign() {
        double[][] s = new double[][]{
            {0.1, 0.9},
            {0.8, 0.2}
        };
        List<int[]> pairs = GreedyAssign.assign(s);
        assertEquals(2, pairs.size());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER GreedyAssignTest END
