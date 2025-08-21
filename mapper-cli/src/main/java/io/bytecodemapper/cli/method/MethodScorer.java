// >>> AUTOGEN: BYTECODEMAPPER CLI MethodScorer BEGIN
package io.bytecodemapper.cli.method;

import io.bytecodemapper.signals.calls.CallBagTfidf;
import io.bytecodemapper.signals.strings.StringTfidf;
import io.bytecodemapper.signals.tfidf.TfIdfModel;
import io.bytecodemapper.signals.micro.MicroScoringService;
import io.bytecodemapper.signals.idf.IdfStore;

import java.util.*;

public final class MethodScorer {

    public static double W_CALLS = 0.45;
    public static double W_MICRO = 0.25;
    public static double W_OPCODE= 0.15; // legacy/raw histogram path only when enabled
    public static double W_STR   = 0.10;
    public static double W_FIELDS= 0.05; // stubbed contribution
    // New: conservative weights for stack histogram cosine and numeric-literal MinHash similarity
    public static double W_STACK = 0.10;   // stack histogram cosine (fixed-5 bins)
    public static double W_LITS  = 0.08;   // numeric-literal MinHash similarity (64)

    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodScorer NORM WEIGHTS BEGIN
    // New: normalized histogram weight (generalized)
    public static double W_NORM = 0.10;

    // Legacy opcode histogram remains available but OFF by default
    public static boolean LEGACY_OPCODE_ENABLED = false;
    public static double W_OPCODE_LEGACY = 0.05;
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodScorer NORM WEIGHTS END

    public static double ALPHA_MP   = 0.60; // blend inside micropatterns
    public static double TAU_ACCEPT = 0.60; // final acceptance threshold (configurable)
    public static double MIN_MARGIN = 0.05; // abstain if best - secondBest < MIN_MARGIN (configurable)

    // penalties (subtract)
    public static final double PEN_LEAF_MISMATCH = 0.05;
    public static final double PEN_RECUR_MISMATCH= 0.03;

    /**
     * Compute S_total per candidate, aligned with cands order. Uses the same
     * models and penalties as {@link #scoreOne} but does not apply acceptance
     * logic. Deterministic given inputs.
     */
    public static double[] scoreVector(MethodFeatures src, List<MethodFeatures> cands, IdfStore microIdf) {
        if (cands.isEmpty()) return new double[0];

        // Build TF-IDF models per source set for determinism
        // calls
        List<List<String>> callDocs = new ArrayList<List<String>>(cands.size()+1);
        callDocs.add(src.callBagNormalized);
        for (MethodFeatures m : cands) callDocs.add(m.callBagNormalized);
        TfIdfModel callsModel = CallBagTfidf.buildModel(callDocs);

        // strings
        List<List<String>> strDocs = new ArrayList<List<String>>(cands.size()+1);
        strDocs.add(src.stringBag);
        for (MethodFeatures m : cands) strDocs.add(m.stringBag);
        TfIdfModel strModel = StringTfidf.buildModel(strDocs);

        // micro
        MicroScoringService microSvc = new MicroScoringService().setIdf(microIdf.computeIdf());

        double[] out = new double[cands.size()];
        for (int i=0;i<cands.size();i++) {
            MethodFeatures t = cands.get(i);
            double sCalls = CallBagTfidf.cosineSimilarity(callsModel, src.callBagNormalized, t.callBagNormalized);
            double sMicro = microSvc.similarity(src.microBits, t.microBits, ALPHA_MP);
            // >>> AUTOGEN: BYTECODEMAPPER CLI MethodScorer NORM COMPOSITION BEGIN
            double sNorm = io.bytecodemapper.signals.normalized.NormalizedAdapters.cosineDense(src.normOpcodeHistogram, t.normOpcodeHistogram);
            double sOpcLegacy = 0.0;
            if (LEGACY_OPCODE_ENABLED) {
                sOpcLegacy = io.bytecodemapper.signals.opcode.OpcodeFeatures.cosineHistogram(src.opcodeHistogram, t.opcodeHistogram);
            }
            double sStr   = StringTfidf.cosineSimilarity(strModel, src.stringBag, t.stringBag);
            double sFields= 0.0; // stub
            // New terms: stack histogram cosine (fixed 5 keys) and numeric-literal MinHash similarity (64)
            java.util.Map<String, Integer> shA = safeStackHist(src);
            java.util.Map<String, Integer> shB = safeStackHist(t);
            double sStack = io.bytecodemapper.signals.normalized.NormalizedAdapters.cosineStackFixed5(shA, shB);
            int[] litsA = safeLits64(src);
            int[] litsB = safeLits64(t);
            double sLits = io.bytecodemapper.signals.normalized.NormalizedAdapters.minhashSimilarity64(litsA, litsB);

        double s = W_CALLS*sCalls
            + W_MICRO*sMicro
            + W_NORM*sNorm
            + (LEGACY_OPCODE_ENABLED ? (W_OPCODE_LEGACY*sOpcLegacy) : 0.0)
            + W_STR*sStr
            + W_FIELDS*sFields
            + W_STACK*sStack
            + W_LITS*sLits;
        // <<< AUTOGEN: BYTECODEMAPPER CLI MethodScorer NORM COMPOSITION END
            if (src.leaf != t.leaf) s -= PEN_LEAF_MISMATCH;
            if (src.recursive != t.recursive) s -= PEN_RECUR_MISMATCH;

            // clip to [0,1] for downstream stability
            if (s < 0) s = 0; else if (s > 1) s = 1;
            out[i] = s;
        }
        return out;
    }

    /** Score source method against candidate targets from the same class pair. */
    public static Result scoreOne(MethodFeatures src, List<MethodFeatures> cands, IdfStore microIdf) {
        if (cands.isEmpty()) return Result.abstain("no candidates");

        // Build TF-IDF models per source set for determinism
        // calls
        List<List<String>> callDocs = new ArrayList<List<String>>(cands.size()+1);
        callDocs.add(src.callBagNormalized);
        for (MethodFeatures m : cands) callDocs.add(m.callBagNormalized);
        TfIdfModel callsModel = CallBagTfidf.buildModel(callDocs);

        // strings
        List<List<String>> strDocs = new ArrayList<List<String>>(cands.size()+1);
        strDocs.add(src.stringBag);
        for (MethodFeatures m : cands) strDocs.add(m.stringBag);
        TfIdfModel strModel = StringTfidf.buildModel(strDocs);

        // micro
        MicroScoringService microSvc = new MicroScoringService().setIdf(microIdf.computeIdf());

        // score each
        double best = -1, second = -1; MethodFeatures bestM = null; double bestCalls=0,bestMicro=0,bestOpc=0,bestStr=0;
        for (MethodFeatures t : cands) {
            double sCalls = CallBagTfidf.cosineSimilarity(callsModel, src.callBagNormalized, t.callBagNormalized);
            double sMicro = microSvc.similarity(src.microBits, t.microBits, ALPHA_MP);
            double sNorm = io.bytecodemapper.signals.normalized.NormalizedAdapters.cosineDense(src.normOpcodeHistogram, t.normOpcodeHistogram);
            double sOpcLegacy = 0.0;
            if (LEGACY_OPCODE_ENABLED) {
                sOpcLegacy = io.bytecodemapper.signals.opcode.OpcodeFeatures.cosineHistogram(src.opcodeHistogram, t.opcodeHistogram);
            }
            double sStr   = StringTfidf.cosineSimilarity(strModel, src.stringBag, t.stringBag);
            double sFields= 0.0; // stub
        // New terms
        java.util.Map<String, Integer> shA = safeStackHist(src);
        java.util.Map<String, Integer> shB = safeStackHist(t);
        double sStack = io.bytecodemapper.signals.normalized.NormalizedAdapters.cosineStackFixed5(shA, shB);
        int[] litsA = safeLits64(src);
        int[] litsB = safeLits64(t);
        double sLits = io.bytecodemapper.signals.normalized.NormalizedAdapters.minhashSimilarity64(litsA, litsB);

            double s = W_CALLS*sCalls
                    + W_MICRO*sMicro
                    + W_NORM*sNorm
                    + (LEGACY_OPCODE_ENABLED ? (W_OPCODE_LEGACY*sOpcLegacy) : 0.0)
                    + W_STR*sStr
            + W_FIELDS*sFields
            + W_STACK*sStack
            + W_LITS*sLits;

            // smart filters: penalties
            if (src.leaf != t.leaf) s -= PEN_LEAF_MISMATCH;
            if (src.recursive != t.recursive) s -= PEN_RECUR_MISMATCH;

            if (s > best) {
                second = best;
                best = s; bestM = t;
                bestCalls=sCalls; bestMicro=sMicro; bestOpc=(LEGACY_OPCODE_ENABLED? sOpcLegacy : sNorm); bestStr=sStr;
            } else if (s > second) {
                second = s;
            }
        }

        if (bestM == null) return Result.abstain("no best");

        String reason = null;
        if (best - Math.max(0, second) < MIN_MARGIN) reason = "low_margin";
        if (best < TAU_ACCEPT) reason = (reason==null ? "below_tau" : reason + "+below_tau");

        if (reason != null) {
            return Result.abstain(reason).withTop(bestM, best, second);
        }

        return Result.accept(bestM, best, second, bestCalls, bestMicro, bestOpc, bestStr);
    }

    public static final class Result {
        public final boolean accepted;
        public final MethodFeatures best;  // may be null if abstain
        public final double scoreBest;
        public final double scoreSecond;
        public final String abstainReason;
        public final double sCalls, sMicro, sOpcode, sStrings;

        private Result(boolean acc, MethodFeatures best, double sb, double ss, String why,
                       double sc, double sm, double so, double ss2) {
            this.accepted = acc; this.best=best; this.scoreBest=sb; this.scoreSecond=ss; this.abstainReason=why;
            this.sCalls=sc; this.sMicro=sm; this.sOpcode=so; this.sStrings=ss2;
        }

        public static Result accept(MethodFeatures best, double sb, double ss, double sC, double sM, double sO, double sS) {
            return new Result(true, best, sb, ss, null, sC, sM, sO, sS);
        }
        public static Result abstain(String why) {
            return new Result(false, null, 0, 0, why, 0,0,0,0);
        }
        public Result withTop(MethodFeatures top, double sb, double ss) {
            return new Result(false, top, sb, ss, this.abstainReason, 0,0,0,0);
        }
    }

    private MethodScorer(){}

    // Optional small refactor: null-safe fetchers to keep loops compact and resilient
    // Test-only attachment registry to allow unit tests to inject normalized features without public API changes.
    private static final java.util.WeakHashMap<MethodFeatures, Object> __TEST_NF = new java.util.WeakHashMap<MethodFeatures, Object>();
    static void __testAttachNormalized(MethodFeatures m, java.util.Map<String,Integer> sh, int[] lits) {
        class NF { final java.util.Map<String,Integer> stack; final int[] sk; NF(java.util.Map<String,Integer> s, int[] k){ this.stack=s; this.sk=k; } }
        Object o = new NF(sh, lits);
        // dummy self-read to silence certain compilers about unused fields
        try { o.getClass().getDeclaredField("stack"); o.getClass().getDeclaredField("sk"); } catch (Throwable ignore) {}
        __TEST_NF.put(m, o);
    }

    private static java.util.Map<String, Integer> safeStackHist(MethodFeatures m) {
        if (m == null) return null;
        // Try reflective access to a nested normalized features object with getStackHist()
        try {
            // Common candidates: features, normalizedFeatures
            Object nf = null;
            try {
                java.lang.reflect.Field f = m.getClass().getDeclaredField("features");
                f.setAccessible(true);
                nf = f.get(m);
            } catch (Throwable ignore) { nf = null; }
            if (nf == null) {
                try {
                    java.lang.reflect.Method gm = m.getClass().getMethod("getNormalizedFeatures");
                    nf = gm.invoke(m);
                } catch (Throwable ignore) { nf = null; }
            }
            if (nf != null) {
                try {
                    java.lang.reflect.Method getter = nf.getClass().getMethod("getStackHist");
                    Object res = getter.invoke(nf);
                    if (res instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String,Integer> cast = (java.util.Map<String,Integer>) res;
                        return cast;
                    }
                } catch (Throwable ignore) { /* fallthrough */ }
            }
        } catch (Throwable ignoreOuter) { /* fallthrough to null */ }
        // As a last resort, consult test-only attached features
        try {
            Object o = __TEST_NF.get(m);
            if (o != null) {
                java.lang.reflect.Field f = o.getClass().getDeclaredField("stack");
                f.setAccessible(true);
                Object v = f.get(o);
                if (v instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String,Integer> cast = (java.util.Map<String,Integer>) v;
                    return cast;
                }
            }
        } catch (Throwable ignore) { }
        return null;
    }

    private static int[] safeLits64(MethodFeatures m) {
        if (m == null) return null;
        try {
            Object nf = null;
            try {
                java.lang.reflect.Field f = m.getClass().getDeclaredField("features");
                f.setAccessible(true);
                nf = f.get(m);
            } catch (Throwable ignore) { nf = null; }
            if (nf == null) {
                try {
                    java.lang.reflect.Method gm = m.getClass().getMethod("getNormalizedFeatures");
                    nf = gm.invoke(m);
                } catch (Throwable ignore) { nf = null; }
            }
            if (nf != null) {
                try {
                    java.lang.reflect.Method getter = nf.getClass().getMethod("getLitsMinHash64");
                    Object res = getter.invoke(nf);
                    if (res instanceof int[]) {
                        return (int[]) res;
                    }
                } catch (Throwable ignore) { /* fallthrough */ }
            }
        } catch (Throwable ignoreOuter) { /* fallthrough to null */ }
        // As a last resort, consult test-only attached features
        try {
            Object o = __TEST_NF.get(m);
            if (o != null) {
                java.lang.reflect.Field f = o.getClass().getDeclaredField("sk");
                f.setAccessible(true);
                Object v = f.get(o);
                if (v instanceof int[]) return (int[]) v;
            }
        } catch (Throwable ignore) { }
        return null;
    }

    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodScorer CONFIG BEGIN
    /** Configure weights/toggles from orchestrator options or callers. */
    public static void configureWeights(double wCalls, double wMicro, double wNorm, double wStr, double wFields, boolean useNorm) {
        W_CALLS = wCalls;
        W_MICRO = wMicro;
        W_STR   = wStr;
        W_FIELDS= wFields;
        if (useNorm) {
            W_NORM = wNorm;
        } else {
            W_NORM = 0.0;
        }
    }

    /** Configure micropattern alpha in [0,1]. */
    public static void setAlphaMicropattern(double v) {
        if (v < 0) v = 0; else if (v > 1) v = 1;
        ALPHA_MP = v;
    }
    /** Configure acceptance threshold in [0,1]. */
    public static void setTauAccept(double v) {
        if (v < 0) v = 0; else if (v > 1) v = 1;
        TAU_ACCEPT = v;
    }
    /** Configure minimum margin in [0,1]. */
    public static void setMinMargin(double v) {
        if (v < 0) v = 0; else if (v > 1) v = 1;
        MIN_MARGIN = v;
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodScorer CONFIG END
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodScorer END
