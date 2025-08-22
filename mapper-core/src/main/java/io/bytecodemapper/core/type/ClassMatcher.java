package io.bytecodemapper.core.type;

import io.bytecodemapper.core.hash.StableHash64;
import java.util.*;
import java.nio.charset.StandardCharsets;

/**
 * Deterministic, anchor-aware greedy 1:1 matcher over TypeEvidenceFingerprint.
 */
public final class ClassMatcher {
  // Thresholds must be literal for acceptance
  public static final double TAU = 0.60;
  public static final double MARGIN = 0.05;

  public static final class Result {
    public final SortedMap<String,String> matches = new TreeMap<String,String>();
    public final SortedMap<String,Double> scores = new TreeMap<String,Double>();
    public byte[] toBytes() {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String,String> e : matches.entrySet()) {
        double s = scores.containsKey(e.getKey()) ? scores.get(e.getKey()) : 0.0;
        sb.append(e.getKey()).append("->").append(e.getValue())
          .append(':').append(String.format(java.util.Locale.ROOT, "%.6f", s)).append('\n');
      }
      return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
  }

  private static final class Cand {
    final String oldC, newC; final double score; final long tie;
    Cand(String o, String n, double s, long t){ oldC=o; newC=n; score=s; tie=t; }
  }

  public Result match(Map<String, TypeEvidenceFingerprint> oldMap,
                      Map<String, TypeEvidenceFingerprint> newMap) {
    SortedMap<String, TypeEvidenceFingerprint> olds = new TreeMap<String, TypeEvidenceFingerprint>(oldMap);
    SortedMap<String, TypeEvidenceFingerprint> news = new TreeMap<String, TypeEvidenceFingerprint>(newMap);
    Result res = new Result();

  // 1) Anchors: similarity == 1.0 → pre-assign deterministically in sorted order (strict equality)
    Set<String> usedNew = new HashSet<String>();
    for (Map.Entry<String, TypeEvidenceFingerprint> eo : olds.entrySet()) {
      String o = eo.getKey();
      TypeEvidenceFingerprint ofp = eo.getValue();
      for (Map.Entry<String, TypeEvidenceFingerprint> en : news.entrySet()) {
        String n = en.getKey();
        if (usedNew.contains(n) || res.matches.containsKey(o)) continue;
        double s = ofp.similarity(en.getValue());
        // Normalize to exact 1.0 for byte-identical fingerprints to satisfy strict anchor equality
        if (java.util.Arrays.equals(ofp.toBytes(), en.getValue().toBytes())) {
          s = 1.0d;
        }
  if (s == 1.0d) {
          res.matches.put(o, n);
          res.scores.put(o, s);
          usedNew.add(n);
        }
      }
    }

    // Remaining
    Set<String> freeOld = new TreeSet<String>(olds.keySet());
    freeOld.removeAll(res.matches.keySet());
    Set<String> freeNew = new HashSet<String>(news.keySet());
    freeNew.removeAll(usedNew);

    // 2) Build eligible candidates per old: top2 and margin/threshold
    List<Cand> eligibles = new ArrayList<Cand>();
    for (String o : freeOld) {
      TypeEvidenceFingerprint ofp = olds.get(o);
      String bestN = null, secondN = null; double bestS = -1, secondS = -1; long bestTie = 0L;
      // Iterate over a sorted view for determinism
      SortedSet<String> candNew = new TreeSet<String>(freeNew);
      for (String n : candNew) {
        double s = ofp.similarity(news.get(n));
        long tie = StableHash64.hash(ofp.toBytes(), news.get(n).toBytes());
        if (s > bestS || (s == bestS && Long.compare(tie, bestTie) < 0)) {
          secondS = bestS; secondN = bestN;
          bestS = s; bestN = n; bestTie = tie;
        } else if (s > secondS) {
          secondS = s; secondN = n;
        }
      }
      if (bestN != null) {
        double margin = (secondN == null) ? bestS : (bestS - secondS);
        if (bestS >= TAU && margin >= MARGIN) {
          eligibles.add(new Cand(o, bestN, bestS, bestTie));
        }
      }
    }

    // 3) Greedy assign by (score desc, tie asc)
    Collections.sort(eligibles, new Comparator<Cand>() {
      public int compare(Cand a, Cand b) {
        int c = Double.compare(b.score, a.score);
        if (c != 0) return c;
        return Long.compare(a.tie, b.tie);
      }
    });
    Set<String> takenOld = new HashSet<String>(res.matches.keySet());
    Set<String> takenNew = new HashSet<String>(usedNew);
    for (Cand c : eligibles) {
      if (takenOld.contains(c.oldC) || takenNew.contains(c.newC)) continue;
      res.matches.put(c.oldC, c.newC);
      res.scores.put(c.oldC, c.score);
      takenOld.add(c.oldC); takenNew.add(c.newC);
    }
    return res;
  }
}
