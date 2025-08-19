// >>> AUTOGEN: BYTECODEMAPPER CORE IRFingerprint BEGIN
package io.bytecodemapper.core.ir;

import io.bytecodemapper.core.normalize.NormalizerFingerprint;
import io.bytecodemapper.core.normalize.Normalizer;
import io.bytecodemapper.core.cfg.ReducedCfgFingerprint;
import io.bytecodemapper.core.cfg.ReducedCFG;

/** Compose a compact, deterministic fingerprint of IR-affecting options. */
public final class IRFingerprint {
    private IRFingerprint() {}

    public static String compose(Normalizer.Options nopt, ReducedCFG.Options copt) {
        String nf = NormalizerFingerprint.optionsFingerprint(nopt);
        String cf = ReducedCfgFingerprint.optionsFingerprint(copt);
        return nf + "||" + cf;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CORE IRFingerprint END
