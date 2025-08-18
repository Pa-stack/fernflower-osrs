// >>> AUTOGEN: BYTECODEMAPPER OpcodeFeatures BEGIN
package io.bytecodemapper.signals.opcode;

import it.unimi.dsi.fastutil.ints.*;
import io.bytecodemapper.signals.common.Cosine;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public final class OpcodeFeatures {
    private OpcodeFeatures(){}

    /** Returns a histogram over opcodes [0..199] counting only real instructions (opcode >= 0). */
    public static int[] opcodeHistogram(MethodNode mn) {
        int[] hist = new int[200];
        for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
            int op = p.getOpcode();
            if (op >= 0 && op < hist.length) hist[op]++;
        }
        return hist;
    }

    /** Cosine similarity over opcode histograms (uses double conversion). */
    public static double cosineHistogram(int[] a, int[] b) {
        int n = Math.max(a.length, b.length);
        double[] da = new double[n];
        double[] db = new double[n];
        for (int i=0;i<a.length;i++) da[i] = a[i];
        for (int i=0;i<b.length;i++) db[i] = b[i];
        return Cosine.cosine(da, db);
    }

    /** n-gram of opcodes (n=2 or n=3). Uses a compact key: (op1<<16) ^ (op2<<8) ^ op3. */
    public static Int2IntOpenHashMap opcodeNGram(MethodNode mn, int n) {
        if (n < 2 || n > 3) throw new IllegalArgumentException("n must be 2 or 3");
        List<Integer> ops = new ArrayList<Integer>();
        for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
            int op = p.getOpcode();
            if (op >= 0) ops.add(op);
        }
        Int2IntOpenHashMap map = new Int2IntOpenHashMap();
        if (ops.size() < n) return map;
        if (n == 2) {
            for (int i=0;i+1<ops.size();i++) {
                int key = (ops.get(i) << 8) ^ (ops.get(i+1) & 0xFF);
                map.addTo(key, 1);
            }
        } else { // n == 3
            for (int i=0;i+2<ops.size();i++) {
                int key = (ops.get(i) << 16) ^ ((ops.get(i+1) & 0xFF) << 8) ^ (ops.get(i+2) & 0xFF);
                map.addTo(key, 1);
            }
        }
        return map;
    }

    /** Cosine similarity over sparse n-gram frequency maps. */
    public static double cosineNGram(Int2IntOpenHashMap A, Int2IntOpenHashMap B) {
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        double dot = 0.0, na2 = 0.0, nb2 = 0.0;
        // dot over intersection
        IntIterator it = A.keySet().iterator();
        while (it.hasNext()) {
            int k = it.nextInt();
            int av = A.get(k);
            int bv = B.get(k);
            if (bv != 0) dot += av * (double) bv;
            na2 += av * (double) av;
        }
        IntIterator itB = B.values().iterator();
        while (itB.hasNext()) {
            int bv = itB.nextInt();
            nb2 += bv * (double) bv;
        }
        if (na2 == 0.0 || nb2 == 0.0) return 0.0;
        return dot / (Math.sqrt(na2) * Math.sqrt(nb2));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER OpcodeFeatures END
