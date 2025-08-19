# Agent Instructions — return Copilot prompts for Steps 10.5 and 11–15 (Gradle 6.4.9, Java 1.8, ASM 7.3.1)

## Global guardrails (apply to every Copilot prompt you return)

- **Workspace:** existing `osrs-fernflower` monorepo; **do not refactor** existing fernflower sources. Add new modules/files only.
- **Tooling:** **Gradle 6.4.9**, **Java 1.8**, **ASM 7.3.1** (`asm`, `asm-tree`, `asm-commons`). No toolchains, no preview features.
- **Determinism:** stable iteration order; fixed-seed hashing; CI single-thread mode toggle.
- **Idempotency:** All patches must use **clear AUTOGEN begin/end markers** so prompts are safe to re-run.
- **Output requirements for each Copilot prompt you return:**
  1. Exact file ops (full paths), create/patch instructions.
  2. **Complete file contents** in fenced code blocks.
  3. Strict Java-8-safe APIs and dependencies only.
  4. **Acceptance checks** (shell commands) that pass (e.g., `./gradlew :module:test`, `grep` checks, existence checks).
- **Micropattern rules to keep consistent:** `ArrayReader = *ALOAD`, `Looping = dominator back-edge`, `Exceptions = ATHROW`. Distinguish **α_mp** (blend) vs **τ_accept** (threshold). The 17-bit order is frozen and documented.
- **Decompiler context:** `osrs-fernflower` (OSRS build) is **for manual review only**; all analysis is **bytecode-based**.

---

## 10.5) **NormalizedMethod** integration — generalized bytecode histogram & fingerprint

**Goal:** Add a normalized per-method feature extractor that (a) unwraps RuntimeException wrappers, (b) excludes noisy wrapper strings & opaque predicate guard code, (c) builds a **generalized opcode histogram**, and (d) produces a **stable fingerprint**. Use it to **replace/augment** the existing opcode histogram signal and to feed strings/call-bags.

**Return Copilot prompts that:**

- Create a **`NormalizedMethod`** feature with:
  - Inputs: `ownerInternalName`, `MethodNode` (normalized body), optional opaque param set (stub ok now).
  - Steps: shallow clone → try unwrap whole RuntimeException wrapper → collect wrapper signature strings (to exclude) → detect opaque predicate guard code and obfuscation wrapper blocks (exclude set) → **process instructions** to build:
    - `opcodeHistogram` (generalized bytecode histogram),
    - `stringConstants` (excluding wrapper strings),
    - `invokedSignatures` (owner.name+desc),
    - `normalizedDescriptor` (handles opaque param drop; stub ok now),
    - stable **SHA-256** fingerprint over the sorted sets + descriptor.
- **Plumb owner** into extractor (since `MethodNode` lacks it) and ensure it’s wired from `ClassNode.name`.
- Expose an adapter in `:mapper-signals` to **replace the previous opcode histogram feature** with the generalized histogram. Update scoring config to either:
  - **Unify** under existing `W_OPC=0.15` using the normalized histogram, **or**
  - Split: `W_NORM=0.10` (generalized histogram) and `W_OPC=0.05` (legacy), with a toggle to disable legacy by default.
- Add **unit tests**:
  - Wrapper unwrap cases (both unwrap and non-unwrap branches).
  - Opaque guard removal lowers histogram weight for excluded code.
  - Fingerprint determinism (same bytes across runs).
  - Owner-plumbing sanity (invoked signatures include correct owner).
- Add **CLI debug flag**: `--debug-normalized` to dump, for a sample of methods, the normalized descriptor, top-N opcodes, top strings, and fingerprint to `build/normalized_debug.txt`.
- **Docs:** Update `docs/runbook.md` to describe how NormalizedMethod interacts with the Normalizer and how it feeds scoring; add a short section in `docs/scoring.md` on generalized histogram weight.
- **Acceptance checks:**
  - `./gradlew :mapper-signals:test`
  - `./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny --debug-normalized"`
    Verify `mapper-cli/build/normalized_debug.txt` exists and contains opcodes/strings/fingerprints.
  - Re-run the pipeline twice and confirm identical fingerprints in the debug file (grep/compare).

---

## 11) Real mapping I/O + remap (Tiny v2 default) — replace stubs

**Goal:** Produce **Tiny v2** mappings and actually **remap** the JAR. Use **asm-commons** now; allow switching to **SpecialSource**/**TinyRemapper** later (Java-8-safe versions only).

**Return Copilot prompts that:**

- Add `org.ow2.asm:asm-commons:7.3.1` to the needed modules’ `build.gradle` (inside AUTOGEN markers).
- Implement a Tiny v2 writer (class/method/field lines with 2 namespaces: `obf` and `deobf`) and wire it into `mapOldNew`.
- Implement `applyMappings` that performs a real **remap** (choose one path):
  - A) `asm-commons` `ClassRemapper` pass (safe for simple remaps), or
  - B) Integrate **SpecialSource** / **TinyRemapper** (Java-8-compatible release) for byte-for-byte remap.
- Ensure **owner remapping** lines up with the class map from Phase-1, and that method/field maps apply consistently.
- **Acceptance checks:**
  - `./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny"`
  - `./gradlew :mapper-cli:run --args="applyMappings --inJar new.jar --mappings build/mappings.tiny --out build/new-mapped.jar"`
  - Confirm output jar exists and class names reflect the mapping (basic `jar tf`/`grep` check).

---

## 12) Orchestrator, persistent caches, deterministic mode

**Goal:** Wire an end-to-end orchestrator (normalize → CFG → Dom/DF/TDF → WL → Class → Method (+Refine) → Field → Write) with **persistent caches** and a **deterministic** switch.

**Return Copilot prompts that:**

- Add an **Orchestrator** that:
  - Uses the **Normalizer** first.
  - Reuses caches keyed by `normalizedBodyHash`: `(WL signature, micropattern bitset, NormalizedMethod histogram/fingerprint, opcode hist, strings, call-bag)`.
  - Runs phases in a deterministic order with explicit sorting before hash/serialize.
- Add CLI flags:
  - `--deterministic` → force single-thread execution and ordered collectors.
  - `--cacheDir <dir>` → persist caches across runs; default `build/cache`.
  - `--idf <path>` → load/save IDF table; default `build/idf.properties`.
- Ensure **two runs** with identical inputs and caches produce **byte-identical** `mappings.tiny`.
- **Acceptance checks:**
  - Run orchestrated `mapOldNew` twice, compare `mappings.tiny` bytes: `cmp -s … || exit 1`.
  - Verify caches created under `build/cache` and IDF persisted under `build/idf.properties`.

---

## 13) Bench harness & metrics (+ ablations)

**Goal:** Add a **benchmark runner** over ≥12 weekly pairs with churn/oscillation/F1 metrics, plus **signal ablations** (e.g., disable NormalizedMethod vs. enable).

**Return Copilot prompts that:**

- CLI: `bench --in data/weeks --out build/bench.json [--manifest pairs.json] [--ablations calls,micro,normalized,opcode,strings,fields]`.
- Default folder layout: `data/weeks/<week>/old.jar` and `.../new.jar`. Manifest wins if provided.
- Metrics:
  - **Churn** (Jaccard) week-to-week,
  - **Oscillation** (3-week flip rate),
  - **Ambiguous-pair F1** on a small labeled set (allow a CSV with gold pairs),
  - Runtime, peak memory (coarse).
- Emit a single JSON; optionally emit CSVs per metric.
- **Acceptance checks:**
  - `./gradlew :mapper-cli:run --args="bench --in data/weeks --out build/bench.json"`
  - Verify file exists and contains the metric keys. Basic `grep '"oscillation"'` should succeed.

---

## 14) VS Code tasks & launch (update, don’t replace)

**Goal:** Patch VS Code configs inside AUTOGEN markers to make mapping and bench easy to run.

**Return Copilot prompts that:**

- Update `.vscode/tasks.json` to add:
  - `Mapper: mapOldNew` task (with args),
  - `Mapper: applyMappings`,
  - `Mapper: bench`,
  - `Mapper: printIdf` (optional).
- Update `.vscode/launch.json` to launch `mapper-cli` with `mapOldNew` defaults.
- Preserve user entries; patch **only within AUTOGEN markers**.
- **Acceptance checks:**
  - Confirm both files exist; `grep` AUTOGEN sections; optionally run the tasks.

---

## 15) Determinism tests & runbook docs

**Goal:** Lock determinism and document day-to-day ops.

**Return Copilot prompts that:**

- Add **tests**:
  - End-to-end determinism: run `mapOldNew` twice on a small pair and compare mapping bytes.
  - Collect existing micropattern per-bit tests under a suite; ensure they’re invoked by default.
  - Expand CFG/DF/TDF tests (diamonds, nested loops, try/catch, switch).
- **Docs**:
  - `docs/scoring.md`: final weights (calls 0.45, micro 0.25, normalized/opcode 0.15 (split as configured), strings 0.10, fields 0.05), α_mp, τ_accept, tuning rules.
  - `docs/runbook.md`: weekly workflow, cache hygiene, what to check if drift spikes (flattening detector, IDF health, abstentions), and how to toggle ablations.
  - `docs/changelog.md`: append a dated “Changes” section for each component delivered.
- **Acceptance checks:**
  - `./gradlew build` green.
  - Docs exist and contain expected sections (simple `grep` checks).

---

### Notes to keep consistent across the returned Copilot prompts

- Use **ASM 7.3.1** everywhere; include **asm-commons** where remapping is done.
- Ensure **owner passing** through all extractors (micropatterns and NormalizedMethod).
- Keep NormalizedMethod features strictly **bytecode-sourced** and **post-Normalization**.
- When integrating NormalizedMethod, **retire** the older opcode histogram path or keep it behind a flag—default to the generalized histogram.
