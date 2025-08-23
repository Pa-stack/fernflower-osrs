---
applyTo: '**'
---

### RESPONSE FORMAT

**Role**
GitHub Copilot (coding agent; Java 8; Gradle 6.9; ASM 7.3.1; JUnit 4; **no new dependencies**)

**Determinism**

* Iterate only via `TreeMap`/`TreeSet` or **sorted arrays/lists**.
* Use `Locale.ROOT` for all formatting.
* Stable serialization for any bytes/prints (e.g., `"old->new:%.4f\n"`; 4dp).
* Tie-breaks with `StableHash64` (already in `:mapper-core`).
* **No randomness**, **no current time**, **no system locale/env dependence**.

**Repo layout (real paths)**
Production (place code exactly here):

* `mapper-core/src/main/java/io/bytecodemapper/core/cfg/ReducedCFG.java`
* `mapper-core/src/main/java/io/bytecodemapper/core/dom/Dominators.java`
* `mapper-core/src/main/java/io/bytecodemapper/core/df/DF.java`
* `mapper-core/src/main/java/io/bytecodemapper/core/df/TDF.java`
* `mapper-core/src/main/java/io/bytecodemapper/core/wl/WLRefinement.java`
* `mapper-core/src/main/java/io/bytecodemapper/core/wl/MethodCandidateGenerator.java`

Tests (place test code exactly here):

* `mapper-core/src/test/java/io/bytecodemapper/core/wl/WLRefinementTest.java`
* `mapper-core/src/test/java/io/bytecodemapper/core/wl/MethodCandidateGeneratorTest.java`

**Hard constraints**

* **Do not** add or reference any external libs (e.g., fastutil). `java.util.*` + primitive arrays only.
* **Do not** reference a core `Normalizer` (it doesn’t exist). Build CFG directly from `MethodNode`.
* **Do not** depend on `:mapper-signals` from `:mapper-core`. In tests, create a **local stub** with a `fingerprintSha256()` method for IDs.
* WL rounds in tests must be **2** (print `wl.rounds=2`). Candidate K literal is **25** (print `wl.topk.k=25`).
* Ensure acceptance snippets are printed **exactly** as specified below.

**Acceptance snippets required in test stdout**

* `wl.rounds=2`
* Three lines: `wl.sig.sha256=<64hex>`
* `wl.topk.k=25`
* `wl.candidates.sha256=<64hex>`

