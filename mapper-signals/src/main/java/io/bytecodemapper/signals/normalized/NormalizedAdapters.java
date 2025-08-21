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

    /**
     * Deterministic cosine over fixed 5-key stack-delta order.
     * Keys are evaluated exactly in order: {"-2","-1","0","+1","+2"}.
     * Missing maps/keys are treated as 0. If either vector is all-zero, returns 0.0.
     */
    public static double cosineStackFixed5(Map<String, Integer> a, Map<String, Integer> b) {
        final String[] order = {"-2", "-1", "0", "+1", "+2"};
        long dot = 0L, na = 0L, nb = 0L;
        for (int i = 0; i < order.length; i++) {
            final String k = order[i];
            final int ai = (a == null) ? 0 : (a.get(k) == null ? 0 : a.get(k).intValue());
            final int bi = (b == null) ? 0 : (b.get(k) == null ? 0 : b.get(k).intValue());
            dot += (long) ai * (long) bi;
            na  += (long) ai * (long) ai;
            nb  += (long) bi * (long) bi;
        }
        if (na == 0L || nb == 0L) return 0.0;
        return dot / (Math.sqrt((double) na) * Math.sqrt((double) nb));
    }

    /**
     * Deterministic 64-bucket minhash similarity.
     * Empty buckets are denoted by Integer.MAX_VALUE. Similarity = matches / denom where
     * denom counts positions where either side is non-empty, and matches counts equal
     * non-empty values. Null arrays yield 0.0. Handles non-64 lengths by treating
     * out-of-bounds positions as empty.
     */
    public static double minhashSimilarity64(int[] a, int[] b) {
        if (a == null || b == null) return 0.0;
        final int MAX = Integer.MAX_VALUE;
        int denom = 0;
        int matches = 0;
        for (int i = 0; i < 64; i++) {
            final int ai = (i < a.length) ? a[i] : MAX;
            final int bi = (i < b.length) ? b[i] : MAX;
            final boolean ae = (ai == MAX);
            final boolean be = (bi == MAX);
            if (!(ae && be)) {
                denom++;
                if (ai == bi && !ae) {
                    matches++;
                }
            }
        }
        return denom == 0 ? 0.0 : (matches * 1.0) / denom;
    }

    private NormalizedAdapters(){}
}
// <<< AUTOGEN: BYTECODEMAPPER signals NormalizedAdapters END
