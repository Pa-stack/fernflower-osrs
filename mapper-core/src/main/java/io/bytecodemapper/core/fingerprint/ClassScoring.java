// >>> AUTOGEN: BYTECODEMAPPER core ClassScoring BEGIN
package io.bytecodemapper.core.fingerprint;

import java.util.*;

/** Similarity for class fingerprints; deterministic and Java-8 friendly. */
public final class ClassScoring {

    public static final double W_WL    = 0.70;
    public static final double W_MICRO = 0.15;
    public static final double W_COUNTS= 0.10;
    public static final double W_TYPES = 0.05;

    /** Cosine similarity between WL signature multisets (using union of keys). */
    public static double wlCosine(MethodSigBag A, MethodSigBag B) {
        long[] keysA = A.allKeysSorted();
        long[] keysB = B.allKeysSorted();
        long[] merged = mergeSorted(keysA, keysB);
        if (merged.length == 0) return 0.0;

        double dot = 0.0, na2 = 0.0, nb2 = 0.0;
        for (long k : merged) {
            int av = A.get(k), bv = B.get(k);
            dot += av * (double) bv;
            na2 += av * (double) av;
            nb2 += bv * (double) bv;
        }
        if (na2 == 0.0 || nb2 == 0.0) return 0.0;
        return dot / (Math.sqrt(na2) * Math.sqrt(nb2));
    }

    /** Cosine for micropattern class histograms (length 17). */
    public static double microCosine(int[] a, int[] b) {
        if (a == null || b == null) return 0.0;
        int n = Math.min(a.length, b.length);
        double dot=0, na2=0, nb2=0;
        for (int i=0;i<n;i++) {
            dot += a[i] * (double) b[i];
            na2 += a[i] * (double) a[i];
            nb2 += b[i] * (double) b[i];
        }
        if (na2 == 0.0 || nb2 == 0.0) return 0.0;
        return dot / (Math.sqrt(na2) * Math.sqrt(nb2));
    }

    /** Count similarity from method/field counts (1 / (1 + |Δ|/max(1,avg))). */
    public static double countSim(int ma, int mb, int fa, int fb) {
        double ms = oneMinusNorm(ma, mb);
        double fs = oneMinusNorm(fa, fb);
        return 0.6 * ms + 0.4 * fs;
    }

    /** Super & interfaces overlap (Jaccard), ignoring java/lang/Object baseline. */
    public static double typeOverlap(String superA, String[] ifA, String superB, String[] ifB) {
        java.util.LinkedHashSet<String> SA = new java.util.LinkedHashSet<String>();
        java.util.LinkedHashSet<String> SB = new java.util.LinkedHashSet<String>();
        if (superA != null && !"java/lang/Object".equals(superA)) SA.add(superA);
        if (superB != null && !"java/lang/Object".equals(superB)) SB.add(superB);
        if (ifA != null) for (String s : ifA) SA.add(s);
        if (ifB != null) for (String s : ifB) SB.add(s);
        if (SA.isEmpty() && SB.isEmpty()) return 0.0;

        int inter = 0;
        for (String s : SA) if (SB.contains(s)) inter++;
        int union = SA.size();
        for (String s : SB) if (!SA.contains(s)) union++;
        return union == 0 ? 0.0 : (inter * 1.0) / union;
    }

    /** Composite score with fixed weights. */
    public static double score(ClassFingerprint A, ClassFingerprint B) {
        double sWl    = wlCosine(A.methodSigs(), B.methodSigs());
        double sMicro = microCosine(A.microHistogram(), B.microHistogram());
        double sCnt   = countSim(A.methodCount(), B.methodCount(), A.fieldCount(), B.fieldCount());
        double sTyp   = typeOverlap(A.superName(), A.interfaces(), B.superName(), B.interfaces());
        return W_WL*sWl + W_MICRO*sMicro + W_COUNTS*sCnt + W_TYPES*sTyp;
    }

    // ---- helpers
    private static double oneMinusNorm(int a, int b) {
        double avg = Math.max(1.0, (a + b) / 2.0);
        double diff = Math.abs(a - b) / avg;
        return 1.0 / (1.0 + diff);
    }

    private static long[] mergeSorted(long[] a, long[] b) {
        int i=0,j=0,k=0;
        long[] out = new long[a.length + b.length];
        long last = Long.MIN_VALUE; boolean have = false;
        while (i < a.length || j < b.length) {
            long v;
            if (j >= b.length || (i < a.length && a[i] <= b[j])) v = a[i++]; else v = b[j++];
            if (!have || v != last) { out[k++] = v; last = v; have = true; }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    private ClassScoring(){}
}
// <<< AUTOGEN: BYTECODEMAPPER core ClassScoring END
