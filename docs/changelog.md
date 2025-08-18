<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 BEGIN -->
# BytecodeMapper Changelog

## [2025-08-18] Component 1 — Core algorithms

- Standardized ASM to 7.3.1 in :mapper-core.
- Implemented ReducedCFG with stable IDs, preds/succs, exception edges (loose), and linear-chain merge.
- Implemented Dominators (CHK) and DF/TDF (Cytron) with sorted int[] sets.
- Added unit tests: single, diamond, loop, nested loops, try/catch, tableswitch; DF determinism hash.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 END -->
