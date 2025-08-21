# Phase 3 — Flattening-aware widening and gates

## Files & snippets

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#detectFlattening`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // Detect flattening on either side before computing near budget
  boolean anyNewFlattened = newSideAnyFlattenedForOwnerDesc(newClasses, newOwner, desc);
  boolean flattened = oldFlattened || anyNewFlattened;
  if (flattened) out.flatteningDetected++;
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#nearBudgetWidening`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // Compute widened near budget when flattened
  final int nearBudget = flattened ? Math.max(1, options.nsfNearBudgetWhenFlattened) : 1;
  // ... later, when applying near tier
  if (flattened && hamBudget > 1 && !candsNear.isEmpty()) {
      out.nearBeforeGates += candsNear.size();
      candsNear = applyFlatteningGatesIfNeeded(true, oldNormFeatures, candsNear,
              options.stackCosineThreshold, newClasses, newOwner, desc);
      out.nearAfterGates += candsNear.size();
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#applyFlatteningGatesIfNeeded`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // Fast-fail AND gates: degree band first, then cosine over fixed 5-bin stack histogram
  private static java.util.ArrayList<NewRef> applyFlatteningGatesIfNeeded(boolean flattened,
                             NormalizedFeatures oldNF,
                             java.util.ArrayList<NewRef> near,
                             double cosThresh,
                             java.util.Map<String, org.objectweb.asm.tree.ClassNode> newClasses,
                             String newOwner,
                             String desc) {
    if (!flattened || near == null || near.isEmpty()) return near;
    java.util.ArrayList<NewRef> out = new java.util.ArrayList<NewRef>(near.size());
    // Precompute old stack vector in fixed 5-key order to avoid repeated lookups
    final String[] KEYS = new String[]{"-2","-1","0","+1","+2"};
    final int[] oldVec = new int[5];
    java.util.Map<String,Integer> ha = (oldNF==null?null:oldNF.getStackHist());
    for (int i=0;i<5;i++) oldVec[i] = (ha != null && ha.get(KEYS[i]) != null) ? ha.get(KEYS[i]).intValue() : 0;

    // Cache per-candidate NormalizedFeatures deterministically by name
    org.objectweb.asm.tree.ClassNode ncn = newClasses.get(newOwner);
    for (NewRef c : near) {
      NormalizedFeatures nf = null;
      if (ncn != null) {
        org.objectweb.asm.tree.MethodNode nmn = findMethod(ncn, c.name, desc);
        if (nmn != null) {
          try {
            NormalizedMethod nm = new NormalizedMethod(newOwner, nmn, java.util.Collections.<Integer>emptySet());
            nf = nm.extract();
          } catch (Throwable ignore) { nf = null; }
        }
      }
      // Fast-fail: check degree band first (integer math)
      boolean bandOK = degreeBandOK(oldNF, nf);
      c.meta.put("gate_flattening_degreeBand", java.lang.Boolean.toString(bandOK));

      boolean cosOK = false;
      if (bandOK) {
        // Compute cosine only when degree band passes
        java.util.Map<String,Integer> hb = (nf==null?null:nf.getStackHist());
        long dot = 0L, na = 0L, nb = 0L;
        for (int i=0;i<5;i++) {
          int x = oldVec[i];
          int y = (hb != null && hb.get(KEYS[i]) != null) ? hb.get(KEYS[i]).intValue() : 0;
          dot += (long)x * (long)y; na += (long)x * (long)x; nb += (long)y * (long)y;
        }
        cosOK = (na != 0L && nb != 0L) && (dot / (Math.sqrt(na) * Math.sqrt(nb)) >= cosThresh);
      }
      c.meta.put("gate_flattening_stackCosOK", java.lang.Boolean.toString(cosOK));
      if (bandOK && cosOK) out.add(c);
      // No reordering: preserve input order which is already deterministic
    }
    return out;
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#degreeBandOK`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  private static boolean degreeBandOK(NormalizedFeatures a, NormalizedFeatures b) {
      int A = degreeBucket(callDegree(a));
      int B = degreeBucket(callDegree(b));
      return Math.abs(A - B) <= 1;
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // Apply widened near only when flattened; record telemetry around gates
  if ("near".equals(t)) {
    int hamBudget = nearBudget;
    // ... query near ...
    if (flattened && hamBudget > 1 && !candsNear.isEmpty()) {
      out.nearBeforeGates += candsNear.size();
      candsNear = applyFlatteningGatesIfNeeded(true, oldNormFeatures, candsNear,
          options.stackCosineThreshold, newClasses, newOwner, desc);
      out.nearAfterGates += candsNear.size();
    }
  }
  ```

---

## Flags & defaults

| Flag                                | Values                     |                Default | Purpose                                                             | Notes                                                    |
| ----------------------------------- | -------------------------- | ---------------------: | ------------------------------------------------------------------- | -------------------------------------------------------- |
| `--nsf-near`                        | integer ≥0                 | 2 (flattened only) | Widened Hamming budget for NSF near under flattening only. | Non-flattened flow uses baseline 1. |
| `--stack-cos`                       | 0.0–1.0                    | 0.60 | Minimum cosine on 5-bin stack histogram for flattened widen gate.   | Deterministic cosine over fixed key order.               |
| `--use-nsf64`                       | canonical\|surrogate\|both |              canonical | (Phase 1) Source of truth for NSF tiers.                            | Unchanged here.                                          |
| `--wl-relaxed-l1`, `--wl-size-band` | int, frac                  |                2, 0.10 | (Phase 3) WL-relaxed gate controls.                                 | WL-relaxed still only feeds candidates.                  |

---

## Tests present

* `mapper-cli/src/test/java/io/bytecodemapper/core/match/NonFlattenedNearSkipIT.java` — `near_before_gates=0`, `near_after_gates=0` on non-flattened.
* `mapper-cli/src/test/java/io/bytecodemapper/core/match/FlatteningGateFastFailEquivalenceTest.java` — degree band first; stack-cos controls survivors.
* `mapper-cli/src/test/java/io/bytecodemapper/core/match/FlatteningGatesTelemetryTest.java` and `FlatteningGateTelemetryTest.java` — counters and meta flags stable.
* `mapper-cli/src/test/java/io/bytecodemapper/cli/Phase4FlagsParseTest.java` — CLI parsing for `--nsf-near`, `--stack-cos`.

---

## Telemetry fields (report JSON)

*Run report excerpts (single-line JSON) from a flattened pair:*

```json
{"candidate_stats":{...},"wl_relaxed_l1":2,"wl_relaxed_size_band":0.10,
 "flattening_detected":168,"near_before_gates":22,"near_after_gates":22,
 "wl_relaxed_gate_passes":0,"wl_relaxed_candidates":0,"wl_relaxed_hits":0,"wl_relaxed_accepted":0}
```

*Per-candidate debug (deterministic order; illustrative):*
Removed: no such text output exists in the code; per-candidate gate flags are stored in `meta` and used by tests.

*Non-flattened pair (widen flags ignored):*

```json
{"candidate_stats":{...},"wl_relaxed_l1":2,"wl_relaxed_size_band":0.10,
 "flattening_detected":0,"near_before_gates":0,"near_after_gates":0,...}
```

---

## Determinism notes

* **Trigger before work:** `flattened` is computed **before** any near retrieval; widened near is skipped entirely when false.
* **Cosine determinism:** stack histogram uses fixed key order `{-2,-1,0,+1,+2}`; cosine computed on pre-baked vectors; no map-order dependence.
* **Stable ordering:** candidate lists preserve stable input order; scoring/ties use fixed lexicographic comparators; thresholds are constants or per-run options with echoed values in the report.

---

## Machine summary (JSON)

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

---

Acceptance checks (optional):

* Run the task "Mapper: mapOldNew" (it enables deterministic mode and writes a report) and confirm in the report JSON:
  * Keys: `flattening_detected`, `near_before_gates`, `near_after_gates` are present.
  * On non-flattened samples, widened-near counters remain 0.
