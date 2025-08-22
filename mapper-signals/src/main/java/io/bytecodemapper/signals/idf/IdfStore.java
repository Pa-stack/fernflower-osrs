package io.bytecodemapper.signals.idf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Deterministic IDF store with EMA updates, clamping, and sorted persistence (4 dp). */
public final class IdfStore {
  private final SortedMap<String,Double> map = new TreeMap<String,Double>();
  private double lambda; // EMA λ

  /** Back-compat: default ctor expected by CLI. Uses λ=0.9. */
  @Deprecated
  public IdfStore() { this.lambda = 0.9; }
  public IdfStore(double lambda) { this.lambda = lambda; }
  /** Back-compat: fluent setter used by older CLI code. */
  @Deprecated
  public IdfStore setLambda(double v) { this.lambda = v; return this; }
  public static IdfStore createDefault() { return new IdfStore(0.9); }
  public double get(String key, double defaultVal) { Double v = map.get(key); return v==null? defaultVal : v.doubleValue(); }
  public void put(String key, double val) { map.put(key, val); }
  public SortedMap<String,Double> snapshot() { return new TreeMap<String,Double>(map); }

  public void load(Path p) throws IOException {
    map.clear();
    if (!Files.exists(p)) return;
    java.util.List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
    for (String s : lines) {
      if (s.trim().isEmpty() || s.startsWith("#")) continue;
      int i = s.indexOf('=');
      if (i <= 0) continue;
      String k = s.substring(0, i);
      double v = Double.parseDouble(s.substring(i+1));
      map.put(k, v);
    }
  }

  public void save(Path p) throws IOException {
    Files.createDirectories(p.getParent());
    java.util.List<String> lines = new java.util.ArrayList<String>(map.size());
    for (Map.Entry<String,Double> e : map.entrySet()) {
      lines.add(e.getKey() + "=" + String.format(java.util.Locale.ROOT, "%.4f", e.getValue()));
    }
    byte[] bytes = join(lines).getBytes(StandardCharsets.UTF_8);
    Path tmp = p.resolveSibling(p.getFileName().toString()+".tmp");
    Files.write(tmp, bytes);
    Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private static String join(java.util.List<String> ls){ StringBuilder sb=new StringBuilder(); for(int i=0;i<ls.size();i++){ if(i>0) sb.append('\n'); sb.append(ls.get(i)); } return sb.toString(); }

  /** Update using EMA: new = clamp( λ*old + (1-λ)*fresh ), fresh = ln((N+1)/(df+1))+1, clamp to [0.5,3.0]. */
  public void update(String key, int N, int df) {
    double fresh = Math.log((N + 1.0) / (df + 1.0)) + 1.0;
    double old = get(key, fresh);
    double merged = lambda*old + (1.0 - lambda)*fresh;
    double clamped = Math.max(0.5, Math.min(3.0, merged));
    map.put(key, clamped);
  }

  /** Back-compat: CLI uses File overloads. */
  @Deprecated
  public static IdfStore load(java.io.File f) throws java.io.IOException {
    IdfStore s = createDefault();
    s.load(f.toPath());
    return s;
  }

  /** Back-compat: CLI uses File overloads. */
  @Deprecated
  public void save(java.io.File f) throws java.io.IOException {
    save(f.toPath());
  }

  /** Back-compat: some CLI paths expect a bit-IDF array; return 17×1.0 as a no-op. */
  @Deprecated
  public double[] computeIdf() {
    double[] arr = new double[17];
    java.util.Arrays.fill(arr, 1.0);
    return arr;
  }
}
