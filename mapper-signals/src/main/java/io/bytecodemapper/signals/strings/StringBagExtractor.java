// >>> AUTOGEN: BYTECODEMAPPER StringBagExtractor BEGIN
package io.bytecodemapper.signals.strings;

import org.objectweb.asm.tree.*;
import java.util.*;

/** Extract string constants from a method (lightweight). */
public final class StringBagExtractor {
    private StringBagExtractor(){}

    /** Extracts raw string constants; filters length < 2 to reduce noise. */
    public static List<String> extract(MethodNode mn) {
        ArrayList<String> out = new ArrayList<String>();
        for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof LdcInsnNode) {
                Object c = ((LdcInsnNode) p).cst;
                if (c instanceof String) {
                    String s = (String) c;
                    if (s != null && s.length() >= 2) out.add(s);
                }
            }
        }
        return out;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER StringBagExtractor END
