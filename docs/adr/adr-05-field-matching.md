<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# ADR-05: Field Matching

Objective
Deterministically match fields after methods are mapped, using usage vectors from bytecode.

Usage vector definition
- For each field, compute a vector over reader/writer contexts, owner-method micro-bits, opcode grams around the access, and class priors.

Scoring and thresholds (frozen)
- MIN_SUPPORT=3
- MIN_MARGIN=2
- τ_ratio=0.60

Process
1. Collect candidate pairs from method mappings and type evidence.
2. Score by overlap and context cosine; enforce MIN_SUPPORT and MIN_MARGIN.
3. Accept if ratio ≥ τ_ratio; otherwise leave unmapped.
