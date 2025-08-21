### Files & snippets

Note: phase1.json was not found in this repository, so method-by-method cross‑checks against it could not be performed. The notes below were verified directly against the current code in this repo.

* `mapper-cli/src/main/java/io/bytecodemapper/core/index/NsfIndex.java#add/exact/near`

  ```java
  // Index is keyed by (owner, desc). Each entry stores NewRef{owner,name,desc,nsf64,bucket}
  // bucket is "nsf64" (canonical) or "nsf_surrogate"; canonical is preferred on dedup.
  public void add(String owner, String desc, String name, long nsf64, Mode mode) { ... }

  // Exact match with deterministic dedup (favor canonical) and stable sort
  public List<NewRef> exact(String owner, String desc, long nsf64) { ... }

  // Near match by Hamming distance over 64‑bit fp; widened budget is applied by caller
  public List<NewRef> near(String owner, String desc, long nsf64, int hammingBudget) { ... }
  ```

  ```java
  // Deterministic ordering is enforced inside exact()/near() by sorting (owner, desc, name)
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/cli/MapOldNew.java#parseFlags`

  ```java
  // --use-nsf64 canonical|surrogate|both (default: CANONICAL)
  io.bytecodemapper.cli.flags.UseNsf64Mode useNsf64Mode = UseNsf64Mode.CANONICAL;
  // accepts "--use-nsf64 <mode>" or "--use-nsf64=<mode>"
  // ...later forwarded to MethodMatcher.setUseNsf64Mode(useNsf64Mode)

  // --dump-normalized-features[=dir] emits deterministic NSFv2 JSONL to dir (default path if bare flag)
  String dumpNormalizedDir = null;
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers`

  ```java
  // Tier order is configurable: default "exact,near,wl,wlrelaxed"
  // 1) NSF exact (canonical nsf64 when available; surrogate fallback depends on --use-nsf64)
  // 2) NSF near (Hamming, default budget=1; widened to 2 when flattening detected on either side)
  // 3) WL exact
  // 4) WL relaxed (L1<=l1Tau & within ±sizeBand); feeds the candidate pool
  // Candidates are then deduplicated in tier order; scoring happens after, not pre‑sorted by score.
  ```

* `mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedMethod.java#normalizedStackDeltaHistogram`

  ```java
  // Stack histogram {-2,-1,0,+1,+2} in fixed key order over filteredInsns
  private Map<String,Integer> normalizedStackDeltaHistogram() {
    // impl uses stackDeltaSlots(...) and maintains LinkedHashMap in fixed order
  }
  ```

* `NormalizedMethod.java#try/catch shape`

  ```java
  // [CODEGEN] try-depth, fanout, catch-types hash
  private int normalizedTryDepth() {
    int max = 0;
    for (TryCatchBlockNode a : tryCatchBlocks) {
      int d = 1;
      for (TryCatchBlockNode b : tryCatchBlocks) if (a != b && encloses(b, a)) d++;
      if (d > max) max = d;
    }
    return max;
  }
  private int normalizedTryFanout() { return tryCatchBlocks.size(); }
  private int normalizedCatchTypesHash() {
    List<String> types = new ArrayList<>();
    for (TryCatchBlockNode t : tryCatchBlocks) if (t.type != null) types.add(t.type);
    Collections.sort(types); // deterministic
    return (int) StableHash64.hashUtf8(String.join(",", types));
  }
  ```

* `NormalizedMethod.java#normalizedLiteralsMinHash64`

  ```java
  // [CODEGEN] numeric-literal MinHash (64 buckets, ignore -1..5)
  private int[] normalizedLiteralsMinHash64() {
    if (numericLiterals.isEmpty()) return null; // deterministic "empty"
    final int B = 64;
    int[] sketch = new int[B];
    Arrays.fill(sketch, Integer.MAX_VALUE);
    for (Long v : numericLiterals) {
      if (v >= -1 && v <= 5) continue; // filter noise
      int h = (int) StableHash64.hashUtf8(Long.toString(v));
      int b = h & (B - 1); // fixed bucket
      if (h < sketch[b]) sketch[b] = h;
    }
    return sketch;
  }
  ```

* `NormalizedMethod.java#invoke-kind encoding`

  ```java
  // [CODEGEN] invoke kind counts [VIRT, STATIC, INTERFACE, CTOR]
  private int[] invokeKindCounts() {
    int virt=0, stat=0, intf=0, ctor=0;
    for (AbstractInsnNode insn : instructions) {
      switch (insn.getOpcode()) {
        case INVOKEVIRTUAL:   virt++; break;
        case INVOKESTATIC:    stat++; break;
        case INVOKEINTERFACE: intf++; break;
        case INVOKESPECIAL:   // ctor detection
          MethodInsnNode m = (MethodInsnNode) insn;
          if ("<init>".equals(m.name)) ctor++; else virt++; // conservative
          break;
      }
    }
    return new int[]{virt, stat, intf, ctor};
  }
  ```

* `NormalizedMethod.java#buildNsfPayloadV2`

  ```java
  // NSFv2 payload (sorted, deterministic). Tags and order in current code:
  private String buildNsfPayloadV2() {
    // Header
    out.append("NSFv2\n");
    // D|<descriptor>
    // O|<sorted opcode keys>
    // S|<sorted invoked signatures>
    // T|<sorted strings>
    // H|-2:n,-1:n,0:n,+1:n,+2:n  (fixed order)
    // Y|<tryDepth>|<tryFanout>|<catchTypesHash>
    // L|∅  or  L|v0,v1,...,v63
    // K|virt,static,interface,ctor
    // nsf64 = StableHash64.hashUtf8(payload)
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
  // Deterministic JSONL dump (NSFv2 & sorted fields)
  {
    "owner":..., "name":..., "desc":...,
    "nsf_version":"NSFv2",
    "nsf64_hex":"%016x",
    "stackHist": {"-2":n,"-1":n,"0":n,"+1":n,"+2":n},
    "tryDepth":n, "tryFanout":n, "catchTypesHash":n,
    "litsMinHash64": [..] or "∅",
    "invokeKindCounts":[V,S,I,C],
    "strings":[sorted...], "invokes":[sorted...]
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

- Build all modules and run tests
  - Windows PowerShell
    - gradlew.bat build
- Verify CLI flags are recognized
  - mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat mapOldNew --help | Select-String "--use-nsf64"
- Generate a small NSF dump
  - Run the workspace task "Mapper: mapOldNew" and check that mapper-cli/build/nsf-jsonl contains old.jsonl and new.jsonl with nsf_version="NSFv2".
