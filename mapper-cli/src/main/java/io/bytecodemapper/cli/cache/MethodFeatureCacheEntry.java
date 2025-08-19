// >>> AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCacheEntry BEGIN
package io.bytecodemapper.cli.cache;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;

public final class MethodFeatureCacheEntry implements Serializable {
    public final long wlSignature;                 // final WL method signature
    public final BitSet microBits;                 // 17-bit micropatterns
    public final Map<Integer,Integer> normOpcodeHistogram; // generalized histogram (opcode -> count)
    public final Set<String> strings;              // filtered strings
    public final Set<String> invokedSignatures;    // call-bag (owner.name+desc)
    public final String normalizedDescriptor;      // descriptor after opaque param drop (if any)
    public final String normFingerprint;           // NormalizedMethod fingerprint (sha-256)
    public final String normalizedBodyHash;        // our normalized body SHA-256

    public MethodFeatureCacheEntry(long wlSignature,
                                   BitSet microBits,
                                   Map<Integer,Integer> normOpcodeHistogram,
                                   Set<String> strings,
                                   Set<String> invokedSignatures,
                                   String normalizedDescriptor,
                                   String normFingerprint,
                                   String normalizedBodyHash) {
        this.wlSignature = wlSignature;
        this.microBits = (BitSet) microBits.clone();
        this.normOpcodeHistogram = normOpcodeHistogram;
        this.strings = strings;
        this.invokedSignatures = invokedSignatures;
        this.normalizedDescriptor = normalizedDescriptor;
        this.normFingerprint = normFingerprint;
        this.normalizedBodyHash = normalizedBodyHash;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCacheEntry END
