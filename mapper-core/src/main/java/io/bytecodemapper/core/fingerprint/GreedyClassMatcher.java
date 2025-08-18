// >>> AUTOGEN: BYTECODEMAPPER core GreedyClassMatcher BEGIN
package io.bytecodemapper.core.fingerprint;

import java.util.*;

/** Deterministic greedy matcher for classes with threshold and margin. */
public final class GreedyClassMatcher {

    public static final double TAU_CLASS = 0.55; // acceptance threshold
    public static final double MIN_MARGIN = 0.02;

    public static List<Pair> match(List<ClassFingerprint> oldC, List<ClassFingerprint> newC) {
        // Precompute all scores
        ArrayList<Edge> edges = new ArrayList<Edge>(oldC.size() * Math.max(1, newC.size()));
        for (int i=0;i<oldC.size();i++) {
            ClassFingerprint A = oldC.get(i);
            for (int j=0;j<newC.size();j++) {
                ClassFingerprint B = newC.get(j);
                double s = ClassScoring.score(A, B);
                edges.add(new Edge(A.internalName(), B.internalName(), s));
            }
        }
        // Sort edges by score desc, then name tie-breaker for determinism
        Collections.sort(edges, new Comparator<Edge>() {
            public int compare(Edge a, Edge b) {
                int c = Double.compare(b.score, a.score);
                if (c != 0) return c;
                return (a.oldName + "#" + a.newName).compareTo(b.oldName + "#" + b.newName);
            }
        });
        // Greedy selection with threshold and "not-yet-matched" sets
        java.util.HashSet<String> usedOld = new java.util.HashSet<String>();
        java.util.HashSet<String> usedNew = new java.util.HashSet<String>();
        ArrayList<Pair> out = new ArrayList<Pair>();

        for (Edge e : edges) {
            if (e.score < TAU_CLASS) break; // remaining are worse due to sorting
            if (usedOld.contains(e.oldName) || usedNew.contains(e.newName)) continue;
            // optional margin check: compute next-best for oldName vs any unused new
            double second = secondBestFor(e.oldName, e.newName, edges, usedNew);
            if (e.score - second < MIN_MARGIN) continue; // abstain if ambiguous
            usedOld.add(e.oldName);
            usedNew.add(e.newName);
            out.add(new Pair(e.oldName, e.newName, e.score));
        }
        // Deterministic order by oldName in output
        Collections.sort(out, new Comparator<Pair>() {
            public int compare(Pair a, Pair b) { return a.oldName.compareTo(b.oldName); }
        });
        return out;
    }

    private static double secondBestFor(String oldName, String chosenNew,
                                        List<Edge> edges,
                                        java.util.Set<String> usedNew) {
        double best = -1.0;
        for (Edge e : edges) {
            if (!e.oldName.equals(oldName)) continue;
            if (e.newName.equals(chosenNew)) continue;
            if (usedNew.contains(e.newName)) continue;
            best = Math.max(best, e.score);
        }
        return best < 0 ? 0.0 : best;
    }

    public static final class Pair {
        public final String oldName;
        public final String newName;
        public final double score;
        public Pair(String oldName, String newName, double score) {
            this.oldName = oldName; this.newName = newName; this.score = score;
        }
        @Override public String toString() { return oldName + " -> " + newName + " score=" + String.format(java.util.Locale.ROOT, "%.4f", score); }
    }

    private static final class Edge {
        final String oldName, newName; final double score;
        Edge(String o, String n, double s) { oldName=o; newName=n; score=s; }
    }

    private GreedyClassMatcher(){}
}
// <<< AUTOGEN: BYTECODEMAPPER core GreedyClassMatcher END
