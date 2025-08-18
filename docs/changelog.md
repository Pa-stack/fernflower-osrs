<!-- >>> AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 BEGIN -->
# BytecodeMapper Changelog

## [2025-08-18] Component 1 — Core algorithms

- Standardized ASM to 7.3.1 in :mapper-core.
- Implemented ReducedCFG with stable IDs, preds/succs, exception edges (loose), and linear-chain merge.
- Implemented Dominators (CHK) and DF/TDF (Cytron) with sorted int[] sets.
- Added unit tests: single, diamond, loop, nested loops, try/catch, tableswitch; DF determinism hash.

<!-- <<< AUTOGEN: BYTECODEMAPPER CHANGELOG Component1 END -->

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
