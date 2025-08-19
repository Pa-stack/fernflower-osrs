// >>> AUTOGEN: BYTECODEMAPPER signals NormalizedAdapters BEGIN
package io.bytecodemapper.signals.normalized;

import java.util.Map;

public final class NormalizedAdapters {

    /** Convert sparse opcode histogram to dense array [0..199] (covers all ASM opcodes in practice). */
    public static int[] toDense200(Map<Integer,Integer> sparse) {
        int[] a = new int[200];
        for (Map.Entry<Integer,Integer> e : sparse.entrySet()) {
            int k = e.getKey().intValue();
            if (k >= 0 && k < a.length) a[k] = e.getValue().intValue();
        }
        return a;
    }

    /** Cosine similarity for dense int histograms. */
    public static double cosineDense(int[] a, int[] b) {
        long dot = 0L, na = 0L, nb = 0L;
        int n = Math.min(a.length, b.length);
        for (int i=0;i<n;i++) {
            int ai=a[i], bi=b[i];
            dot += (long) ai * (long) bi;
            na  += (long) ai * (long) ai;
            nb  += (long) bi * (long) bi;
        }
        if (na==0L || nb==0L) return 0.0;
        return dot / (Math.sqrt((double)na) * Math.sqrt((double)nb));
    }

    private NormalizedAdapters(){}
}
// <<< AUTOGEN: BYTECODEMAPPER signals NormalizedAdapters END
