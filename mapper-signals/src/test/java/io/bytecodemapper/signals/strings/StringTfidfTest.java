// >>> AUTOGEN: BYTECODEMAPPER TEST StringTfidfTest BEGIN
package io.bytecodemapper.signals.strings;

import io.bytecodemapper.signals.tfidf.TfIdfModel;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

import static org.junit.Assert.*;

public class StringTfidfTest implements Opcodes {

    private static MethodNode stringsABC() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()V", null, null);
        InsnList in = m.instructions = new InsnList();
        in.add(new LdcInsnNode("alpha"));
        in.add(new LdcInsnNode("beta"));
        in.add(new LdcInsnNode("gamma"));
        in.add(new InsnNode(RETURN));
        return m;
    }

    private static MethodNode stringsCAB_dupBeta() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()V", null, null);
        InsnList in = m.instructions = new InsnList();
        in.add(new LdcInsnNode("gamma"));
        in.add(new LdcInsnNode("alpha"));
        in.add(new LdcInsnNode("beta"));
        in.add(new LdcInsnNode("beta")); // duplicate to confirm TF handling
        in.add(new InsnNode(RETURN));
        return m;
    }

    @Test
    public void orderInvariantButTfAware() {
        List<String> s1 = StringBagExtractor.extract(stringsABC());
        List<String> s2 = StringBagExtractor.extract(stringsCAB_dupBeta());

        TfIdfModel model = StringTfidf.buildModel(Arrays.asList(s1, s2));
        double simSym = StringTfidf.cosineSimilarity(model, s1, s2);
        assertTrue("similarity should be high but <= 1 due to duplicate 'beta'", simSym > 0.8 && simSym < 1.0000001);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST StringTfidfTest END
