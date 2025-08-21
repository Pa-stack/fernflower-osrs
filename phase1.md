### Files & snippets

Note: phase1.json was not found in this repository, so method-by-method cross‑checks against it could not be performed. The notes below were verified directly against the current code in this repo.

* `mapper-cli/src/main/java/io/bytecodemapper/core/index/NsfIndex.java#add/exact/near`

  ```java
  // Index is keyed by (owner, desc). Each entry stores NewRef{owner,name,desc,nsf64,bucket}
  // bucket is "nsf64" (canonical) or "nsf_surrogate"; canonical is preferred on dedup.
  public void add(String owner, String desc, String name, long nsf64, Mode mode) {
    String k = key(owner, desc);
    ArrayList<NewRef> bucket = byKey.get(k);
    if (bucket == null) { bucket = new ArrayList<NewRef>(); byKey.put(k, bucket); }
    if (mode == Mode.BOTH) {
      if (nsf64 != 0L) {
        bucket.add(new NewRef(owner, name, desc, nsf64, "nsf64"));
        bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
      } else {
        bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
      }
    } else if (mode == Mode.CANONICAL) {
      bucket.add(new NewRef(owner, name, desc, nsf64, "nsf64"));
    } else {
      bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
    }
  }

  // Exact match with deterministic dedup (favor canonical) and stable sort
  public java.util.List<NewRef> exact(String owner, String desc, long nsf64) {
    ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return Collections.emptyList();
    LinkedHashMap<String,NewRef> dedup = new LinkedHashMap<>();
    for (NewRef r : b) {
      if (r.nsf64 == nsf64) {
        String refKey = r.owner + "\u0000" + r.name + "\u0000" + r.desc + "\u0000" + r.nsf64;
        if (!dedup.containsKey(refKey) || "nsf64".equals(r.bucket)) {
          dedup.put(refKey, r);
        }
      }
    }
    ArrayList<NewRef> out = new ArrayList<>(dedup.values());
    sort(out); return out;
  }

  // Near match by Hamming distance over 64‑bit fp; widened budget is applied by caller
  public java.util.List<NewRef> near(String owner, String desc, long nsf64, int hammingBudget) {
    ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return Collections.emptyList();
    LinkedHashMap<String,NewRef> dedup = new LinkedHashMap<>();
    for (NewRef r : b) {
      int pop = Long.bitCount(r.nsf64 ^ nsf64);
      if (pop <= hammingBudget) {
        String refKey = r.owner + "\u0000" + r.name + "\u0000" + r.desc + "\u0000" + r.nsf64;
        if (!dedup.containsKey(refKey) || "nsf64".equals(r.bucket)) {
          dedup.put(refKey, r);
        }
      }
    }
    ArrayList<NewRef> out = new ArrayList<>(dedup.values());
    sort(out);
    int MAX = Math.min(512, out.size());
    return new ArrayList<>(out.subList(0, MAX));
  }
  ```

  ```java
  private static void sort(ArrayList<NewRef> xs){
    Collections.sort(xs, new Comparator<NewRef>() {
      public int compare(NewRef a, NewRef b){
        int c = a.owner.compareTo(b.owner); if (c!=0) return c;
        c = a.desc.compareTo(b.desc); if (c!=0) return c;
        return a.name.compareTo(b.name);
      }});
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/cli/MapOldNew.java#parseFlags`

  ```java
  // Initial flags
  boolean deterministic = false;
  String cacheDirStr = "mapper-cli/build/cache";
  String idfPathStr  = "mapper-cli/build/idf.properties";
  String dumpNormalizedDir = null; // --dump-normalized-features[=dir]
  String reportPathStr = null; // --report <path>
  for (int i=0;i<args.length;i++) {
    if ("--deterministic".equals(args[i])) { deterministic = true; }
    else if ("--cacheDir".equals(args[i]) && i+1<args.length) { cacheDirStr = args[++i]; }
    else if ("--idf".equals(args[i]) && i+1<args.length) { idfPathStr = args[++i]; }
    else if (args[i].startsWith("--dump-normalized-features")) {
      String a = args[i];
      int eq = a.indexOf('=');
      if (eq > 0 && eq < a.length()-1) dumpNormalizedDir = a.substring(eq+1);
    } else if ("--report".equals(args[i]) && i+1<args.length) {
      reportPathStr = args[++i];
    }
  }

  // Debug/rollout flags
  String nsfTierOrder = "exact,near,wl,wlrelaxed";
  io.bytecodemapper.cli.flags.UseNsf64Mode useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.CANONICAL;
  for (int i=0;i<args.length;i++) {
    String a = args[i];
    if ("--nsf-tier-order".equals(a) && i+1<args.length) {
      nsfTierOrder = args[++i];
    } else if ("--use-nsf64".equals(a) && i+1<args.length) {
      useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.parse(args[++i]);
    } else if (a.startsWith("--use-nsf64=")) {
      String val = a.substring("--use-nsf64=".length());
      useNsf64Mode = io.bytecodemapper.cli.flags.UseNsf64Mode.parse(val);
    }
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers`

  ```java
  // Compute near budget with flattening detection
  boolean anyNewFlattened = newSideAnyFlattenedForOwnerDesc(newClasses, newOwner, desc);
  boolean flattened = oldFlattened || anyNewFlattened;
  if (flattened) out.flatteningDetected++;
  final int nearBudget = flattened ? Math.max(1, options.nsfNearBudgetWhenFlattened) : 1;

  for (String tier : NSFTierOrder.split(",")) {
    String t = tier.trim().toLowerCase(java.util.Locale.ROOT);
    if ("exact".equals(t)) {
      if (nsfIdx != null) {
        java.util.List<Long> fps = queryFps(oldCanonical, oldSurrogate, NSF_MODE);
        for (Long qfp : fps) {
          java.util.List<NsfIndex.NewRef> xs = nsfIdx.exact(newOwner, desc, qfp.longValue());
          for (NsfIndex.NewRef r : xs) {
            candsExact.add(new NewRef(r.owner, r.name, r.desc, 0));
            String k = r.owner + "\u0000" + r.desc + "\u0000" + r.name;
            if (!candProv.containsKey(k)) candProv.put(k, "nsf64");
          }
        }
      }
    } else if ("near".equals(t)) {
      if (nsfIdx != null) {
        java.util.List<Long> fps = queryFps(oldCanonical, oldSurrogate, NSF_MODE);
        for (Long qfp : fps) {
          java.util.List<NsfIndex.NewRef> xs = nsfIdx.near(newOwner, desc, qfp.longValue(), nearBudget);
          for (NsfIndex.NewRef r : xs) {
            candsNear.add(new NewRef(r.owner, r.name, r.desc, 0));
            String k = r.owner + "\u0000" + r.desc + "\u0000" + r.name;
            if (!candProv.containsKey(k)) candProv.put(k, "nsf64");
          }
        }
        if (flattened && nearBudget > 1 && !candsNear.isEmpty()) {
          out.nearBeforeGates += candsNear.size();
          candsNear = applyFlatteningGatesIfNeeded(true, oldNormFeatures, candsNear, options.stackCosineThreshold, newClasses, newOwner, desc);
          out.nearAfterGates += candsNear.size();
        }
      }
    } else if ("wl".equals(t)) {
      java.util.List<NewRef> xs = wlIndex.getOrDefault(new Key(desc, oldWl), java.util.Collections.<NewRef>emptyList());
      candsWl.addAll(xs);
      for (NewRef r : xs) { String k = r.owner + "\u0000" + desc + "\u0000" + r.name; if (!candProv.containsKey(k)) candProv.put(k, "wl"); }
    } else if ("wlrelaxed".equals(t)) {
      final int l1Tau = options.wlRelaxedL1;
      final double band = options.wlSizeBand;
      java.util.List<NewRef> xs = relaxedCandidates(oldClasses, newClasses, oldOwner, oldName, desc, newFeat, newOwner, deterministic, l1Tau, band);
      if (!xs.isEmpty()) { out.wlRelaxedGatePasses++; out.wlRelaxedCandidates += xs.size(); }
      candsRelax.addAll(xs);
      for (NewRef r : xs) { String k = r.owner + "\u0000" + desc + "\u0000" + r.name; if (!candProv.containsKey(k)) candProv.put(k, "wl_relaxed"); }
    }
  }
  ```

* `mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedMethod.java#normalizedStackDeltaHistogram`

  ```java
  // Stack histogram {-2,-1,0,+1,+2} in fixed key order over filteredInsns
  private Map<String,Integer> normalizedStackDeltaHistogram() {
    final String[] keys = new String[]{"-2","-1","0","+1","+2"};
    LinkedHashMap<String,Integer> hist = new LinkedHashMap<String,Integer>();
    for (String k : keys) hist.put(k, Integer.valueOf(0));
    if (filteredInsns == null) return hist;

    for (AbstractInsnNode insn : filteredInsns) {
      int op = insn.getOpcode();
      if (op < 0) continue;
      int delta = stackDeltaSlots(insn);
      if (delta < -2) delta = -2; else if (delta > 2) delta = 2;
      switch (delta) {
        case -2: hist.put("-2", Integer.valueOf(hist.get("-2").intValue()+1)); break;
        case -1: hist.put("-1", Integer.valueOf(hist.get("-1").intValue()+1)); break;
        case 0:  hist.put("0",  Integer.valueOf(hist.get("0").intValue()+1)); break;
        case 1:  hist.put("+1", Integer.valueOf(hist.get("+1").intValue()+1)); break;
        case 2:  hist.put("+2", Integer.valueOf(hist.get("+2").intValue()+1)); break;
      }
    }
    return hist;
  }
  ```

* `NormalizedMethod.java#try/catch shape`

  ```java
  // [CODEGEN] try-depth, fanout, catch-types hash over filteredTryCatch
  private int normalizedTryDepth() {
    if (filteredTryCatch == null || filteredTryCatch.isEmpty() || filteredInsns == null || filteredInsns.length==0) return 0;
    Map<LabelNode,Integer> labelIndex = buildLabelIndex();
    ArrayList<int[]> events = new ArrayList<int[]>();
    for (TryCatchBlockNode t : filteredTryCatch) {
      Integer s = labelIndex.get(t.start);
      Integer e = labelIndex.get(t.end);
      if (s == null || e == null) continue;
      events.add(new int[]{s.intValue(), +1});
      events.add(new int[]{e.intValue(), -1});
    }
    if (events.isEmpty()) return 0;
    Collections.sort(events, new Comparator<int[]>() {
      public int compare(int[] a, int[] b) { int c = Integer.compare(a[0], b[0]); if (c!=0) return c; return Integer.compare(a[1], b[1]); }
    });
    int depth = 0, maxDepth = 0;
    for (int[] ev : events) { depth += ev[1]; if (depth > maxDepth) maxDepth = depth; }
    return maxDepth;
  }
  private int normalizedTryFanout() {
    if (filteredTryCatch == null || filteredTryCatch.isEmpty()) return 0;
    Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
    for (TryCatchBlockNode t : filteredTryCatch) {
      String key = System.identityHashCode(t.start) + ":" + System.identityHashCode(t.end);
      Integer cur = counts.get(key);
      counts.put(key, Integer.valueOf(cur==null?1:cur.intValue()+1));
    }
    int max = 0; for (Integer v : counts.values()) if (v!=null && v.intValue()>max) max = v.intValue();
    return max;
  }
  private int normalizedCatchTypesHash() {
    if (filteredTryCatch == null || filteredTryCatch.isEmpty()) return 0;
    ArrayList<String> types = new ArrayList<String>();
    for (TryCatchBlockNode t : filteredTryCatch) {
      if (t.type != null) types.add(t.type);
    }
    if (types.isEmpty()) return 0;
    Collections.sort(types);
    String joined = join(types, ",");
    long h = StableHash64.hashUtf8(joined);
    return (int)(h ^ (h >>> 32));
  }
  ```

* `NormalizedMethod.java#normalizedLiteralsMinHash64`

  ```java
  // [CODEGEN] numeric-literal MinHash (64 buckets, ignore -1..5)
  private int[] normalizedLiteralsMinHash64() {
    if (filteredInsns == null || filteredInsns.length == 0) return null;
    final int BUCKETS = 64;
    int[] sketch = new int[BUCKETS];
    Arrays.fill(sketch, Integer.MAX_VALUE);
    boolean saw = false;
    for (AbstractInsnNode insn : filteredInsns) {
      int op = insn.getOpcode();
      if (op < 0) continue;
      if (op == BIPUSH || op == SIPUSH) {
        if (insn instanceof IntInsnNode) {
          int v = ((IntInsnNode) insn).operand;
          if (v >= -1 && v <= 5) continue;
          saw |= updateSketch(sketch, String.valueOf(v));
        }
      } else if (insn instanceof LdcInsnNode) {
        Object c = ((LdcInsnNode) insn).cst;
        if (c instanceof Integer) {
          int v = ((Integer) c).intValue();
          if (v >= -1 && v <= 5) continue;
          saw |= updateSketch(sketch, String.valueOf(v));
        } else if (c instanceof Long) {
          saw |= updateSketch(sketch, String.valueOf(((Long) c).longValue()));
        } else if (c instanceof Float) {
          saw |= updateSketch(sketch, Float.toString(((Float) c).floatValue()));
        } else if (c instanceof Double) {
          saw |= updateSketch(sketch, Double.toString(((Double) c).doubleValue()));
        }
      }
    }
    if (!saw) return null;
    return sketch;
  }
  ```

* `NormalizedMethod.java#invoke-kind encoding`

  ```java
  // [CODEGEN] invoke kind counts [VIRT, STATIC, INTERFACE, CTOR]
  private int[] invokeKindCounts() {
  int virt = getOpCount(INVOKEVIRTUAL);
  int stat = getOpCount(INVOKESTATIC);
  int itf  = getOpCount(INVOKEINTERFACE);
  int ctor = 0;
  for (String sig : this.invokedSignatures) if (sig.indexOf(".<init>(") >= 0) ctor++;
  return new int[]{virt, stat, itf, ctor};
  }
  ```

* `NormalizedMethod.java#buildNsfPayloadV2`

  ```java
  // NSFv2 payload (sorted, deterministic). Tags and order in current code:
  private String buildNsfPayloadV2() {
    StringBuilder out = new StringBuilder();
    out.append("NSFv2\n");
    out.append("D|").append(this.normalizedDescriptor).append('\n');
    ArrayList<String> opcodeKeys = new ArrayList<String>();
    for (Integer k : this.opcodeHistogram.keySet()) opcodeKeys.add(String.valueOf(k));
    Collections.sort(opcodeKeys);
    out.append("O|").append(join(opcodeKeys, ",")).append('\n');
    ArrayList<String> invokes = new ArrayList<String>(this.invokedSignatures);
    Collections.sort(invokes);
    out.append("S|").append(join(invokes, ",")).append('\n');
    ArrayList<String> strs = new ArrayList<String>(this.stringConstants);
    Collections.sort(strs);
    out.append("T|").append(join(strs, ",")).append('\n');
    LinkedHashMap<String,Integer> sh = normalizedStackDeltaHistogram();
    out.append("H|")
       .append("-2:").append(String.valueOf(sh.get("-2")))
       .append(',').append("-1:").append(String.valueOf(sh.get("-1")))
       .append(',').append("0:").append(String.valueOf(sh.get("0")))
       .append(',').append("+1:").append(String.valueOf(sh.get("+1")))
       .append(',').append("+2:").append(String.valueOf(sh.get("+2")))
       .append('\n');
    out.append("Y|").append(normalizedTryDepth()).append('|').append(normalizedTryFanout()).append('|').append(normalizedCatchTypesHash()).append('\n');
    int[] sketch = normalizedLiteralsMinHash64();
    if (sketch == null) {
      out.append("L|∅\n");
    } else {
      out.append("L|");
      for (int i=0;i<sketch.length;i++) { if (i>0) out.append(','); out.append(sketch[i]); }
      out.append('\n');
    }
    int[] kinds = invokeKindCounts();
    out.append("K|").append(kinds[0]).append(',').append(kinds[1]).append(',').append(kinds[2]).append(',').append(kinds[3]);
    return out.toString();
  }
  ```

* `mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedFeatures.java#getters`

  ```java
  // [CODEGEN] nsf64 source of truth + new feature accessors
  public long   getNsf64()                   { return nsf64; }
  public Map<String,Integer> getStackHist()  { return stackHist; }
  public int    getTryDepth()                { return tryDepth; }
  public int    getTryFanout()               { return tryFanout; }
  public int    getCatchTypesHash()          { return catchTypesHash; }
  public int[]  getLitsMinHash64()           { return litsMinHash64; }
  public int[]  getInvokeKindCounts()        { return invokeKindCounts; }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/cli/util/NormalizedDumpWriter.java#write`

  ```java
  private static void writeJsonl(BufferedWriter bw, String owner, String name, String desc,
                 NormalizedMethod nm,
                 io.bytecodemapper.signals.normalized.NormalizedFeatures nf) throws IOException {
    StringBuilder sb = new StringBuilder(512);
    sb.append('{');
    field(sb, "owner", owner).append(',');
    field(sb, "name", name).append(',');
    field(sb, "desc", desc).append(',');
    field(sb, "nsf_version", "NSFv2").append(',');
    field(sb, "nsf64_hex", toHex16(nf.getNsf64())).append(',');
    sb.append("\"stackHist\":{");
    {
      java.util.Map<String,Integer> sh = nf.getStackHist();
      String[] order = new String[]{"-2","-1","0","+1","+2"};
      for (int i=0;i<order.length;i++) {
        if (i>0) sb.append(',');
        String k = order[i];
        sb.append('"').append(escapeJson(k)).append('"').append(':').append(String.valueOf(sh.get(k)));
      }
    }
    sb.append('}').append(',');
    sb.append("\"tryDepth\":").append(nf.getTryDepth()).append(',');
    sb.append("\"tryFanout\":").append(nf.getTryFanout()).append(',');
    sb.append("\"catchTypesHash\":").append(nf.getCatchTypesHash()).append(',');
    sb.append("\"litsMinHash64\":");
    int[] sk = nf.getLitsMinHash64();
    if (sk == null) {
      sb.append('"').append("∅").append('"');
    } else {
      sb.append('[');
      for (int i=0;i<sk.length;i++) { if (i>0) sb.append(','); sb.append(sk[i]); }
      sb.append(']');
    }
    sb.append(',');
    sb.append("\"invokeKindCounts\":");
    int[] kc = nf.getInvokeKindCounts();
    sb.append('[').append(kc[0]).append(',').append(kc[1]).append(',').append(kc[2]).append(',').append(kc[3]).append(']').append(',');
    java.util.List<String> strings = new java.util.ArrayList<String>(nm.stringConstants);
    java.util.Collections.sort(strings);
    sb.append("\"strings\":"); writeStringArray(sb, strings); sb.append(',');
    java.util.List<String> invs = new java.util.ArrayList<String>(nm.invokedSignatures);
    java.util.Collections.sort(invs);
    sb.append("\"invokes\":"); writeStringArray(sb, invs);
    sb.append('}').append('\n');
    bw.write(sb.toString());
  }
  ```

---

### Flags & defaults

| Flag                               | Values                               | Default     | Effect                                                                    | Notes                                                                                                                         |
| ---------------------------------- | ------------------------------------ | ----------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `--use-nsf64`                      | `canonical` \| `surrogate` \| `both` | `canonical` | Selects which fingerprint feeds `NsfIndex` (and candidate tiers).         | Canonical preferred; surrogate is stable hash fallback; both allowed for diagnostics. |
| `--dump-normalized-features[=dir]` | path or none                         | off         | Emits deterministic NSF dump JSON including `nsf_version="NSFv2"`.        | Keys/arrays in fixed order.                                                                                                   |
| `--nsf-tier-order`                 | comma list                            | exact,near,wl,wlrelaxed | Controls tier order in `MethodMatcher`. | Default matches current implementation. |
| *(implicit)* NSFv2                 | on                                   | –           | Payload includes stack hist, try-shape, literal sketch, and invoke kinds. | Version string `NSFv2` in payload/dump.                                                                                       |

---

### Tests present

* `NormalizedFeaturesNsfV2StabilityTest` — benign LDC reorder yields identical `nsf64`.
* `NormalizedFeaturesNsfV2SensitivityTest` — opcode perturbation changes `nsf64`.
* `NormalizedFeaturesNsfV2WrapperInvarianceTest` — wrapper handler unwrap invariance.
* `NormalizedDumpTest` — deterministic JSONL dump; sorted keys.
* `ReportCandidateStatsSmokeTest` — deterministic report JSON; candidate stats present.
* `DeterminismEndToEndTest` — identical Tiny + report across repeated runs with `--deterministic`.

---

### Determinism notes

* **Stable hashing**: all payloads hashed with `StableHash64.hashUtf8()`.
* **Sorted concatenation**: strings, invokes, opcode keys, and catch types are **sorted**; stack hist uses fixed key order `{-2,-1,0,+1,+2}`.
* **Stable iteration**: candidates and dumps use **lexicographic sorts**; no reliance on hash-map iteration order; seeds and thresholds fixed.

---

### Machine summary (JSON)

```json
{
  "nsf_index": {
    "module": "mapper-cli",
    "mode_default": "canonical",
    "fallback": "surrogate",
    "provenance": "NewRef.bucket + nsfProv map in matcher"
  },
  "normalized_method": {
    "stack_histogram": true,
    "try_shape": true,
    "literals_minhash": { "size": 64, "seed": "fixed", "filter_small_ints": true },
    "invoke_kind_encoding": true,
    "payload_version": "NSFv2",
    "payload_sorted": true
  },
  "tiers": ["nsf_exact", "nsf_near", "wl_exact", "wl_relaxed"],
  "flags": {
    "use_nsf64": "canonical|surrogate|both",
    "nsf_tier_order": "exact,near,wl,wlrelaxed",
    "dump_normalized_features": true
  }
}
```

---

Acceptance checks (optional):

* Build all modules and run tests
  * Windows PowerShell
    * gradlew.bat build
* Verify CLI flags are recognized
  * mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat mapOldNew --help | Select-String "--use-nsf64"
* Generate a small NSF dump
  * Run the workspace task "Mapper: mapOldNew" and check that mapper-cli/build/nsf-jsonl contains old.jsonl and new.jsonl with nsf_version="NSFv2".
