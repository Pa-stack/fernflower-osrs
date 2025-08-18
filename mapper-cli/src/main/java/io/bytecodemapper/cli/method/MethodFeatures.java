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

    public MethodFeatures(MethodRef ref, long wlSignature, BitSet microBits, boolean leaf, boolean recursive,
                          int[] opcodeHistogram, List<String> callBagNormalized, List<String> stringBag) {
        this.ref = ref;
        this.wlSignature = wlSignature;
        this.microBits = microBits;
        this.leaf = leaf;
        this.recursive = recursive;
        this.opcodeHistogram = opcodeHistogram;
        this.callBagNormalized = callBagNormalized;
        this.stringBag = stringBag;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodFeatures END
