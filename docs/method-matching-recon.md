# Method Matching Recon, Gaps, and Prep (repo-grounded)

This report maps the current method-matching pipeline, calls out gaps vs. the intended design, and prepares materials to draft a deep-research prompt. It is based only on this repository’s sources and docs.

## Pipeline map (where things live)

- Candidate generation and matching
  - `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java`: candidate tiers (NSF + WL), scoring hookup, accept/abstain, diagnostics.
  - `mapper-cli/src/main/java/io/bytecodemapper/cli/method/MethodScorer.java`: composite score, weights, thresholds, penalties, normalized histogram toggle.
  - `mapper-core/src/main/java/io/bytecodemapper/core/score/CompositeScorer.java`: options container and generic combiner (used conceptually by CLI scorer).
  - `mapper-core/src/main/java/io/bytecodemapper/core/index/NsfIndex.java`: exact/near lookup by 64-bit fingerprint per owner+desc.

- Normalized features (bytecode-only, post-normalization)
  - `mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedMethod.java`: opcode bag, strings, invoked signatures, normalized descriptor, and fingerprint(s). Produces `NormalizedFeatures` with an `nsf64`.
  - `mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedFeatures.java`: container for opcode bag, call-kinds, coarse stack deltas, try/catch shape, literals sketch, strings TF, and `nsf64`.
  - Adapters used in scoring: `NormalizedAdapters` (dense[200] histogram similarity) referenced from `MethodScorer`.

- Primary analysis pillars (references in codebase)
  - WL refinement/signature: `mapper-core/.../wl/WLRefinement.java` (referenced in docs; WL_K=4 is the standard across the repo).
  - CFG/DF/TDF: `mapper-core/.../cfg/ReducedCFG.java`, `mapper-core/.../df/DF.java` (docs outline Cytron-style DF and TDF fixpoint).
  - Stable hashing: `mapper-core/src/main/java/io/bytecodemapper/core/hash/StableHash64.java` (used for nsf64 and WL hashing).

- Docs
  - `docs/scoring.md`: weights, α_mp, τ_accept, and normalized histogram notes.
  - `docs/runbook.md`: end-to-end flow, determinism, CLI flags, mapping I/O/remap, normalized feature debug.

## NormalizedMethod parity vs. intent

Implemented (confirmed in `NormalizedMethod.java`):

- Instruction processing after minimal normalization flow:
  - Attempts unwrap of whole-method RuntimeException wrappers when a known handler pattern is detected.
  - Excludes wrapper handler code and wrapper signature strings from features when not fully unwrapped.
  - Detects simple opaque guards for integer params and excludes early-exit regions.
- Features collected:
  - opcodeHistogram: integer opcode key → count (order-invariant bag).
  - stringConstants: LDC strings minus wrapper-noise.
  - invokedSignatures: owner.name+desc, plus indy tokens.
  - normalizedDescriptor: allows opaque-parameter dropping (basic implementation).
  - fingerprint (SHA-256) over: normalizedDescriptor, sorted opcode key set, sorted invoked set, sorted strings.
- NormalizedFeatures extraction scaffold:
  - opcode bag, call-kinds (VIRT/STATIC/INTERFACE/CTOR), strings TF (unweighted TF=1.0), and a StableHash64 `nsf64` built from a sorted, tagged payload of available pieces.

Gaps and stubs (need work):

- Stack delta histogram: currently empty map (no contribution).
- Try/catch shape: depth/fanout/catchTypeHash all return zeros; needs CFG/exception-aware implementation.
- Literals MinHash32: returns null; no numeric immediates sketch.
- Strings sketch: only TF is produced; IDF is applied later elsewhere, but potential normalization/tokenization policy is not captured here.
- NSFv1 vs. CLI indexing: `nsf64` emitted by `NormalizedFeatures` exists, but `MethodMatcher` builds its nsf index using a surrogate fingerprint derived from cache fields (`normFingerprint` → `normalizedBodyHash` → `normalizedDescriptor` → `sig`) hashed via StableHash64. The NSFv1 from `NormalizedFeatures` is not the source of truth for `NsfIndex` today.

Impact: With multiple sub-features stubbed, `nsf64` may be lower-information than intended. The CLI also bypasses it in favor of other fingerprints, so improvements to `NormalizedMethod` won’t affect candidate tiers unless we rewire the index to use the new `nsf64`.

## Candidate generation and tiering (current behavior)

Defined in `MethodMatcher`:

- Tiers (default CSV): `exact,near,wl,wlrelaxed`.
  - exact: same descriptor + `NsfIndex.exact(newOwner, desc, oldNsf)`.
  - near: same descriptor + `NsfIndex.near(..., hamBudget=1)` (Hamming/bit budget semantics internal to index).
  - wl: exact WL bucket: key = (desc, wlSignature) on new side.
  - wlrelaxed: same owner + same desc, then filtered by WL multiset distance ≤ 1. Note: current implementation uses a placeholder distance on identical strings, so the filter passes trivially; ordering is deterministic but not semantically informative yet.
- Dedup preserves tier order, then cap to MAX_CANDIDATES=120 with a stable tie-break.
- Deterministic iteration order (sorted owners, sorted method sigs) throughout.

Gaps:

- NSF tiers aren’t using `NormalizedFeatures.nsf64`; they rely on a cache-derived fingerprint. This limits leverage from richer normalized features.
- WL relaxed distance uses a placeholder; no real WL-token multiset strings are available at that point.

## Scoring: weights, toggles, thresholds

Defined in `MethodScorer` (CLI) and documented in `docs/scoring.md`:

- Component scores and defaults:
  - Calls TF-IDF cosine: W_CALLS=0.45
  - Micropatterns blended: W_MICRO=0.25, α_mp=0.60
  - Normalized opcode histogram cosine (dense[200]): W_NORM=0.10
  - Legacy opcode histogram (toggle off by default): LEGACY_OPCODE_ENABLED=false; W_OPCODE_LEGACY=0.05
  - Strings TF-IDF cosine: W_STR=0.10
  - Fields (stub): W_FIELDS=0.05
- Smart penalties: Leaf mismatch −0.05; Recursive mismatch −0.03
- Acceptance and margin: τ_accept=0.60; MIN_MARGIN=0.05; abstain if below either.
- Deterministic TF-IDF models are built per source set (src + candidates) to stabilize token ID order.

Notes:

- `CompositeScorer.Options` exists in core and aligns with the above knobs, but the CLI scorer is the effective implementation used.
- Weights are configurable at runtime via `MethodScorer.configureWeights(...)` and threshold setters.

## Determinism controls (end-to-end)

- Stable hashing: `StableHash64` used for WL and nsf payload hashing; `NormalizedMethod` uses SHA-256 for its string fingerprint; candidate `nsf64` in CLI built via StableHash64 over cache fingerprints.
- Sorted iteration: owners, method signatures, candidate dedup, and final outputs are sorted deterministically.
- Caps: candidate lists trimmed to a stable top-N; TF-IDF built with deterministic corpora ordering.
- CLI flags and docs (`docs/runbook.md`) emphasize `--deterministic`, stable outputs, and identical artifacts across runs.

## Gap analysis and proposed fixes

High-priority, low-risk (unblocks research value):

- Wire `NormalizedFeatures.nsf64` into `NsfIndex` as the canonical fingerprint for exact/near tiers. Keep the cache-derived fallback only when `nsf64` is absent.
- Implement WL relaxed distance properly by materializing stable WL multiset strings in cache/entries (or by deriving them from existing WL signatures) and computing L1 multiset distance.
- Fill `normalizedStackDeltaHistogram` with a coarse, ASM-computable stack change distribution (e.g., small bucketization −2..+2) to reduce ties without heavy analysis.

Medium effort (CFG-aware, still localized):

- Try/catch shape: compute depth/fanout and a stable catch-type hash using try-catch blocks and handler types present in `MethodNode` (avoid full exception CFG initially).
- Literals MinHash32: sketch numeric immediates from LDC and IntInsnNode/const opcodes, with a fixed seed and size (e.g., 64).
- Strings TF: apply a simple normalization policy (lowercase, trim long constants, drop printable non-word noise) behind a flag, to improve cosine stability on obfuscated inputs.

Integration cleanup:

- Normalize the `nsf64` path: ensure the same nsf feature drives both candidate indexing and scoring diagnostics. Document precedence and cache key impacts in `docs/runbook.md`.
- Make `LEGACY_OPCODE_ENABLED` truly orthogonal: if disabled, legacy opcode contributions should be omitted end-to-end (already mostly true).

Risks to note:

- Changing the nsf source will shift candidate buckets; guard with ablation toggles and confirm precision/coverage on a weekly benchmark set before adopting as default.

## Verification harness and experiments

Tests to add/expand:

- NormalizedMethod unit tests: wrapper unwrap (positive/negative), opaque-guard exclusion, fingerprint determinism, owner plumbed correctly for invoked signatures.
- WL relaxed distance: fixtures with known multiset deltas to validate ≤1 gating and sort order.
- Determinism: run a tiny end-to-end map twice; assert identical bytes for `mappings.tiny` and identical top-3 candidate lists for selected methods.
- CFG/DF/TDF and WL stability: keep or extend diamonds/loops/exceptions/switch tests; WL_K=4 invariant.

Bench and ablations (supports deep research):

- Use `data/weeks` to run `bench` and capture: churn (Jaccard), oscillation (3-week flip), ambiguous-pair F1, runtime/memory.
- Ablate signals: normalized on/off, legacy histogram on/off, calls/micro/strings individually. Compare coverage and precision under each setting.

## Deep-research prompt scaffold (to fill after patching)

- Objective: Improve candidate recall at fixed precision by enhancing normalized signatures and relaxed WL distance while preserving determinism.
- Context snippets to include:
  - Current tiers and where `nsf64` comes from; gaps and the proposed wiring change.
  - Exact definitions for new sub-features (stack deltas, try/catch shape, literals sketch) and how they join NSFv1.
  - WL multiset distance design and tokenization.
  - Determinism requirements and test acceptance criteria.
- Target outputs: patch list with files to modify, tests to add, acceptance checks (module tests green; bench metrics improved; determinism unchanged).

## Requirements coverage

- Repo reconnaissance: Done. Files and responsibilities enumerated.
- Feature parity for NormalizedMethod: Done. Implemented vs. stubbed listed.
- Integration points and tiering order: Done. NsfIndex wiring and tier CSV documented; WL relaxed placeholder noted.
- Scoring, thresholds, toggles: Done. Weights, α_mp, τ_accept, penalties captured.
- Determinism controls: Done. Stable hash, sorting, caps, CLI flags captured.
- Gaps and next patches: Done. Prioritized list with risks.
- Verification harness and bench plan: Done. Tests/ablations outlined.

## Notes

- Keep improvements strictly bytecode-based and post-normalization to stay aligned with DF/TDF + WL.
- Prefer deterministic, sorted payloads for any new fingerprints, and record version bumps in cache metadata as documented in the runbook.
