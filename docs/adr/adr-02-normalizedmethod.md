<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# ADR-02: NormalizedMethod extraction

Purpose
- Define method-body normalization for analysis-only features (no decompiler source).

Normalization steps (bytecode-level)
- Strip NOPs and trivial GOTO-chains; merge linear blocks (ReducedCFG).
- Canonicalize try/catch ordering and handler ranges; stable label ordering.
- Whole-method RuntimeException unwrap: if the only instruction path ends with `ATHROW` of a newly-created `RuntimeException` that wraps another throwable, unwrap to inner cause for exception-shape features.
- Early guard exclusion shape: trivial parameter-null guards that immediately throw are excluded from opcode n-gram stats to reduce noise; matching is based on a simple pattern at entry block.

Tokenization for n-grams
- Opcode bigrams/trigrams are computed over the normalized instruction stream, excluding labels/frames/line-numbers.
- Constants and owner/name/desc of invokes are projected to category tokens to improve stability.

Outputs
- Stable per-method hash (StableHash64: FNV-1a 64-bit fixed seed) over the normalized bytecode.
- Feature vectors (micropatterns, opcode n-grams) and CFG metadata for DF/TDF.
