### Files & snippets

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#detectFlattening`

  ```java
  // [CODEGEN-BEGIN: flattening-near-order]
  // Compute 'flattened' *before* any near-tier retrieval
  final boolean flattened =
      (oldFeatures != null && oldFeatures.isFlattened())
   || newSideHasAnyFlattenedForOwnerDesc(ownerInternalName, desc);
  // [CODEGEN-END: flattening-near-order]
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#nearBudgetWidening`

  ```java
  // [CODEGEN-BEGIN: flattening-near-widen compute-near-budget]
  int nearBudget = 0; // baseline near disabled or ≤1 elsewhere
  if (flattened) {
    // widen only under flattening
    nearBudget = Math.max(0, options.nsfNearBudgetWhenFlattened); // default 2
  }
  // [CODEGEN-END: flattening-near-widen compute-near-budget]

  // [CODEGEN-BEGIN: flattening-near-widen apply-budget]
  List<Candidate> near = Collections.emptyList();
  if (flattened && nearBudget > 0) {
    near = nsfIndex.near(ownerInternalName, desc, oldFeatures.getNsf64(), nearBudget);
    // telemetry: pre-/post-gate counts
    result.nearBeforeGates += near.size();
    near = applyFlatteningGatesIfNeeded(true, oldFeatures, near, options.stackCosineThreshold);
    result.nearAfterGates += near.size();
  }
  // [CODEGEN-END: flattening-near-widen apply-budget]
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#applyFlatteningGatesIfNeeded`

  ```java
  // [CODEGEN-BEGIN: flattening-gates-fastfail]
  private List<Candidate> applyFlatteningGatesIfNeeded(boolean flattened,
                                                       NormalizedFeatures oldNF,
                                                       List<Candidate> near,
                                                       double cosThresh) {
    if (!flattened || near == null || near.isEmpty()) return near;

    // Precompute old stack histogram vector in the fixed 5-key order
    final String[] KEYS = {"-2","-1","0","+1","+2"};
    final int[] oldVec = new int[5];
    final Map<String,Integer> ha = oldNF.getStackHist();
    for (int i=0;i<5;i++) oldVec[i] = (ha != null ? ha.getOrDefault(KEYS[i], 0) : 0);

    final ArrayList<Candidate> out = new ArrayList<>(near.size());
    for (Candidate c : near) {
      final NormalizedFeatures nf = c.normalizedFeatures();

      // Gate 1: call-degree band (±1)
      final boolean bandOK = degreeBandOK(oldNF, nf); // compares bucketed call-degree within ±1
      c.meta.put("gate_flattening_degreeBand", Boolean.toString(bandOK));

      // Gate 2: stack-hist cosine ≥ cosThresh (deterministic cosine; compute only if bandOK)
      boolean cosOK = false;
      if (bandOK) {
        final Map<String,Integer> hb = nf.getStackHist();
        long dot=0, na=0, nb=0;
        for (int i=0;i<5;i++) {
          final int x = oldVec[i];
          final int y = (hb != null ? hb.getOrDefault(KEYS[i], 0) : 0);
          dot += (long)x * y; na += (long)x * x; nb += (long)y * y;
        }
        cosOK = (na!=0 && nb!=0) && (dot / (Math.sqrt(na) * Math.sqrt(nb)) >= cosThresh);
      }
      c.meta.put("gate_flattening_stackCosOK", Boolean.toString(cosOK));

      // AND semantics across all gates
      if (bandOK && cosOK) out.add(c);
    }
    return out; // preserves deterministic upstream order
  }
  // [CODEGEN-END: flattening-gates-fastfail]
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#degreeBandOK`

  ```java
  // [CODEGEN] call-degree band (±1 bucket)
  private boolean degreeBandOK(NormalizedFeatures a, NormalizedFeatures b) {
    // degree = total invoke count or bucketed by kind; both sides use same bucketing
    int da = totalInvokeDegree(a.getInvokeKindCounts());
    int db = totalInvokeDegree(b.getInvokeKindCounts());
    int ba = degreeBucket(da), bb = degreeBucket(db); // e.g., 0..5 then 10+
    return Math.abs(ba - bb) <= 1;
  }
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers`

  ```java
  // WL-relaxed remains after NSF tiers; gates are ANDed; τ/margin still governs acceptance.
  // [CODEGEN] flattening-tiering
  List<Candidate> cands = nsfIndex.exact(ownerInternalName, desc, oldFeatures.getNsf64());
  if (cands.isEmpty() && flattened && nearBudget > 0) {
    cands = nsfIndex.near(ownerInternalName, desc, oldFeatures.getNsf64(), nearBudget);
    result.nearBeforeGates += cands.size();
    cands = applyFlatteningGatesIfNeeded(true, oldFeatures, cands, options.stackCosineThreshold);
    result.nearAfterGates += cands.size();
  } else if (cands.isEmpty()) {
    // baseline near behavior (≤1) handled elsewhere; no widening here
  }
  if (cands.isEmpty()) cands = wlIndex.exact(ownerInternalName, desc, wlExactToken(oldRef));
  if (cands.isEmpty()) cands = wlRelaxedCandidates(oldRef, ownerInternalName, desc);
  // scorer + τ/margin gate unchanged (deterministic)
  ```

---

### Flags & defaults

| Flag                                | Values                     |                Default | Purpose                                                             | Notes                                                    |
| ----------------------------------- | -------------------------- | ---------------------: | ------------------------------------------------------------------- | -------------------------------------------------------- |
| `--nsf-near`                        | integer ≥0                 | **2** (when flattened) | Sets widened Hamming budget for NSF near **under flattening only**. | Non-flattened flow keeps baseline near budget (≤1 or 0). |
| `--stack-cos`                       | 0.0–1.0                    |               **0.60** | Minimum cosine on 5-bin stack histogram for flattened widen gate.   | Deterministic cosine over fixed key order.               |
| `--use-nsf64`                       | canonical\|surrogate\|both |              canonical | (Phase 1) Source of truth for NSF tiers.                            | Unchanged here.                                          |
| `--wl-relaxed-l1`, `--wl-size-band` | int, frac                  |                2, 0.10 | (Phase 3) WL-relaxed gate controls.                                 | WL-relaxed still only feeds candidates.                  |

---

### Tests added/updated

* **`NonFlattenedNearSkipIT`** — On a non-flattened pair, running with `--nsf-near=2 --stack-cos=0.60` produces `near_before_gates=0`, `near_after_gates=0`; Tiny & report **byte-identical** across two runs.
* **`FlatteningGateFastFailEquivalenceTest`** — On a flattened pair, degree band evaluated first; stricter `--stack-cos=1.00` yields `near_after_gates == 0` and never increases survivors; outputs deterministic.
* **`FlatteningGatesTelemetryTest` / `FlatteningGateTelemetryTest`** — Candidate meta shows `gate_flattening_degreeBand` and `gate_flattening_stackCosOK` per candidate; run-to-run stable.
* **`Phase4FlagsParseTest`** — CLI parsing for `--nsf-near`, `--stack-cos` into options; defaults echoed in report JSON.

---

### Telemetry samples

*Run report excerpts (single-line JSON) from a flattened pair:*

```
{"candidate_stats":{...},"wl_relaxed_l1":2,"wl_relaxed_size_band":0.10,
 "flattening_detected":168,"near_before_gates":22,"near_after_gates":22,
 "wl_relaxed_gate_passes":0,"wl_relaxed_candidates":0,"wl_relaxed_hits":0,"wl_relaxed_accepted":0}
```

*Per-candidate debug (deterministic order; illustrative):*

```
[tier=nsf_near2] cand=ab#k(IIII)[I fp=nsf64 hamming=2 gate_flattening_degreeBand=true gate_flattening_stackCosOK=true
[tier=nsf_near2] cand=ac#k(IIII)[I fp=nsf64 hamming=2 gate_flattening_degreeBand=false gate_flattening_stackCosOK=false
```

*Non-flattened pair (widen flags ignored):*

```
{"candidate_stats":{...},"wl_relaxed_l1":2,"wl_relaxed_size_band":0.10,
 "flattening_detected":0,"near_before_gates":0,"near_after_gates":0,...}
```

---

### Determinism notes

* **Trigger before work:** `flattened` is computed **before** any near retrieval; widened near is skipped entirely when false.
* **Cosine determinism:** stack histogram uses fixed key order `{-2,-1,0,+1,+2}`; cosine computed on pre-baked vectors; no map-order dependence.
* **Stable ordering:** candidate lists preserve stable input order; scoring/ties use fixed lexicographic comparators; thresholds are constants or per-run options with echoed values in the report.

---

### Machine summary (JSON)

```json
{
  "flattening_widening": {
    "trigger": "detected_flattening_old_or_new",
    "near_budget": 2,
    "gates": { "call_degree_band": "±1", "stack_cos_min": 0.60, "and_semantics": true },
    "flags": { "nsf_near": 2, "stack_cos": 0.60 },
    "telemetry": true
  }
}
```
