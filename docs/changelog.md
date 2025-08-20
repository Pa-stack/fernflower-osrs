<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS CHANGELOG PHASES 1-3 BEGIN -->
# Changelog

## [Unreleased]

### Added

- **Method matcher (Phase 1):** WL-indexed candidates over DF/TDF; relaxed WL distance ≤ 1; composite scorer; abstention policy and optional refinement. Deterministic ordering.
- **Remapping (Phase 2):** `applyMappings` with TinyRemapper (default) and ASM fallback; `--verifyRemap`; deterministic repack (sorted entries, fixed timestamps).
- **Bench manifest (Phase 3):** `bench --manifest pairs.json` with deterministic metrics JSON.

### Fixed/Improved

- Hardened `MapOldNewSmokeTest`: CWD-independent I/O, explicit determinism and debug flags.
- CLI help now includes `--maxMethods`, threshold flags, and remapper options.

### Compatibility

- Java 8, Gradle 6.4.9, ASM 7.3.1. Tiny v2 is canonical mapping format.

### Notes

- WL_K=4; caches fingerprinted accordingly.
- ENIGMA format accepted as a flag but guarded at runtime (fail-fast).
<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS CHANGELOG PHASES 1-3 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component12 BEGIN -->
## [2025-08-19] Component 12 — Orchestrator, caches, deterministic mode

- Introduced an end-to-end **Orchestrator** that runs Normalize → CFG → Dom/DF/TDF → WL → Class → Method (+Refine) → Field → Write (Tiny v2).
- Added persistent **method-feature caches** keyed by normalized body hash; stored under `build/cache`.
- CLI flags: `--deterministic`, `--cacheDir`, `--idf` now supported in `mapOldNew`.
- Determinism acceptance: two runs with identical inputs produce **byte-identical** mappings.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component12 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component9 BEGIN -->
## [2025-08-19] Component 9 — Phase-3 Call-graph refinement
- Added app-only intra-class call graphs and IsoRank-style damped refinement (λ in [0.6, 0.8]).
- Caps (−0.05/+0.10) and freeze of strong base matches (S₀≥0.80, margin≥0.05) to protect WL/DF-TDF evidence.
- `methodMatch` now supports `--refine [--lambda L --refineIters K]` and prints per-iteration flips/maxΔ.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component9 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component7 BEGIN -->
# BytecodeMapper Changelog

## [2025-08-18] Component 7 — Phase-1 Class matching

- Added class fingerprints (WL top-N, micropattern histogram, counts, super/interfaces).
- Implemented deterministic greedy matcher with fixed weights and thresholding.
- CLI `classMatch` writes `old -> new score=…` mappings.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component7 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 BEGIN -->
# BytecodeMapper Changelog

## [2025-08-18] Component 1 — Core algorithms

- Standardized ASM to 7.3.1 in :mapper-core.
- Implemented ReducedCFG with stable IDs, preds/succs, exception edges (loose), and linear-chain merge.
- Implemented Dominators (CHK) and DF/TDF (Cytron) with sorted int[] sets.
- Added unit tests: single, diamond, loop, nested loops, try/catch, tableswitch; DF determinism hash.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component4-Polish BEGIN -->
## [2025-08-18] Component 4 — micropattern extractor polish

- Owner-aware extraction is now null-safe; when owner is unknown, `Recursive` and `SameName` are left unset.
- Clarified `Looping` detection to explicitly check successor edges (u→v) where `v` dominates `u`.
- Deprecated the owner-less overload in favor of the owner-aware API; Javadoc updated.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component4-Polish END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component2 BEGIN -->
## [2025-08-18] Component 2 — WL refinement

- Implemented deterministic WL relabeling:
  - Initial tuple: (degIn, degOut, domDepth, domChildren, |DF|, H(DF), |TDF|, H(TDF), loopHeader?).
	- Iterative labels with sorted multisets of predecessor/successor labels and DF/TDF hashes.
	- Method-level signature = multiset hash of final node labels plus (blockCount, loopCount).

- Added unit tests:
	- Stability under no-op reorderings within a block.
	- Different constant values yield identical WL signature when CFG is unchanged.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component2 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component2-Fix BEGIN -->
## [2025-08-18] Component 2 — WL refinement stability fix

- Hardened `Dominators`:
	- Store both index-space (`idomIndex[]`) and block-id-space (`idomIds[]`) idoms.
	- Use RPO position table in CHK `intersect` to ensure consistent convergence.
	- Implemented cycle-safe `dominates(v,u)` walking the index-space idom chain with a bounded guard.

- Patched `WLRefinement`:
	- Skip trivial self `dominates` checks and clamp iterations to [0,8] defensively.

- Result: `WLRefinementTest.differentConstantsSameWL` no longer times out; tests pass.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component2-Fix END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component3 BEGIN -->

## [2025-08-18] Component 3 — Normalizer + CFG wiring

- Added `Normalizer` with options:
	- `stripOpaquePredicates` (ICONST_0/1 + IFEQ/IFNE, constant–constant IF_ICMP*, constant-key switches),
	- `removeTrivialRuntimeExceptionWrappers` (exact NEW/DUP/[LDC]/<init>/ATHROW sequence),
	- `detectFlattening` heuristic (early big switch + high GOTO ratio) → sets `bypassDFTDF`.

- Wired `ReducedCFG.build(...)` to normalize methods before CFG construction (analysis CFG is post-normalization).
- Added tests verifying branch/switch folding, wrapper removal, and flattening detection.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component3 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component3-Fixes BEGIN -->
## [2025-08-18] Component 3 — test & formatting fixes

- Added non-folding AsmSynth variants and updated CFG tests to avoid normalization side-effects.
- Made flattening detection test deterministic (opaque disabled, higher GOTO ratio).
- Minor test lint: removed unused labels in constant switch test.
- Formatting: spaces over tabs; blank lines around headings/lists.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component3-Fixes END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component5 BEGIN -->

## [2025-08-18] Component 5 — Micropattern IDF EMA + scoring blend

- Added `IdfStore` with 12-week EMA (λ=0.9), clamp [0.5, 3.0], 4dp rounding; `.properties` save/load.
- Implemented `MicroScore.blended(a,b,idf, α_mp)` and `MicroScoringService` API.
- Added CLI hook: `printIdf --out build/idf.properties` to emit the current IDF table.
- Java 8 & Gradle 6.4.9 compatible; deterministic & idempotent.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component5 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component6 BEGIN -->

## [2025-08-18] Component 6 — Secondary signals (calls, strings, opcode)

- Implemented **Call-bag TF-IDF** (excludes `java.*`, `javax.*`) with cosine similarity and stub owner normalization.
- Implemented **String TF-IDF** (lightweight) with cosine similarity.
- Implemented **Opcode features**: histogram + cosine, and optional 2–3-gram frequencies with sparse cosine.
- Added unit tests ensuring stability: harmless reorderings leave histograms/call-bags unaffected.
- Java 8 / Gradle 6.4.9 compatible and deterministic (sorted vocabulary).

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component6 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component8 BEGIN -->

## [2025-08-18] Component 8 — Phase-2 Method matching

- Candidate generation: top-K by WL signature Hamming distance (default K=7).
- Composite score: 0.45*calls + 0.25*micro(α_mp=0.60) + 0.15*opcode + 0.10*strings + 0.05*fields (stub).
- Smart filters: Leaf vs non-Leaf penalty, Recursive mismatch penalty.
- Abstention when (best-secondBest) < 0.05 or best < τ_accept(0.60).
- CLI: `methodMatch --old ... --new ... --classMap build/classmap.txt --out build/methodmap.txt`.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component8 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component10 BEGIN -->
## [2025-08-19] Component 10 — Phase-4 Field matching (conservative)

- Added field usage extraction (reads/writes, static/instance, ordinal) in `mapper-signals`.
- Implemented conservative matcher using co-occurrence across matched methods, RW-ratio similarity, and owner consistency via class map in `mapper-cli`.
- New CLI: `fieldMatch --old --new --methodMap --out` with deterministic output and abstentions listed.

Compatibility / minor adjustments from prompt:

```diff
+ Added Main dispatcher branch for fieldMatch with AUTOGEN markers.
+ Used existing ClasspathScanner instead of non-existent AsmJar helper.
+ Implemented robust methodMap parser for lines like "owner#name(desc) -> owner#name(desc) score=…".
+ Removed an unused import/variable in FieldMatcher after initial stub to satisfy Java 8 compilation.
```
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component10 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component11 BEGIN -->
## [2025-08-19] Component 11 — Verification utilities & stats

- Added `--verifyRemap` to `applyMappings` to print pre/post class counts and a sample renamed entry for quick smoke checks.
- New CLI `tinyStats --in <mappings.tiny>` prints counts of classes/fields/methods and header namespaces.
- `mapOldNew` now supports `--debug-stats` to emit an accept/abstain summary and totals.
- Exposed method acceptance thresholds via flags: `--tauAcceptMethods`, `--marginMethods`.
- Optional smoke helpers: `--includeIdentity` to emit class identity lines and `--demoRemapCount/--demoRemapPrefix` to force a tiny visual rename overlay.
- Docs updated (runbook) with a verification workflow and PowerShell-friendly commands.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component11 END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component11 polishing BEGIN -->
### [2025-08-19] Component 11 — Polishing

- Remapper now uses **case-sensitive** deterministic ordering and writes `META-INF/MANIFEST.MF` first when present.
- `tinyStats` enhanced to display **identity vs non-identity** counts and sample pairs.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component11 polishing END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component13 BEGIN -->
## [2025-08-19] Component 13 — IR fingerprint for cache robustness

- Added `IRFingerprint` that combines `NormalizerFingerprint` and `ReducedCfgFingerprint` to robustly key method-feature cache entries.
- Updated orchestrator to include `#IRfp` in cache keys and write both normalizer and reduced-CFG option fingerprints into per-jar cache metadata.
- Runbook updated: cache key now `owner#name(desc)#normalizedBodyHash#IRfp`; determinism acceptance snippet for Windows/PowerShell added.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component13 END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component13 BEGIN -->
## [2025-08-19] Component 13 — Bench harness & metrics

- Added `bench` CLI to run mapping over consecutive jar pairs and emit JSON metrics (`churnJaccard`, `osc3Coverage`, runtimes, memory).
- Supports ablation of tie-breaker signals via `--ablate calls,micro,opcode,strings,fields,norm`.
- Deterministic by default; honors `--cacheDir` and `--idf`.
<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component13 END -->
