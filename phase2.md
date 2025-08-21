### Files & snippets

* `mapper-core/src/main/java/io/bytecodemapper/core/index/WlIndex.java#bagFor`

  ```java
  // Final-iteration WL token bag as a sorted multiset (token -> count), cached per method
  // [CODEGEN] wl-bag-materialize-cache
  public final class WlIndex {
    private final Object2ObjectOpenHashMap<MethodRef, Long2IntOpenHashMap> bagByMethod = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<MethodRef, long[]> sortedKeysByMethod = new Object2ObjectOpenHashMap<>();

    public Long2IntOpenHashMap bagFor(MethodRef mref) {
      Long2IntOpenHashMap bag = bagByMethod.get(mref);
      if (bag != null) return bag;
      // 'tokens' are final-iteration labels produced by the WL pass (K fixed in normalizer)
      LongList tokens = wlFinalIterationTokens(mref); // deterministic source
      Long2IntOpenHashMap b = new Long2IntOpenHashMap(tokens.size());
      for (int i = 0; i < tokens.size(); i++) {
        long t = tokens.getLong(i);
        b.addTo(t, 1);
      }
      bagByMethod.put(mref, b);
      // keep a sorted key array for deterministic iteration in distance calculations
      long[] keys = b.keySet().toLongArray();
      Arrays.sort(keys);
      sortedKeysByMethod.put(mref, keys);
      return b;
    }

    public long[] sortedKeys(MethodRef mref) {
      long[] keys = sortedKeysByMethod.get(mref);
      if (keys != null) return keys;
      bagFor(mref); // populates both maps
      return sortedKeysByMethod.get(mref);
    }
  }
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#l1Distance`

  ```java
  // Deterministic L1 distance over merged sorted keys
  // [CODEGEN] wl-l1-distance
  static int l1Distance(Long2IntOpenHashMap a, long[] aKeys, Long2IntOpenHashMap b, long[] bKeys) {
    int d = 0;
    int i = 0, j = 0;
    while (i < aKeys.length || j < bKeys.length) {
      long ka = (i < aKeys.length ? aKeys[i] : Long.MAX_VALUE);
      long kb = (j < bKeys.length ? bKeys[j] : Long.MAX_VALUE);
      if (ka == kb) {
        d += Math.abs(a.get(ka) - b.get(kb));
        i++; j++;
      } else if (ka < kb) {
        d += a.get(ka);
        i++;
      } else { // kb < ka
        d += b.get(kb);
        j++;
      }
    }
    return d;
  }
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#wlRelaxedCandidates`

  ```java
  // WL-relaxed gate: L1 ≤ N AND size-band ±10%; feeds candidates only (no auto-accept)
  // [CODEGEN] wl-relaxed-gate
  private List<Candidate> wlRelaxedCandidates(MethodRef oldRef, String owner, String desc) {
    final int l1Tau = options.wlRelaxedL1;      // default 2
    final double band = options.wlSizeBand;     // default 0.10

    Long2IntOpenHashMap oldBag = wlIndex.bagFor(oldRef);
    long[] oldKeys = wlIndex.sortedKeys(oldRef);
    final int oldSum = oldBag.values().intStream().sum();

    List<Candidate> out = new ArrayList<>();
    for (MethodRef cand : wlIndex.allInOwnerDesc(owner, desc)) {
      Long2IntOpenHashMap nb = wlIndex.bagFor(cand);
      long[] nk = wlIndex.sortedKeys(cand);
      int newSum = nb.values().intStream().sum();

      // size-band check: within ±10% (configurable)
      int minSize = (int) Math.floor((1.0 - band) * oldSum);
      int maxSize = (int) Math.ceil((1.0 + band) * oldSum);
      if (newSum < minSize || newSum > maxSize) continue;

      int l1 = l1Distance(oldBag, oldKeys, nb, nk);
      if (l1 <= l1Tau) {
        out.add(Candidate.from(cand).withMeta("wl_relaxed_l1", l1));
      }
    }

    // Deterministic ordering: lower L1 first, then lexicographic (owner,name,desc)
    out.sort((a,b) -> {
      int la = (int) a.metaInt("wl_relaxed_l1", Integer.MAX_VALUE);
      int lb = (int) b.metaInt("wl_relaxed_l1", Integer.MAX_VALUE);
      if (la != lb) return Integer.compare(la, lb);
      return Candidate.BY_LEX.compare(a, b);
    });

    return out;
  }
  ```

* `mapper-core/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers`

  ```java
  // Tier order unchanged; WL-relaxed only contributes candidates. Acceptance still uses τ & MIN_MARGIN.
  // [CODEGEN] wl-relaxed-tiering-nonbypass
  List<Candidate> cands = nsfIndex.exact(owner, desc, oldNF.getNsf64());
  if (cands.isEmpty()) cands = nsfIndex.near(owner, desc, oldNF.getNsf64(), nearBudget);
  if (cands.isEmpty()) cands = wlIndex.exact(owner, desc, wlExactToken(oldRef));
  if (cands.isEmpty()) cands = wlRelaxedCandidates(oldRef, owner, desc);

  // score + calibrate; τ_accept / MIN_MARGIN gate remain authoritative
  Scored top = scorer.rank(oldNF, cands);         // deterministic scorer
  if (top.score >= TAU_ACCEPT && (top.score - top.nextBest) >= MIN_MARGIN) {
    accept(top); // WL-relaxed cannot bypass this: it's a feeder only
  } else {
    abstain(oldRef);
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/cli/MapOldNew.java#flags`

  ```java
  // WL-relaxed flags
  // [CODEGEN] wl-flags
  @Option(names="--wl-relaxed-l1", paramLabel="N", description="WL-relaxed L1 threshold (default 2)")
  public void setWlRelaxedL1(int n) { matcherOptions.wlRelaxedL1 = n; }

  @Option(names="--wl-size-band", paramLabel="F", description="WL-relaxed size-band fraction (default 0.10 for ±10%)")
  public void setWlSizeBand(double f) { matcherOptions.wlSizeBand = f; }
  ```

---

### Flags & defaults

| Flag              | Values            |  Default | Purpose                                                     |
| ----------------- | ----------------- | -------: | ----------------------------------------------------------- |
| `--wl-relaxed-l1` | integer ≥ 0       |    **2** | Sets the L1 threshold `N` for WL-relaxed gate (`L1 ≤ N`).   |
| `--wl-size-band`  | fraction ∈ \[0,1] | **0.10** | Enforces bag-size band: new bag count within ±(100×value)%. |

---

### Tests added/updated

* **`WLBagsDeterminismTest`** — WL token bag materialization is deterministic: identical key sets & counts over repeated runs; keys are sorted.
* **`WLRelaxedDeltaFixturesTest`** — unit fixtures for known bag deltas (0/1/2/3):

  * `Δ=0,1,2` → gate passes with default `N=2`; `Δ=3` → gate fails.
  * Also verifies size-band behavior at ±10%.
* **`WLRelaxedDefaultsReportTest`** — running without flags reports `wl_relaxed_l1=2`, `wl_relaxed_size_band=0.10`; overrides are echoed.
* **`WLRelaxedCountersIT`** — integration: WL-relaxed contributes candidates (`wl_relaxed_gate_passes`, `wl_relaxed_candidates` increase) without changing acceptance unless τ/margin is satisfied; repeated runs are byte-identical.

---

### Determinism notes

* **Token bag determinism:** bags built from the final WL iteration; keys are cached **sorted** (`long[]`) and used for all distance iterations.
* **Distance determinism:** L1 merges **sorted** key arrays; no hash-iteration order.
* **Candidate determinism:** WL-relaxed outputs sorted by `(L1 asc, owner/name/desc lex)`; acceptance still gated by fixed `TAU_ACCEPT` and `MIN_MARGIN`.

---

### Machine summary (JSON)

```json
{
  "wl_relaxed": {
    "token_bag": "final_iteration_sorted_multiset",
    "distance": "L1",
    "threshold_default": 2,
    "size_band_percent": 10,
    "gate_only": true,
    "flags": { "wl_relaxed_l1": 2 }
  }
}
```
