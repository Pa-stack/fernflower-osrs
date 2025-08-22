package io.bytecodemapper.cli;

import io.bytecodemapper.signals.graph.CallGraphBuilder;
import io.bytecodemapper.signals.graph.IsoRankRefiner;
import io.bytecodemapper.signals.norm.NormalizedMethod;

import java.util.*;

/** Tiny CLI-facing helper to optionally run IsoRank refinement (deterministic). */
public final class RefineRunner {
  private RefineRunner() {}

  public static SortedMap<String, SortedMap<String, Double>> maybeRefine(
      boolean enabled,
      java.util.List<NormalizedMethod> oldMs,
      java.util.List<NormalizedMethod> newMs,
      java.util.Map<String, java.util.Map<String, Double>> S0) {
    if (!enabled) return sortedCopy(S0);
    CallGraphBuilder.Graph oldG = CallGraphBuilder.fromMethods(oldMs, "old#");
    CallGraphBuilder.Graph newG = CallGraphBuilder.fromMethods(newMs, "new#");
    return IsoRankRefiner.refine(oldG, newG, S0);
  }

  private static SortedMap<String, SortedMap<String, Double>> sortedCopy(Map<String, Map<String, Double>> m) {
    SortedMap<String, SortedMap<String, Double>> out = new TreeMap<String, SortedMap<String, Double>>();
    for (String k : new TreeSet<String>(m.keySet())) {
      SortedMap<String, Double> row = new TreeMap<String, Double>();
      Map<String, Double> r = m.get(k);
      if (r != null) row.putAll(r);
      out.put(k, row);
    }
    return out;
  }
}
