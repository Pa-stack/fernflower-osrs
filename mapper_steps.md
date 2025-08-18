# Agent Instructions — produce Copilot prompts to integrate the mapper into `osrs-fernflower` (Gradle 6.4.9, Java 1.8)

## Global constraints (apply to every Copilot prompt you return)

- **Workspace:** existing `osrs-fernflower` repository. Do **not** refactor existing fernflower code; add new modules/files only.
- **Build:** **Gradle 6.4.9**, **Java 1.8** (`sourceCompatibility=1.8`, `targetCompatibility=1.8`; no toolchains, no preview features).
- **Determinism:** stable, fixed-seed hashing; sorted iteration; single-thread CI mode by default.
- **Decompilation:** `osrs-fernflower` is for **manual review only**; **all features** come from **bytecode**.
- **Idempotency:** All edits must use **clearly marked begin/end comments** so prompts are safe to re-run.
- **Output:** For each step, return **one or more Copilot Chat prompts**. Each Copilot prompt must:
  1. List **exact file operations** (create/patch) with **full paths**.
  2. Include **complete file contents** in fenced code blocks.
  3. Preserve Java 8 compatibility in deps/APIs.
  4. End with **Acceptance checks** (shell commands) that must pass (e.g., `./gradlew :module:test`, `grep` on output).
- **Micropattern corrections to honor**:
  - `ArrayReader` uses `*ALOAD` opcodes (e.g., `FALOAD`) — **not** `FLOAD`.
  - `Looping` is set by **dominator back-edge** (edge `u→v` where `v` dominates `u`), not by backward offsets.
  - `Exceptions` = presence of **`ATHROW`**. (If you add “escapes method”, call it `ThrowsOut` and keep separate.)
  - Split parameters: **`α_mp`** (micropattern blend) and **`τ_accept`** (final acceptance threshold). Do **not** reuse the same symbol.
  - Freeze & document the **17-bit order**.

## Return Copilot prompts in this **order** (build-first)

### 1) Core algorithms — Reduced CFG, Dominators (CHK), DF/TDF (Cytron)

**Goal:** Replace scaffolds with working implementations + tests.

**Return Copilot prompts that:**

- Implement `mapper-core/src/main/java/.../cfg/ReducedCFG.java`
  - Build basic blocks, preds/succs, merge linear chains, stable integer ids by first instruction index.
  - Include exception edges (real ones), drop unreachable/synthetic as needed.
- Implement `mapper-core/src/main/java/.../dom/Dominators.java` (Cooper–Harvey–Kennedy)
  - Provide `idom[]`, `domDepth[]`, `children[]`, `dominates(v,u)`.
- Implement `mapper-core/src/main/java/.../df/DF.java`
  - `compute(cfg, dom)` using Cytron; store DF sets as **sorted int[]**.
  - `iterateToFixpoint(df)` to produce TDF (closure).
- Add tests under `mapper-core/src/test/java/...` covering:
  - Single block, diamond, loop, nested loops, try/catch, switch.
- **Acceptance checks**:
  - `./gradlew :mapper-core:test` must pass.
  - Determinism: run DF twice and compare serialized DF hash is identical (test prints same value both runs).

### 2) WL refinement — primary pillar

**Goal:** Deterministic WL relabeling and method signatures.

**Return Copilot prompts that:**

- Implement `mapper-core/src/main/java/.../wl/WLRefinement.java`:
  - Initial node label tuple: `(degIn,degOut,domDepth,domChildren, |DF|, hash(DF), |TDF|, hash(TDF), loopHeader?1:0)`.
  - Iterative formula with **sorted multisets** of neighbor labels; use `StableHash64`.
  - Export a method-level signature = multiset hash of final node labels + `(blockCount, loopCount)`.
- Add unit tests:
  - Stability under no-op reorderings.
  - Different constants → same WL.
- **Acceptance checks**:
  - `./gradlew :mapper-core:test` green.

### 3) Normalizer (minimal, surgical) + wire CFG path

**Goal:** Ensure all features use the **analysis CFG** (post-normalization).

**Return Copilot prompts that:**

- Implement `mapper-core/src/main/java/.../normalize/Normalizer.java`:
  - Remove trivial `RuntimeException` wrappers.
  - Strip known opaque predicates (documented patterns).
  - Optionally mark/peel obvious control-flow flattening dispatchers (or a flag to bypass DF/TDF).
- Add tests for normalization effects (before/after instruction counts, branch removal).
- Ensure `ReducedCFG.build` accepts **normalized** method body.
- **Acceptance checks**: `./gradlew :mapper-core:test`.

### 4) Micropattern extractor — finalization + tests

**Goal:** Finish `MicroPatternExtractor` with CFG-aware Looping; freeze 17-bit ABI.

**Return Copilot prompts that:**

- Update `mapper-signals/.../MicroPatternExtractor.java`:
  - `StraightLine` default true; flip false on any branch/switch.
  - `Looping`: set via **back-edge** using `Dominators`.
  - Ensure opcodes for arrays use `*ALOAD/*ASTORE`.
- Add synthetic test classes producing **one bit each**; assert exact bitset:
  - Place under `mapper-signals/src/test/java/...`
- Freeze and document bit order in `docs/bitset.md` (ensure already present info is correct).
- **Acceptance checks**:
  - `./gradlew :mapper-signals:test` green.

### 5) Micropattern IDF EMA + scoring blend

**Goal:** 12-week EMA IDF (λ=0.9), clamp [0.5,3.0], round 4dp; blended score.

**Return Copilot prompts that:**

- Finish `IdfStore` save/load and add an **EMA updater** task.
- Implement `MicroScore.blended(a,b,idf, α_mp)` and expose via a small API service.
- Add a tiny CLI hook to print current IDF table for inspection.
- **Acceptance checks**:
  - `./gradlew :mapper-signals:build`
  - `./gradlew :mapper-cli:run --args="printIdf --out build/idf.properties"` creates file.

### 6) Call-bag TF-IDF (Java 8), strings, opcode histograms

**Goal:** Secondary signals ready for scoring.

**Return Copilot prompts that:**

- Implement in `:mapper-signals`:
  - **Call-bag TF-IDF** per method (exclude `java.*`, `javax.*`), cosine similarity; owner normalization will be applied **after** class matching (stub normalization for now).
  - **String TF-IDF** (lightweight).
  - **Opcode histogram** + cosine; optional 2–3-gram frequency (keep Java-8 friendly).
- Tests to ensure stability (harmless reorderings do not swing histograms).
- **Acceptance checks**:
  - `./gradlew :mapper-signals:test`

### 7) Phase-1 Class matching (fingerprints + greedy/Hungarian)

**Goal:** Reliable class anchoring before method matching.

**Return Copilot prompts that:**

- Implement `:mapper-core` class fingerprint:
  - Histogram of **WL method signatures** (top-N), plus `(methodCount, fieldCount, super, interfaces)`, plus **micropattern class histogram** (weak).
- Implement class scoring and **deterministic greedy** (or tiny in-house Hungarian).
- Add CLI subcommand: `classMatch --old <old.jar> --new <new.jar> --out build/classmap.txt`.
- **Acceptance checks**:
  - `./gradlew :mapper-cli:run --args="classMatch --old old.jar --new new.jar --out build/classmap.txt"`
  - Output file exists; contains plausible pairs.

### 8) Phase-2 Method matching (candidates + composite scoring + abstention)

**Goal:** Composite score and thresholding.

**Return Copilot prompts that:**

- Candidate generation: for each mapped class pair, take **top-k** nearest by WL signature distance.
- Composite score:
  ```
  S_total = 0.45*S_calls
          + 0.25*S_micro(α_mp)
          + 0.15*S_opcode
          + 0.10*S_strings
          + 0.05*S_fields
  ```
- Add **smart filters** (Leaf vs non-Leaf; Recursive mismatch penalty).
- Add **abstention** if (best − secondBest) < 0.05 or `S_total < τ_accept`.
- CLI: `methodMatch --old ... --new ... --classMap build/classmap.txt --out build/methodmap.txt`.
- **Acceptance checks**:
  - `./gradlew :mapper-cli:run --args="methodMatch --old old.jar --new new.jar --classMap build/classmap.txt --out build/methodmap.txt"`
  - Output exists; summaries printed.

### 9) Phase-3 Call-graph refinement (damped reweighting)

**Goal:** Iterative neighborhood consistency without overturning strong WL evidence.

**Return Copilot prompts that:**

- Build app-only call graphs.
- Implement a damped reweighting (IsoRank-style) with λ∈[0.6,0.8] and **caps** so strong DF/TDF cannot be overturned.
- Iterate until fixpoint or max iterations; re-emit refined method map.
- CLI flag: `--refine` toggles this step in `methodMatch`.
- **Acceptance checks**:
  - Before/after scores printed; oscillation metric decreases on a small test set.

### 10) Phase-4 Field matching (conservative)

**Goal:** Field matches from usage vectors with high precision.

**Return Copilot prompts that:**

- Build per-field usage vectors from already matched methods.
- Score by co-occurrence and proximity; accept only with multiple agreeing evidences.
- CLI: `fieldMatch --old ... --new ... --methodMap build/methodmap.txt --out build/fieldmap.txt`.
- **Acceptance checks**:
  - `./gradlew :mapper-cli:run --args="fieldMatch --old old.jar --new new.jar --methodMap build/methodmap.txt --out build/fieldmap.txt"`

### 11) Real mapping I/O + remap (replace stubs)

**Goal:** Produce Tiny v2 (or Enigma), and remap new.jar.

**Return Copilot prompts that:**

- Implement Tiny writer (or integrate **SpecialSource**/**TinyRemapper** version compatible with Java 8).
- Replace `applyMappings` stub to actually remap.
- CLI:
  - `mapOldNew --old <old.jar> --new <new.jar> --out build/mappings.tiny` (runs all phases).
  - `applyMappings --inJar <new.jar> --mappings build/mappings.tiny --out build/new-mapped.jar`.
- **Acceptance checks**:
  - `./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny"`
  - `./gradlew :mapper-cli:run --args="applyMappings --inJar new.jar --mappings build/mappings.tiny --out build/new-mapped.jar"`
  - Output jar exists.

### 12) Orchestrator, caching, and determinism mode

**Goal:** End-to-end pipeline with persisted caches.

**Return Copilot prompts that:**

- Add an orchestrator that:
  - Normalizes → CFG → Dominators/DF/TDF → WL → class match → method match (+refine) → field match → write mappings.
- Add caches:
  - `(class,method,desc, normalizedBodyHash)` → `(WL sig, micropattern bitset, opcode hist)`.
  - `IdfStore` persisted to `build/idf.properties`.
- Add `--deterministic` flag (single-thread), and `--cacheDir`.
- **Acceptance checks**:
  - Two runs produce **identical** `mappings.tiny` (compare bytes).

### 13) Bench harness & metrics

**Goal:** Evaluate stability over weeks; tune weights.

**Return Copilot prompts that:**

- Add CLI `bench --in data/weeks --out build/bench.json`:
  - For ≥12 week pairs, emit: churn (Jaccard), oscillation (3-week flip), ambiguous-pair F1, runtime/mem.
- Add an **ablation mode** (toggle signals).
- **Acceptance checks**:
  - `./gradlew :mapper-cli:run --args="bench --in data/weeks --out build/bench.json"`
  - Output JSON exists; contains metrics.

### 14) VS Code tasks/launch (update if needed)

**Goal:** Make it easy to run mapping & bench.

**Return Copilot prompts that:**

- Patch `.vscode/tasks.json` & `.vscode/launch.json` to add:
  - `Mapper: mapOldNew` with args.
  - `Mapper: bench`.
- **Acceptance checks**:
  - Files exist and validate (basic `grep` checks).

### 15) Determinism tests & runbook docs

**Goal:** Lock determinism and document operations.

**Return Copilot prompts that:**

- Add tests:
  - **Determinism**: run pipeline twice; compare artifacts.
  - **Micropattern**: one test class per bit.
  - **CFG/DF/TDF**: diamonds/loops/exceptions/switch.
- Add docs:
  - `docs/scoring.md` (weights, `α_mp`, `τ_accept`, tuning rules).
  - `docs/runbook.md` (weekly ops, caches, what to check when drift spikes).
- **Acceptance checks**:
  - `./gradlew build` green; docs exist.

## Output shape reminder

For **each numbered section above**, return **one or more Copilot Chat prompts**. Each Copilot prompt must:

- Be **copy-paste ready** into Copilot Chat.
- Include exact file paths and **full file content**.
- Use **idempotent markers** for patches.
- End with **Acceptance checks** (commands) that will pass when the prompt is executed correctly.

Keep everything **Java 1.8** and **Gradle 6.4.9** compatible.
