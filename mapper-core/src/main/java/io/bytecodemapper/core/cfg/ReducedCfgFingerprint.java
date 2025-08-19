// >>> AUTOGEN: BYTECODEMAPPER CORE ReducedCfgFingerprint BEGIN
package io.bytecodemapper.core.cfg;

/**
 * Stable fingerprint for CFG-affecting ReducedCFG options.
 * Bump CFG_VERSION when semantics change in a way that affects CFG shape/edges.
 */
public final class ReducedCfgFingerprint {
    private ReducedCfgFingerprint() {}

    /** Bump when CFG semantics change. */
    public static final String CFG_VERSION = "1";

    /** Deterministic options fingerprint string. */
    public static String optionsFingerprint(ReducedCFG.Options o) {
        if (o == null) o = ReducedCFG.Options.defaults();
        StringBuilder sb = new StringBuilder(64);
        sb.append("cv=").append(CFG_VERSION)
          .append(";excEdges=").append(o.exceptionEdgesPolicy.name())
          .append(";mergeLinear=").append(o.mergeLinearChains);
        return sb.toString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CORE ReducedCfgFingerprint END
