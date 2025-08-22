<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# ADR-04: IsoRank-style Call-Graph Refinement

Goal
Improve candidate stability via neighbor-consistency on call graphs after initial DF/TDF + WL + secondary scoring. Refinement is optional, deterministic, and bounded.

Frozen parameters (verbatim)
- REFINE_BETA=0.70
- CAPS=[-0.05,+0.10]
- FREEZE=0.80
- MAX_ITERS=10
- EPS=1e-3

Rationale
- beta mixes structural similarity with prior scores.
- caps prevent runaway amplification; negative cap allows slight down-weighting, positive cap permits limited boost.
- freeze threshold stops updates when a candidate already has high confidence, improving determinism and runtime.

Procedure
1. Build bipartite method graph with edges weighted by prior score.
2. Iterate similarity propagation with damping (beta) and per-iteration clamping (caps).
3. Freeze entries ≥ FREEZE; stop at MAX_ITERS or if max delta < EPS.
4. Re-rank candidates by refined score; respect TAU_ACCEPT and MARGIN as outer acceptance guards.

Determinism
- Input ordering is lexicographically sorted by owner/name/desc; tie-breaking stable.
- Single-threaded by default; optional `--deterministic` flag is recommended in pipelines.
