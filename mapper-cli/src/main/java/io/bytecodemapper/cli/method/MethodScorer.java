// >>> AUTOGEN: BYTECODEMAPPER CLI MethodScorer BEGIN
package io.bytecodemapper.cli.method;

import io.bytecodemapper.signals.calls.CallBagTfidf;
import io.bytecodemapper.signals.opcode.OpcodeFeatures;
import io.bytecodemapper.signals.strings.StringTfidf;
import io.bytecodemapper.signals.tfidf.TfIdfModel;
import io.bytecodemapper.signals.micro.MicroScoringService;
import io.bytecodemapper.signals.idf.IdfStore;

import java.util.*;

public final class MethodScorer {

    public static final double W_CALLS = 0.45;
    public static final double W_MICRO = 0.25;
    public static final double W_OPCODE= 0.15;
    public static final double W_STR   = 0.10;
    public static final double W_FIELDS= 0.05; // stubbed 0.0 for now

    public static final double ALPHA_MP   = 0.60; // blend inside micropatterns
    public static final double TAU_ACCEPT = 0.60; // final acceptance threshold
    public static final double MIN_MARGIN = 0.05; // abstain if best - secondBest < MIN_MARGIN

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
            double sOpc   = OpcodeFeatures.cosineHistogram(src.opcodeHistogram, t.opcodeHistogram);
            double sStr   = StringTfidf.cosineSimilarity(strModel, src.stringBag, t.stringBag);
            double sFields= 0.0; // stub

            double s = W_CALLS*sCalls + W_MICRO*sMicro + W_OPCODE*sOpc + W_STR*sStr + W_FIELDS*sFields;
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
            double sOpc   = OpcodeFeatures.cosineHistogram(src.opcodeHistogram, t.opcodeHistogram);
            double sStr   = StringTfidf.cosineSimilarity(strModel, src.stringBag, t.stringBag);
            double sFields= 0.0; // stub

            double s = W_CALLS*sCalls + W_MICRO*sMicro + W_OPCODE*sOpc + W_STR*sStr + W_FIELDS*sFields;

            // smart filters: penalties
            if (src.leaf != t.leaf) s -= PEN_LEAF_MISMATCH;
            if (src.recursive != t.recursive) s -= PEN_RECUR_MISMATCH;

            if (s > best) {
                second = best;
                best = s; bestM = t;
                bestCalls=sCalls; bestMicro=sMicro; bestOpc=sOpc; bestStr=sStr;
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
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodScorer END
