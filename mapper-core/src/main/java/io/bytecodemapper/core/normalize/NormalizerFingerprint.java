// >>> AUTOGEN: BYTECODEMAPPER CORE NormalizerFingerprint BEGIN
package io.bytecodemapper.core.normalize;

/**
 * Stable, compact fingerprint for Normalizer options and version.
 * Bump NORMALIZER_VERSION if semantics change in a way that affects IR/CFG shape.
 */
public final class NormalizerFingerprint {
    private NormalizerFingerprint() {}

    /** Bump when normalization semantics change. */
    public static final String NORMALIZER_VERSION = "1";

    /**
     * Deterministic options fingerprint string.
     * Extend if you add new options that affect analysis CFG.
     */
    public static String optionsFingerprint(Normalizer.Options o) {
        if (o == null) o = Normalizer.Options.defaults();
        StringBuilder sb = new StringBuilder(64);
        sb.append("v=").append(NORMALIZER_VERSION)
          .append(";opaque=").append(o.normalizeOpaque)
          .append(";unwrapRTE=").append(o.removeTrivialRuntimeWrapper)
          .append(";detectFlatten=").append(o.detectFlattening);
        return sb.toString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CORE NormalizerFingerprint END
