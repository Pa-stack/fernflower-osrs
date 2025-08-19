// >>> AUTOGEN: BYTECODEMAPPER CLI MethodFeatures BEGIN
package io.bytecodemapper.cli.method;

import java.util.BitSet;
import java.util.List;

public final class MethodFeatures {
    public final MethodRef ref;
    public final long wlSignature;
    public final BitSet microBits;     // 17-bit
    public final boolean leaf;
    public final boolean recursive;
    public final int[] opcodeHistogram; // [0..199]
    public final List<String> callBagNormalized; // owner-normalized to new-space
    public final List<String> stringBag;
    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodFeatures NORMALIZED FIELDS BEGIN
    /** Normalized descriptor after opaque-param policy (stub: identical to desc when not used). */
    public final String normalizedDescriptor;
    /** Stable SHA-256 fingerprint over normalized features (descriptor+opcodes+invoked+strings). */
    public final String normalizedFingerprint;
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodFeatures NORMALIZED FIELDS END

    public MethodFeatures(MethodRef ref, long wlSignature, BitSet microBits, boolean leaf, boolean recursive,
                          int[] opcodeHistogram, List<String> callBagNormalized, List<String> stringBag,
                          String normalizedDescriptor, String normalizedFingerprint) {
        this.ref = ref;
        this.wlSignature = wlSignature;
        this.microBits = microBits;
        this.leaf = leaf;
        this.recursive = recursive;
        this.opcodeHistogram = opcodeHistogram;
        this.callBagNormalized = callBagNormalized;
        this.stringBag = stringBag;
        // >>> AUTOGEN: BYTECODEMAPPER CLI MethodFeatures NORMALIZED FIELDS INIT BEGIN
        this.normalizedDescriptor = normalizedDescriptor;
        this.normalizedFingerprint = normalizedFingerprint;
        // <<< AUTOGEN: BYTECODEMAPPER CLI MethodFeatures NORMALIZED FIELDS INIT END
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodFeatures END
