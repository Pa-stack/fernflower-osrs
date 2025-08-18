// >>> AUTOGEN: BYTECODEMAPPER StableHash64 BEGIN
package io.bytecodemapper.core.hash;

/**
 * Stable 64-bit FNV-1a hash (pure Java), fixed-seed deterministic.
 * Acceptable xxHash64 alternative for determinism on Java 8.
 */
public final class StableHash64 {
    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;

    private StableHash64() {}

    public static long hashBytes(byte[] data) {
        long h = FNV64_OFFSET_BASIS;
        for (byte b : data) {
            h ^= (b & 0xff);
            h *= FNV64_PRIME;
        }
        return h;
    }

    public static long hashUtf8(String s) {
        return hashBytes(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER StableHash64 END
