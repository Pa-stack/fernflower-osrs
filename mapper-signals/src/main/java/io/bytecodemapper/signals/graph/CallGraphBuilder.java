package io.bytecodemapper.signals.graph;

import io.bytecodemapper.signals.norm.NormalizedMethod;
import java.util.*;

/** Deterministic call-graph builder over NormalizedMethod (Java 8). */
public final class CallGraphBuilder {
  private CallGraphBuilder() {}

  /** Simple directed graph: out adjacency per node id (sorted, stable). */
  public static final class Graph {
    public final SortedMap<String, SortedSet<String>> out = new TreeMap<String, SortedSet<String>>();
  }

  /**
   * Build a graph from methods. Node ids default to fingerprint; edges include app→app calls
   * when a callee signature appears among the provided set. Since NormalizedMethod doesn't expose
   * its own owner#name(desc), this best-effort builder only adds nodes deterministically.
   * For tests using ids with prefixes, prefer the overload with a prefix.
   */
  public static Graph fromMethods(java.util.List<NormalizedMethod> methods) {
    return fromMethods(methods, "");
  }

  /** Overload allowing an id prefix (e.g., "old#" or "new#"). */
  public static Graph fromMethods(java.util.List<NormalizedMethod> methods, String prefix) {
    Graph g = new Graph();
    // Insert nodes deterministically and index self signatures → node ids
    SortedMap<String,String> sigToNode = new TreeMap<String,String>();
    for (NormalizedMethod m : methods) {
      String nid = prefix + m.fingerprintSha256();
      g.out.put(nid, new TreeSet<String>());
      sigToNode.put(m.selfSignature(), nid);
    }
    // Build edges: app→app only, filter out java./javax.
    for (NormalizedMethod m : methods) {
      String from = prefix + m.fingerprintSha256();
      SortedSet<String> outs = g.out.get(from);
      SortedMap<String,Integer> bag = m.callBag();
      for (String callee : bag.keySet()) {
        int h = callee.indexOf('#');
        String owner = h >= 0 ? callee.substring(0, h) : callee;
        if (owner.startsWith("java/") || owner.startsWith("javax/")) continue;
        String to = sigToNode.get(callee);
        if (to != null) outs.add(to);
      }
    }
    return g;
  }
}
