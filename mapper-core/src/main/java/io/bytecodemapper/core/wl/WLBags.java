// >>> AUTOGEN: BYTECODEMAPPER WLBags BEGIN
package io.bytecodemapper.core.wl;

import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;

// CODEGEN-BEGIN: wl-bags-utils
public final class WLBags {
  private WLBags() {}

  // Deterministic L1 distance over two sorted multisets.
  public static int l1(Long2IntSortedMap a, Long2IntSortedMap b) {
    LongBidirectionalIterator ia = a.keySet().iterator();
    LongBidirectionalIterator ib = b.keySet().iterator();
    long ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
    long kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
    int d = 0;
    while (ka != Long.MAX_VALUE || kb != Long.MAX_VALUE) {
      if (ka == kb) {
        d += Math.abs(a.get(ka) - b.get(kb));
        ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
        kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
      } else if (ka < kb) {
        d += a.get(ka);
        ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
      } else { // ka > kb
        d += b.get(kb);
        kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
      }
    }
    return d;
  }

  // Size-band check: bag sizes within ±pct (e.g., 10%).
  public static boolean withinBand(Long2IntSortedMap a, Long2IntSortedMap b, double pct) {
    int sa = sum(a); int sb = sum(b);
    int lo = (int)Math.floor(sa * (1.0 - pct));
    int hi = (int)Math.ceil (sa * (1.0 + pct));
    return sb >= lo && sb <= hi;
  }

  private static int sum(Long2IntSortedMap m) {
    int s = 0; LongBidirectionalIterator it = m.keySet().iterator();
    while (it.hasNext()) s += m.get(it.nextLong());
    return s;
  }
}
// CODEGEN-END: wl-bags-utils
// <<< AUTOGEN: BYTECODEMAPPER WLBags END
