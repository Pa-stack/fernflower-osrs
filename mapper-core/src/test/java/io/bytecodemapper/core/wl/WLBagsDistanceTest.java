// >>> AUTOGEN: BYTECODEMAPPER TEST WLBagsDistanceTest BEGIN
package io.bytecodemapper.core.wl;

import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;
import org.junit.Assert;
import org.junit.Test;

public class WLBagsDistanceTest {

    private static Long2IntSortedMap bag(long[] keys, int[] vals) {
        Long2IntAVLTreeMap m = new Long2IntAVLTreeMap();
        for (int i = 0; i < keys.length; i++) m.put(keys[i], vals[i]);
        return m;
    }

    @Test
    public void l1Distances_0_to_3() {
        Long2IntSortedMap a = bag(new long[]{1L, 2L, 3L}, new int[]{2, 1, 1});
        Long2IntSortedMap b0 = bag(new long[]{1L, 2L, 3L}, new int[]{2, 1, 1}); // identical
        Long2IntSortedMap b1 = bag(new long[]{1L, 2L, 3L}, new int[]{3, 1, 0}); // move one count from 3->1
        Long2IntSortedMap b2 = bag(new long[]{1L, 2L, 4L}, new int[]{2, 1, 1}); // replace 3 with 4 (1 add, 1 remove)
        Long2IntSortedMap b3 = bag(new long[]{1L, 2L, 3L, 5L}, new int[]{2, 1, 1, 1}); // add extra token

        Assert.assertEquals(0, WLBags.l1(a, b0));
        Assert.assertEquals(2, WLBags.l1(a, b1));
        Assert.assertEquals(2, WLBags.l1(a, b2));
        Assert.assertEquals(1, WLBags.l1(a, b3));
    }

    @Test
    public void sizeBand_plusMinusTenPercent() {
        Long2IntSortedMap a = bag(new long[]{10L, 20L}, new int[]{5, 5}); // total 10
        Long2IntSortedMap okLow  = bag(new long[]{10L}, new int[]{9});    // 9 within 10%
        Long2IntSortedMap okHigh = bag(new long[]{10L}, new int[]{11});   // 11 within 10%
        Long2IntSortedMap badLow = bag(new long[]{10L}, new int[]{8});    // 8 outside 10%
        Long2IntSortedMap badHigh= bag(new long[]{10L}, new int[]{12});   // 12 outside 10%

        Assert.assertTrue(WLBags.withinBand(a, okLow, 0.10));
        Assert.assertTrue(WLBags.withinBand(a, okHigh, 0.10));
        Assert.assertFalse(WLBags.withinBand(a, badLow, 0.10));
        Assert.assertFalse(WLBags.withinBand(a, badHigh, 0.10));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST WLBagsDistanceTest END
