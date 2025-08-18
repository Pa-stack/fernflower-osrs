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
