<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# ADR-01: Type Evidence Matching

This ADR records how we compute type-level evidence and class priors that seed the mapper. The goal is deterministic, bytecode-only matching.

Scope
- Java 1.8 bytecode only (ASM 7.3.1)
- Deterministic outputs independent of parallelism

Class prior (phase-1)
- Features: super-type/implements presence, member count buckets, constant pool stats, package-like name hints.
- Scoring uses weak cosine nudging for pruning. Fixed weights (frozen): 0.40, 0.30, 0.20, 0.10

Notes
- No decompiled source is used. We operate on normalized bytecode structures only.
- All iterations and collections are in stable, sorted order.

Pipeline positioning
- Priors are used before method candidates are expanded.

Acceptance constants referenced elsewhere
- TAU_ACCEPT=0.60
- MARGIN=0.05
