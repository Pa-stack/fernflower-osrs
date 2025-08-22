package io.bytecodemapper.core.hash;

/**
 * Deterministic 64-bit FNV-1a hash with fixed seed, for tie-breaks and cache keys.
 * Stable across JVMs; no randomness.
 */
public final class StableHash64 {
  // Standard FNV-1a 64-bit constants (unchanged for determinism)
  private static final long FNV_OFFSET = 0xcbf29ce484222325L;
  private static final long FNV_PRIME  = 0x100000001b3L;
  private StableHash64() {}

  public static long hash(byte[] data) {
    long h = FNV_OFFSET;
    for (int i = 0; i < data.length; i++) {
      h ^= (data[i] & 0xff);
      h *= FNV_PRIME;
    }
    return h;
  }

  public static long hash(byte[] a, byte[] b) {
    long h = FNV_OFFSET;
    for (int i = 0; i < a.length; i++) { h ^= (a[i] & 0xff); h *= FNV_PRIME; }
    for (int i = 0; i < b.length; i++) { h ^= (b[i] & 0xff); h *= FNV_PRIME; }
    return h;
  }

  public static long hashUtf8(String s) {
    byte[] bytes = s == null ? new byte[0] : s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return hash(bytes);
  }
}
