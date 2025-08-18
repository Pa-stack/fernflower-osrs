// >>> AUTOGEN: BYTECODEMAPPER core ClassFingerprint BEGIN
package io.bytecodemapper.core.fingerprint;

import java.util.Arrays;

/**
 * Class-level fingerprint aggregating:
 * - 17-bit micropattern histogram (frozen ABI order below)
 * - Multiset of WL method signatures (64-bit IDs)
 *
 * Frozen 17-bit order:
 * 0  NoParams
 * 1  NoReturn
 * 2  Recursive
 * 3  SameName
 * 4  Leaf
 * 5  ObjectCreator
 * 6  FieldReader
 * 7  FieldWriter
 * 8  TypeManipulator
 * 9  StraightLine
 * 10 Looping
 * 11 Exceptions
 * 12 LocalReader
 * 13 LocalWriter
 * 14 ArrayCreator
 * 15 ArrayReader
 * 16 ArrayWriter
 */
public final class ClassFingerprint {
    public static final int MICRO_BITS = 17; // do not change without version bump

    private final String internalName; // e.g., pkg/Foo
    private final int[] microHistogram; // length 17
    private final MethodSigBag methodSigs;
    // --- extended metadata for scoring ---
    private final int methodCount;
    private final int fieldCount;
    private final String superName;   // may be null
    private final String[] interfaces; // never null; may be empty

    // >>> AUTOGEN: BYTECODEMAPPER core ClassFingerprint CTOR(v1) BEGIN
    public ClassFingerprint(String internalName, int[] microHistogram, MethodSigBag methodSigs) {
        if (microHistogram == null || microHistogram.length != MICRO_BITS) {
            throw new IllegalArgumentException("microHistogram length must be " + MICRO_BITS);
        }
        this.internalName = internalName;
        this.microHistogram = Arrays.copyOf(microHistogram, microHistogram.length);
        this.methodSigs = methodSigs;
        // defaults for extended fields
        this.methodCount = 0;
        this.fieldCount = 0;
        this.superName = null;
        this.interfaces = new String[0];
    }
    // >>> AUTOGEN: BYTECODEMAPPER core ClassFingerprint CTOR(v1) END

    // >>> AUTOGEN: BYTECODEMAPPER core ClassFingerprint CTOR(v2) BEGIN
    public ClassFingerprint(String internalName,
                            int[] microHistogram,
                            MethodSigBag methodSigs,
                            int methodCount,
                            int fieldCount,
                            String superName,
                            String[] interfaces) {
        if (microHistogram == null || microHistogram.length != MICRO_BITS) {
            throw new IllegalArgumentException("microHistogram length must be " + MICRO_BITS);
        }
        this.internalName = internalName;
        this.microHistogram = Arrays.copyOf(microHistogram, microHistogram.length);
        this.methodSigs = methodSigs;
        this.methodCount = methodCount;
        this.fieldCount = fieldCount;
        this.superName = superName;
        this.interfaces = interfaces == null ? new String[0] : Arrays.copyOf(interfaces, interfaces.length);
    }
    // >>> AUTOGEN: BYTECODEMAPPER core ClassFingerprint CTOR(v2) END

    public String internalName() { return internalName; }
    public int[] microHistogram() { return Arrays.copyOf(microHistogram, microHistogram.length); }
    public MethodSigBag methodSigs() { return methodSigs; }
    public int methodCount() { return methodCount; }
    public int fieldCount() { return fieldCount; }
    public String superName() { return superName; }
    public String[] interfaces() { return Arrays.copyOf(interfaces, interfaces.length); }
}
// <<< AUTOGEN: BYTECODEMAPPER core ClassFingerprint END
