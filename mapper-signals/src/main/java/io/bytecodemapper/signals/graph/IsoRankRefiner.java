package io.bytecodemapper.signals.graph;

import java.util.*;

/** IsoRank-style refinement with caps/freeze and deterministic logs. */
public final class IsoRankRefiner {
  private IsoRankRefiner() {}

  public static SortedMap<String, SortedMap<String, Double>> refine(
      CallGraphBuilder.Graph oldG,
      CallGraphBuilder.Graph newG,
      Map<String, Map<String, Double>> S0) {
    final double BETA = 0.7;
    SortedMap<String, SortedMap<String, Double>> cur = copy(S0);
    for (int it = 1; it <= 10; it++) {
      double maxDelta = 0.0;
      SortedMap<String, SortedMap<String, Double>> next = new TreeMap<String, SortedMap<String, Double>>();
      for (String u : new TreeSet<String>(cur.keySet())) {
        SortedMap<String, Double> row = cur.get(u);
        SortedMap<String, Double> nrow = new TreeMap<String, Double>();
        SortedSet<String> ou = oldG.out.containsKey(u) ? oldG.out.get(u) : new TreeSet<String>();
        for (String v : new TreeSet<String>(row.keySet())) {
          SortedSet<String> nv = newG.out.containsKey(v) ? newG.out.get(v) : new TreeSet<String>();
          double s0 = get(S0, u, v, 0.0);
          boolean freeze = s0 >= 0.80; // prevent decrease
          double nbr = neighborConsistency(u, v, ou, nv, cur);
          double raw = (1.0 - BETA) * s0 + BETA * nbr;
          double lo = s0 - 0.05, hi = s0 + 0.10;
          double capped = Math.max(lo, Math.min(hi, raw));
          double prev = row.get(v);
          if (freeze && capped < prev - 1e-12) {
            System.out.println("FREEZE " + u + "," + v);
            capped = prev; // freeze decrease
          }
          nrow.put(v, capped);
          maxDelta = Math.max(maxDelta, Math.abs(capped - prev));
        }
        next.put(u, nrow);
      }
      System.out.println(String.format(java.util.Locale.ROOT, "REFINE_ITER=%d delta=%.6f", it, maxDelta));
      cur = next;
      if (maxDelta < 1e-3) break;
    }
    return cur;
  }

  private static double neighborConsistency(String u, String v,
      SortedSet<String> ou, SortedSet<String> nv,
      SortedMap<String, SortedMap<String, Double>> S) {
    if (ou.isEmpty() || nv.isEmpty()) return 0.0;
    // Greedy deterministic pairing by max similarity per neighbor
    double sum = 0.0; int cnt = 0;
    for (String u2 : ou) {
      double best = 0.0; String bestV = null;
      for (String v2 : nv) {
        double s = get(S, u2, v2, 0.0);
        if (s > best || (s == best && (bestV == null || v2.compareTo(bestV) < 0))) {
          best = s; bestV = v2;
        }
      }
      sum += best; cnt++;
    }
    return cnt == 0 ? 0.0 : (sum / cnt);
  }

  private static SortedMap<String, SortedMap<String, Double>> copy(Map<String, Map<String, Double>> m) {
    SortedMap<String, SortedMap<String, Double>> out = new TreeMap<String, SortedMap<String, Double>>();
    for (String k : new TreeSet<String>(m.keySet())) {
      SortedMap<String, Double> row = new TreeMap<String, Double>();
      row.putAll(m.get(k));
      out.put(k, row);
    }
    return out;
  }

  private static double get(Map<String, ? extends Map<String, Double>> m, String a, String b, double d) {
    Map<String, Double> r = m.get(a);
    if (r == null) return d;
    Double x = r.get(b);
    return x == null ? d : x.doubleValue();
  }
}
