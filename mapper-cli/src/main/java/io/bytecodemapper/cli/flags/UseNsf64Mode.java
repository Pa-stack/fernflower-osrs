// >>> AUTOGEN: BYTECODEMAPPER CLI Flags UseNsf64Mode BEGIN
package io.bytecodemapper.cli.flags;

/** Rollout mode for using nsf64 as the canonical fingerprint. */
public enum UseNsf64Mode {
    CANONICAL,
    SURROGATE,
    BOTH;

    public static UseNsf64Mode parse(String s) {
        if (s == null) return CANONICAL;
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        if ("canonical".equals(t)) return CANONICAL;
        if ("surrogate".equals(t)) return SURROGATE;
        if ("both".equals(t)) return BOTH;
        return CANONICAL;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Flags UseNsf64Mode END
