// >>> AUTOGEN: BYTECODEMAPPER TEST CallBagTfidfTest BEGIN
package io.bytecodemapper.signals.calls;

import io.bytecodemapper.signals.tfidf.TfIdfModel;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

import static org.junit.Assert.*;

public class CallBagTfidfTest implements Opcodes {

    private static MethodNode callsABC() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()V", null, null);
        InsnList in = m.instructions = new InsnList();
        in.add(new MethodInsnNode(INVOKESTATIC, "a/A", "a", "()V", false));
        in.add(new MethodInsnNode(INVOKESTATIC, "b/B", "b", "()V", false));
        in.add(new MethodInsnNode(INVOKESTATIC, "c/C", "c", "()V", false));
        // noisy java.* call should be excluded
        in.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
        in.add(new InsnNode(RETURN));
        return m;
    }

    private static MethodNode callsCBA() {
        MethodNode m = new MethodNode(ACC_PUBLIC|ACC_STATIC, "m", "()V", null, null);
        InsnList in = m.instructions = new InsnList();
        in.add(new MethodInsnNode(INVOKESTATIC, "c/C", "c", "()V", false));
        in.add(new MethodInsnNode(INVOKESTATIC, "b/B", "b", "()V", false));
        in.add(new MethodInsnNode(INVOKESTATIC, "a/A", "a", "()V", false));
        in.add(new InsnNode(RETURN));
        return m;
    }

    @Test
    public void orderInvariant() {
        List<String> bag1 = CallBagExtractor.extract("X/Owner", callsABC());
        List<String> bag2 = CallBagExtractor.extract("X/Owner", callsCBA());

        // Ensure java.* was filtered
        assertEquals(3, bag1.size());

        TfIdfModel model = CallBagTfidf.buildModel(Arrays.asList(bag1, bag2));
        double sim = CallBagTfidf.cosineSimilarity(model, bag1, bag2);
        assertEquals(1.0, sim, 1e-9);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST CallBagTfidfTest END
