package io.bytecodemapper.core.type;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Immutable class type-evidence fingerprint and similarity scorer.
 * Features: superclass, interfaces, field-type multiset, method-type multiset (params+returns), counts.
 * Scoring weights (literal as required): fields 0.40, method types 0.30, superclass 0.20, interfaces 0.10.
 */
public final class TypeEvidenceFingerprint {
  // Weights must remain literal for acceptance
  private static final double W_FIELDS = 0.40, W_METHOD_TYPES = 0.30, W_SUPER = 0.20, W_INTERFACES = 0.10;

  private final String superName;              // internal name or null
  private final SortedSet<String> interfaces;  // sorted for determinism
  private final SortedMap<String,Integer> fieldTypes;   // multiset via counts
  private final SortedMap<String,Integer> methodTypes;  // multiset via counts (params+returns)
  private final int methodCount;
  private final int fieldCount;

  private TypeEvidenceFingerprint(Builder b) {
    this.superName = b.superName;
    this.interfaces = Collections.unmodifiableSortedSet(new TreeSet<String>(b.interfaces));
    this.fieldTypes = unmodifiableSortedCounts(b.fieldTypes);
    this.methodTypes = unmodifiableSortedCounts(b.methodTypes);
    this.methodCount = b.methodCount;
    this.fieldCount = b.fieldCount;
  }

  private static SortedMap<String,Integer> unmodifiableSortedCounts(Map<String,Integer> src) {
    SortedMap<String,Integer> m = new TreeMap<String,Integer>();
    for (Map.Entry<String,Integer> e : src.entrySet()) {
      if (e.getValue() != null && e.getValue() > 0) m.put(e.getKey(), e.getValue());
    }
    return Collections.unmodifiableSortedMap(m);
  }

  public byte[] toBytes() {
    // Deterministic ASCII serialization for caching and tie-breaks
    StringBuilder sb = new StringBuilder();
    sb.append("super=").append(superName == null ? "" : superName).append('\n');
    sb.append("interfaces=");
    boolean first = true;
    for (String itf : interfaces) { if (!first) sb.append(','); sb.append(itf); first = false; }
    sb.append('\n');
    sb.append("fieldTypes=");
    first = true;
    for (Map.Entry<String,Integer> e : fieldTypes.entrySet()) { if (!first) sb.append(','); sb.append(e.getKey()).append(':').append(e.getValue()); first = false; }
    sb.append('\n');
    sb.append("methodTypes=");
    first = true;
    for (Map.Entry<String,Integer> e : methodTypes.entrySet()) { if (!first) sb.append(','); sb.append(e.getKey()).append(':').append(e.getValue()); first = false; }
    sb.append('\n');
    sb.append("counts=").append(fieldCount).append(',').append(methodCount).append('\n');
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  public double similarity(TypeEvidenceFingerprint other) {
    double sFields = jaccardMultiset(this.fieldTypes, other.fieldTypes);
    double sMeths  = jaccardMultiset(this.methodTypes, other.methodTypes);
    double sSuper  = (this.superName == null ? other.superName == null : this.superName.equals(other.superName)) ? 1.0 : 0.0;
    double sItf    = jaccardSet(this.interfaces, other.interfaces);
    return W_FIELDS * sFields + W_METHOD_TYPES * sMeths + W_SUPER * sSuper + W_INTERFACES * sItf;
  }

  private static double jaccardSet(SortedSet<String> a, SortedSet<String> b) {
    if (a.isEmpty() && b.isEmpty()) return 1.0;
    int inter = 0, union = 0;
    Iterator<String> ia = a.iterator(), ib = b.iterator();
    String xa = ia.hasNext() ? ia.next() : null, xb = ib.hasNext() ? ib.next() : null;
    while (xa != null || xb != null) {
      int cmp = (xa == null) ? 1 : (xb == null ? -1 : xa.compareTo(xb));
      if (cmp == 0) { inter++; union++; xa = ia.hasNext()?ia.next():null; xb = ib.hasNext()?ib.next():null; }
      else if (cmp < 0) { union++; xa = ia.hasNext()?ia.next():null; }
      else { union++; xb = ib.hasNext()?ib.next():null; }
    }
    return union == 0 ? 1.0 : ((double) inter) / union;
  }

  private static double jaccardMultiset(SortedMap<String,Integer> a, SortedMap<String,Integer> b) {
    if (a.isEmpty() && b.isEmpty()) return 1.0;
    Iterator<Map.Entry<String,Integer>> ia = a.entrySet().iterator();
    Iterator<Map.Entry<String,Integer>> ib = b.entrySet().iterator();
    Map.Entry<String,Integer> ea = ia.hasNext()?ia.next():null, eb = ib.hasNext()?ib.next():null;
    long inter = 0L, union = 0L;
    while (ea != null || eb != null) {
      int cmp = (ea == null) ? 1 : (eb == null ? -1 : ea.getKey().compareTo(eb.getKey()));
      if (cmp == 0) {
        int va = ea.getValue(), vb = eb.getValue();
        inter += Math.min(va, vb);
        union += Math.max(va, vb);
        ea = ia.hasNext()?ia.next():null; eb = ib.hasNext()?ib.next():null;
      } else if (cmp < 0) {
        union += ea.getValue(); ea = ia.hasNext()?ia.next():null;
      } else {
        union += eb.getValue(); eb = ib.hasNext()?ib.next():null;
      }
    }
    return union == 0 ? 1.0 : ((double) inter) / ((double) union);
  }

  public static final class Builder {
    private String superName;
    private final SortedSet<String> interfaces = new TreeSet<String>();
    private final Map<String,Integer> fieldTypes = new LinkedHashMap<String,Integer>();
    private final Map<String,Integer> methodTypes = new LinkedHashMap<String,Integer>();
    private int methodCount;
    private int fieldCount;

    public Builder superName(String n) { this.superName = n; return this; }
    public Builder addInterface(String n) { this.interfaces.add(n); return this; }
    public Builder addFieldType(String desc) { this.fieldTypes.put(desc, this.fieldTypes.getOrDefault(desc, 0) + 1); return this; }
    public Builder addMethodParamType(String desc) { this.methodTypes.put(desc, this.methodTypes.getOrDefault(desc, 0) + 1); return this; }
    public Builder addMethodReturnType(String desc) { return addMethodParamType(desc); }
    public Builder methodCount(int c) { this.methodCount = c; return this; }
    public Builder fieldCount(int c) { this.fieldCount = c; return this; }
    public TypeEvidenceFingerprint build() { return new TypeEvidenceFingerprint(this); }
  }
}
