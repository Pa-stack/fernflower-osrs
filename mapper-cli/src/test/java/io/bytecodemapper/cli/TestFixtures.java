// >>> AUTOGEN: BYTECODEMAPPER TEST TestFixtures BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;

import java.util.*;

public final class TestFixtures {
    private TestFixtures(){}

    /** Minimal MethodFeatures for tests; fills optional arrays/fields with safe defaults. */
    public static MethodFeatures mf(MethodRef ref) {
        return new MethodFeatures(
                ref,
                0L,
                new java.util.BitSet(),
                false, false,
                /* legacy opcode */ new int[200],
                /* normalized opcode */ new int[200],
                /* strings */ Collections.<String>emptyList(),
                /* invoked */ Collections.<String>emptyList(),
                /* norm desc */ ref.desc,
                /* norm fp */ ""
        );
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST TestFixtures END
