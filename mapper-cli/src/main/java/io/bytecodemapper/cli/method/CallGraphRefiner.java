// >>> AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner BEGIN
package io.bytecodemapper.cli.method;

import java.util.*;

public final class CallGraphRefiner {

    public static final double DEFAULT_LAMBDA = 0.70;
    public static final int    DEFAULT_MAX_ITERS = 5;
    public static final double EPS = 1e-4;

    // >>> AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner LAMBDA CLAMP BEGIN
    public static final double LAMBDA_MIN = 0.60;
    public static final double LAMBDA_MAX = 0.80;
    // <<< AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner LAMBDA CLAMP END

    // caps relative to S0
    public static final double CAP_DOWN = 0.05; // max decrease
    public static final double CAP_UP   = 0.10; // max increase

    public static final double STRONG_FREEZE_TAU = 0.80;
    public static final double STRONG_FREEZE_MARGIN = 0.05;

    /** Per-source candidate pack used by the refiner. */
    public static final class CandidateSet {
        public final java.util.List<MethodFeatures> targets; // deterministic order
        public final double[] baseScores;                    // aligned with targets
        public CandidateSet(java.util.List<MethodFeatures> targets, double[] baseScores) {
            this.targets = targets; this.baseScores = baseScores;
        }
    }

    public static final class Result {
        public final Map<MethodRef, MethodRef> mapping;   // u -> v*
        public final Map<MethodRef, Double> bestScore;    // u -> refined S(u,v*)
        public final Stats stats;
        Result(Map<MethodRef, MethodRef> m, Map<MethodRef, Double> s, Stats st) { this.mapping=m; this.bestScore=s; this.stats=st; }
    }

    /** Iteration statistics for oscillation tracking. */
    public static final class Stats {
        public final int iters;
        public final int[] flipsPerIter;
        public final double[] maxDeltaPerIter;
        Stats(int iters, int[] flips, double[] deltas) { this.iters=iters; this.flipsPerIter=flips; this.maxDeltaPerIter=deltas; }
    }

    /** One class-pair refinement. */
    public static Result refine(
            Map<MethodRef, CandidateSet> candidates,             // per src u
            Map<MethodRef, Set<MethodRef>> adjOldIntra,          // old-class intra graph
            Map<MethodRef, Set<MethodRef>> adjNewIntra,          // new-class intra graph
            double lambda, int maxIters) {

        // >>> AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner LAMBDA CLAMP BEGIN
        // normalize parameters
        if (maxIters <= 0) maxIters = DEFAULT_MAX_ITERS;
        if (lambda < LAMBDA_MIN) lambda = LAMBDA_MIN;
        if (lambda > LAMBDA_MAX) lambda = LAMBDA_MAX;
        // <<< AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner LAMBDA CLAMP END

        Map<MethodRef, double[]> scores = new LinkedHashMap<MethodRef, double[]>();
        Map<MethodRef, Integer>  bestIdx = new LinkedHashMap<MethodRef, Integer>();
        Map<MethodRef, Boolean>  frozen  = new LinkedHashMap<MethodRef, Boolean>();

        for (Map.Entry<MethodRef, CandidateSet> e : candidates.entrySet()) {
            MethodRef u = e.getKey();
            CandidateSet cs = e.getValue();
            double[] s = new double[cs.baseScores.length];
            double best = -1.0, second = -1.0; int bi = -1;
            for (int i=0;i<s.length;i++) {
                s[i] = clip01(cs.baseScores[i]);
                if (s[i] > best) { second = best; best = s[i]; bi = i; }
                else if (s[i] > second) { second = s[i]; }
            }
            scores.put(u, s);
            bestIdx.put(u, bi);
            boolean isFrozen = best >= STRONG_FREEZE_TAU && (best - Math.max(0, second)) >= STRONG_FREEZE_MARGIN;
            frozen.put(u, isFrozen);
        }

        int[] flipsArr = new int[maxIters];
        double[] deltasArr = new double[maxIters];

        int iter = 0;
        int lastFlips = Integer.MAX_VALUE;
        for (; iter < maxIters; iter++) {
            int flips = 0;
            double maxDelta = 0.0;

            for (Map.Entry<MethodRef, CandidateSet> e : candidates.entrySet()) {
                MethodRef u = e.getKey();
                CandidateSet cs = e.getValue();
                double[] s = scores.get(u);

                Set<MethodRef> neighU = adjOldIntra.get(u);
                int degU = (neighU == null) ? 0 : neighU.size();

                for (int i=0;i<cs.targets.size();i++) {
                    MethodRef v = cs.targets.get(i).ref;

                    double nScore = 0.0;
                    // >>> AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner GUARD NEIGHBORS BEGIN
                    if (degU > 0) {
                        int agree = 0;
                        Set<MethodRef> neighV = adjNewIntra.get(v);
                        if (neighV != null && !neighV.isEmpty()) {
                            for (MethodRef uN : neighU) {
                                Integer idxObj = bestIdx.get(uN);
                                if (idxObj == null) continue;
                                int idx = idxObj.intValue();
                                CallGraphRefiner.CandidateSet csN = candidates.get(uN);
                                if (csN == null) continue;                // neighbor not in candidate sets
                                if (idx < 0 || idx >= csN.targets.size()) continue; // stale index
                                MethodRef vN = csN.targets.get(idx).ref;
                                if (neighV.contains(vN)) agree++;
                            }
                        }
                        nScore = agree / (double) degU;
                    }
                    // <<< AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner GUARD NEIGHBORS END

                    double sOld = s[i];
                    double s0   = cs.baseScores[i];
                    double sNew = (1.0 - lambda) * sOld + lambda * nScore;

                    // cap relative to S0
                    double minCap = s0 - CAP_DOWN;
                    double maxCap = s0 + CAP_UP;
                    if (sNew < minCap) sNew = minCap;
                    if (sNew > maxCap) sNew = maxCap;

                    sNew = clip01(sNew);
                    double delta = Math.abs(sNew - sOld);
                    if (delta > maxDelta) maxDelta = delta;

                    s[i] = sNew;
                }

                if (!frozen.get(u).booleanValue()) {
                    int oldBest = bestIdx.get(u).intValue();
                    int newBest = argmax(scores.get(u));
                    if (newBest != oldBest) flips++;
                    bestIdx.put(u, Integer.valueOf(newBest));
                }
            }

            flipsArr[iter] = flips;
            deltasArr[iter] = maxDelta;

            System.out.println(String.format(java.util.Locale.ROOT,
                    "Refine iter=%d flips=%d maxDelta=%.5f (prev flips=%s)",
                    (iter+1), flips, maxDelta,
                    (iter==0 ? "-" : String.valueOf(flipsArr[iter-1]))));

            if (flips > 0 && flips >= lastFlips) break; // oscillation not improving
            if (maxDelta < EPS) break;
            lastFlips = flips;
        }

        Map<MethodRef, MethodRef> finalMap = new LinkedHashMap<MethodRef, MethodRef>();
        Map<MethodRef, Double>    finalScore = new LinkedHashMap<MethodRef, Double>();
        for (Map.Entry<MethodRef, CandidateSet> e : candidates.entrySet()) {
            MethodRef u = e.getKey();
            int bi = bestIdx.get(u).intValue();
            if (bi >= 0) {
                finalMap.put(u, e.getValue().targets.get(bi).ref);
                finalScore.put(u, Double.valueOf(scores.get(u)[bi]));
            }
        }
    // number of iterations recorded: if loop exhausted, iter==maxIters; else last index is 'iter' inclusive
    int count = (iter >= maxIters) ? maxIters : (iter + 1);
    return new Result(finalMap, finalScore, new Stats(count, trim(flipsArr, count), trim(deltasArr, count)));
    }

    private static int argmax(double[] s) {
        int bi = -1; double best = -1.0;
        for (int i=0;i<s.length;i++) if (s[i] > best) { best = s[i]; bi = i; }
        return bi;
    }
    private static double clip01(double x){ return x<0?0:(x>1?1:x); }
    private static int[] trim(int[] a, int n){ int[] r=new int[n]; System.arraycopy(a,0,r,0,n); return r; }
    private static double[] trim(double[] a, int n){ double[] r=new double[n]; System.arraycopy(a,0,r,0,n); return r; }

    private CallGraphRefiner(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI CallGraphRefiner END
