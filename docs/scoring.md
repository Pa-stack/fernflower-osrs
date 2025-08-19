<!-- >>> AUTOGEN: BYTECODEMAPPER DOC scoring NormalizedMethod BEGIN -->

### Generalized opcode histogram (NormalizedMethod)

We include a **generalized histogram** built from `NormalizedMethod` with weight:

- `W_NORM = 0.10` (default), cosine on dense[200].

The legacy raw opcode histogram remains available behind a toggle:

- `LEGACY_OPCODE_ENABLED=false`; if enabled, `W_OPCODE_LEGACY = 0.05`.

These do **not** override DF/TDF+WL. They only participate in Phase-2 tie-breaking.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC scoring NormalizedMethod END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC scoring BEGIN -->
# Scoring Parameters

Micropattern similarity:
S_micro = α_mp · Jaccard(bitset) + (1 − α_mp) · Cosine(IDF-weighted)

Defaults:
- α_mp = 0.60
- Weights: calls 0.45, micro 0.25, opcodes 0.15, strings 0.10, fields 0.05
- Acceptance threshold: τ_accept = 0.60

Micropatterns are **tie-breakers**; DF/TDF+WL are primary.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC scoring END -->
