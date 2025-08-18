---
applyTo: '**'
---

# BytecodeMapper — Integration Guide for DF/TDF-centric Mapper in `osrs-fernflower` (Gradle 6.4.9, Java 1.8)

## Purpose (read me first)

We are integrating a **DF/TDF-centric** Java bytecode mapper into the **`osrs-fernflower`** repo as a set of new Gradle subprojects. The decompiler is for **manual review only**; all analysis uses **bytecode**. The mapper must be **deterministic**, **Java 1.8** compatible, and **build with Gradle 6.4.9**.

This document gives Copilot the **why** and **how** behind the numbered prompts that follow in Chat. Always conform to **best practices & rules** below.

---

## Non-negotiable best practices & rules

- **Build / Tooling**

  - Gradle: **6.4.9** (use/ensure wrapper).
  - Java: **1.8** (`sourceCompatibility = 1.8`, `targetCompatibility = 1.8`). **No toolchains**. **No preview features**.
  - Tests: JUnit 4.x (e.g., `junit:junit:4.13.2`).

- **Determinism**

  - Single-thread CI mode acceptable; stable results **regardless of cores**.
  - Fixed-seed stable hashing (prefer **xxHash64**; acceptable fallback: **FNV-1a 64-bit** in pure Java).
  - Stable iteration order everywhere (sort keys; aggregate features by **fixed bit order**).
  - Persist & round IDF values (4 dp) with metadata (date + git SHA).

- **Scope separation**

  - `osrs-fernflower` remains the **host repo**; **do not** wire decompiled source into features.
  - New modules: `:mapper-core`, `:mapper-signals`, `:mapper-io`, `:mapper-cli`.

- **Dependencies (Java-8 safe; pin stable)**

  - **ASM** (Tree API): prefer **ASM 7.3.1** for strict Java 8 compatibility.
    _Note_: ASM 9.x may require newer runtimes; use 7.3.1 unless told otherwise.
  - **fastutil**: e.g., `8.5.12`.
  - **Hashing**: in-house 64-bit (FNV-1a) unless xxhash **pure-Java** lib known to be Java-8 safe is introduced.
  - **Mapping**: start with **in-house Tiny-like writer**; add `mapping-io` / `TinyRemapper` later only if Java-8 safe.
  - **Hungarian**: optional; if needed, prefer small in-house deterministic implementation to avoid heavy deps.

- **Idempotent editing**
  - All file modifications use **clear begin/end markers** so prompts can re-run safely.
  - Add **acceptance checks** (CLI or Gradle commands + simple greps) at end of each prompt.

---

## Architecture & modules

- **`:mapper-core`**
  Reduced CFG (post minimal normalization), Dominators, **DF**, **TDF**, WL-style refinement, stable hashing, deterministic assignment (greedy; optional Hungarian).

- **`:mapper-signals`**
  Secondary signals (tie-breakers):

  1. Call-bag TF-IDF
  2. **Micropatterns** (17 nano-patterns; see bit order)
  3. Opcode 2–3-gram histograms/shingles
  4. String constants TF-IDF
  5. Field-usage patterns
     Includes **12-week EMA IDF** store: λ=0.9, clamp [0.5, 3.0], round 4 dp, persisted.

- **`:mapper-io`**
  Mapping I/O (Tiny/Enigma text), apply step (TinyRemapper later), serialization.

- **`:mapper-cli`**
  CLI entrypoints:
  - `mapOldNew --old path/to/old.jar --new path/to/new.jar --out build/mappings.tiny`
  - `applyMappings --inJar path/to/new.jar --mappings build/mappings.tiny --out build/new-mapped.jar`

---

## Primary pillar (never compromise)

- **DF/TDF + WL refinement over reduced CFG** is the **backbone**.
  - CFG reduction: merge linear chains; minimize noise.
  - WL labels include: `deg_in/deg_out`, `dom_depth`, `#dom_children`, `|DF(n)|`, `hash(DF(n))`, `|TDF(n)|`, `hash(TDF(n))`, `loop_header?`.
  - K iterations configurable (default 3–5). Deterministic hash.

---

## Micropatterns (nano-patterns) — frozen 17-bit order

**Bit indices are ABI; never change without version bump.**

```
0  NoParams
1  NoReturn
2  Recursive
3  SameName
4  Leaf
5  ObjectCreator
6  FieldReader
7  FieldWriter
8  TypeManipulator
9  StraightLine
10 Looping           (dominator back-edge: edge u→v where v dominates u)
11 Exceptions        (presence of ATHROW; optional OSRS flag `ThrowsOut` if escape is needed)
12 LocalReader
13 LocalWriter
14 ArrayCreator
15 ArrayReader       (uses *ALOAD opcodes, e.g., FALOAD — NOT FLOAD)
16 ArrayWriter
```

**Rules to enforce**

- **ArrayReader** uses `IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD`.
  _Do not_ confuse with local loads like `FLOAD`.
- **Looping** = **dominator back-edge** (not just backward PC offset).
- **Exceptions** = **presence of `ATHROW`** (canonical nano-pattern).
  If you need “escapes method”, add **separate** non-ABI flag `ThrowsOut`.
- **Leaf**: treat **`INVOKEDYNAMIC`** as a call.
- **SameName** noise under obfuscation is OK — **IDF** will down-weight; keep a feature-flagged cap if needed.

---

## Scoring & thresholds (defaults)

**Micropattern similarity (Phase-2):**

```
S_micro = α_mp · Jaccard(bitset) + (1 − α_mp) · Cosine(IDF-weighted)
α_mp = 0.60
```

**Composite method tie-breaker:**

```
S_total = 0.45·S_calls + 0.25·S_micro + 0.15·S_opcode + 0.10·S_strings + 0.05·S_fields
τ_accept = 0.60   # minimum score to accept a match
```

**Class prior (Phase-1):** 17-bin histograms → cosine (weak pruning / tie-nudging only).

**Hierarchy (never invert):**

1. Primary: **DF/TDF + WL**
2. Damped call-graph consistency
3. Secondary tie-breakers: calls, **micropatterns**, opcodes, strings, fields

---

## IDF policy (persisted, deterministic)

- Window: **12 weeks** with **EMA**, `λ = 0.9`.
- For pattern `p`:
  `DF_t(p) = Σ_{k=0..11} λ^k * count(p in week t-k)`
  `M_t = Σ_{k=0..11} λ^k * totalMethods_{t-k}`
  `IDF_t(p) = log((M_t + 1) / (DF_t(p) + 1)) + 1`
  Clamp to `[0.5, 3.0]`, **round 4 dp**, **persist with {date, git SHA}**.

---

## Normalization & caching

- Extract on **analysis CFG** (post minimal normalization).
- Cache key: `(classId, methodId, bytecodeHash, normalizationHash) → bitset(17)`.
- Stable method body hash: **xxHash64** (preferred) or **FNV-1a 64-bit** with fixed seed.

---

## Risk points & mitigations

- **Control-flow flattening:** detect dispatcher + uniform DF; **peel/region-recover** before extraction.
- **Constructors:** always `NoReturn`; watch for histogram skew (IDF handles it).
- **Obfuscation churn:** especially on `SameName`; rely on IDF; optional cap in cosine.

---

## Acceptance criteria & measurement

- **Build**: `./gradlew build` succeeds on **Gradle 6.4.9 + Java 1.8**.
- **CLI**: `mapOldNew` writes a Tiny-like file; `applyMappings` produces an output jar.
- **Determinism**: repeating runs on same inputs → **bit-for-bit identical** outputs.
- **Micropattern value** (kept by default if either holds):
  - **≥ 5–10% F1 lift** on ambiguous pairs at same precision; **or**
  - **≥ 10% oscillation reduction** (3-week flip rate).
- Track:
  - **AUC(S_micro)** (true matches vs top false candidates),
  - **ΔOscillation** with/without `w_micro`,
  - **Top-k stability** (k∈{1,3,5}).

---

## What NOT to do

- Don’t require Java > 1.8.
- Don’t add non-Java tools or annotation processors.
- Don’t feed **decompiled source** into features/scoring.
- Don’t change the **17-bit order**.

---

## Prompting style (for the numbered prompts)

- Each prompt must:
  - Specify **create/modify** with **full paths**.
  - Use **marker comments** for idempotent patches.
  - Provide **complete file contents** for new files.
  - End with **acceptance checks** (commands + simple output checks).
- Keep diffs **minimal**; don’t refactor fernflower code outside the new modules.

---

## Versioning notes

- If we ever need ASM 9.x, ensure runtime JDK supports it. Default here is **ASM 7.3.1** for Java-8 safety.
- If we later add `mapping-io` / `TinyRemapper`, verify Java-8 compatibility or gate behind a build flag.

---

## Quick reference (defaults)

- Weights: `w_calls=0.45`, `w_micro=0.25`, `w_opc=0.15`, `w_str=0.10`, `w_fields=0.05`
- Blend: `α_mp=0.60`
- Accept: `τ_accept=0.60`
- IDF: EMA 12w, `λ=0.9`, clamp `[0.5, 3.0]`, 4 dp persisted
- Bit order: **frozen** (see table above)
- Hash: **xxHash64** (or **FNV-1a 64**) fixed seed

---

**End of `main.instructions.md`.**
