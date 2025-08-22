<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# ADR-03: Multi-signal Scoring

Overview
The mapper scores method candidates using a frozen linear blend of secondary signals after DF/TDF + WL produce candidates. All iteration order is sorted and deterministic.

Frozen constants (verbatim)
- TAU_ACCEPT=0.60
- MARGIN=0.05
- ALPHA_MICRO=0.60
- WEIGHTS_METHOD={calls:0.45, micro:0.25, opcode:0.15, strings:0.10}

Class-level priors (weak)
- Weights: 0.40, 0.30, 0.20, 0.10

Method score definition
S_total = 0.45·S_calls + 0.25·S_micro + 0.15·S_opcode + 0.10·S_strings
S_micro = ALPHA_MICRO · Jaccard(bitset) + (1 − ALPHA_MICRO) · Cosine(IDF-weighted)

IDF policy (EMA)
- EMA_LAMBDA=0.90
- IDF_CLAMP=[0.5,3.0]
- ROUND=4dp

Acceptance
- Accept if S_total ≥ TAU_ACCEPT; otherwise leave unmatched.
