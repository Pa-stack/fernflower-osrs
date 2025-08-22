package io.bytecodemapper.core;

import io.bytecodemapper.core.hash.StableHash64;
import org.junit.Assert;
import org.junit.Test;

public class StableHash64Test {
  @Test public void sameBytes_sameHash() {
    byte[] a = new byte[]{1,2,3,4};
    byte[] b = new byte[]{1,2,3,4};
    long ha = StableHash64.hash(a);
    long hb = StableHash64.hash(b);
    System.out.println("stablehash.same=" + Long.toUnsignedString(ha));
    Assert.assertEquals(ha, hb);
  }
  @Test public void differentBytes_differentHashLikely() {
    byte[] a = new byte[]{1,2,3,4};
    byte[] b = new byte[]{1,2,3,4,5};
    long ha = StableHash64.hash(a);
    long hb = StableHash64.hash(b);
    System.out.println("stablehash.diff=" + Long.toUnsignedString(hb));
    Assert.assertNotEquals(ha, hb);
  }
  @Test public void hashUtf8_nullAndEmpty_consistent() {
    long hNull = StableHash64.hashUtf8(null);
    long hEmpty = StableHash64.hashUtf8("");
    System.out.println("stablehash.empty=" + Long.toUnsignedString(hEmpty));
    Assert.assertEquals(hNull, StableHash64.hash(new byte[0]));
    Assert.assertEquals(hEmpty, StableHash64.hash(new byte[0]));
  }
}
