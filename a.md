# Project Architecture (Method Matching)

## Modules & Key Packages

### `:mapper-core`

- Packages: `cfg`, `dom`, `df`, `wl`, `normalize`, `match`
- Core graph & normalization algorithms reside here.

### `:mapper-signals`

- Packages: `micro`, `strings`, `calls`, `opcode`, `fields`, `normalized`, `idf`, `tfidf`, `score`, `common`
- Hosts secondary signal providers and normalized feature extraction.

### `:mapper-cli`

- Packages: `cli`, `cli.method`, `cli.orch`, `core.match`
- Entry points, feature orchestration, candidate matching, and scoring logic.

---

## Key Components & File Paths

### ReducedCFG, Dominators, DF/TDF, WL refinement

text
Copy
`mapper-core/src/main/java/io/bytecodemapper/core/cfg/ReducedCFG.java mapper-core/src/main/java/io/bytecodemapper/core/dom/Dominators.java mapper-core/src/main/java/io/bytecodemapper/core/df/DF.java          // DF + TDF mapper-core/src/main/java/io/bytecodemapper/core/wl/WLRefinement.java`

### Method Candidate Generator & Orchestrator

text
Copy
`mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java mapper-cli/src/main/java/io/bytecodemapper/cli/orch/Orchestrator.java`

### Signal Providers

text
Copy
`Micropatterns:  mapper-signals/src/main/java/io/bytecodemapper/signals/micro/MicroPatternProviderImpl.java Strings:        mapper-signals/src/main/java/io/bytecodemapper/signals/strings/StringBagExtractor.java Calls TF‑IDF:   mapper-signals/src/main/java/io/bytecodemapper/signals/calls/CallBagExtractor.java                 mapper-signals/src/main/java/io/bytecodemapper/signals/calls/CallBagTfidf.java Opcode histogram:                 mapper-signals/src/main/java/io/bytecodemapper/signals/opcode/OpcodeFeatures.java Field-shape:    mapper-signals/src/main/java/io/bytecodemapper/signals/fields/FieldUsageExtractor.java`

### Normalization Utilities

text
Copy
`Opaque/flattening:    mapper-core/src/main/java/io/bytecodemapper/core/normalize/Normalizer.java Try-catch unwrap & string extractor:                       mapper-signals/src/main/java/io/bytecodemapper/signals/normalized/NormalizedMethod.java                       mapper-signals/src/main/java/io/bytecodemapper/signals/strings/StringBagExtractor.java`

### Decision Policy, Tie-breaks, Determinism

text
Copy
`Thresholds & margins: mapper-cli/src/main/java/io/bytecodemapper/cli/method/MethodScorer.java Determinism switch:   mapper-cli/src/main/java/io/bytecodemapper/cli/orch/OrchestratorOptions.java Candidate ordering & tie-breaks:                       mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java`

### Tests touching Method Matching

text
Copy
`Core WL/CFG/DF tests:   mapper-core/src/test/java/io/bytecodemapper/core/WLRefinementTest.java   mapper-core/src/test/java/io/bytecodemapper/core/WLSignatureSmokeTest.java   mapper-core/src/test/java/io/bytecodemapper/core/NormalizerTest.java Signal tests:   mapper-signals/src/test/java/io/bytecodemapper/signals/micro/MicroPatternExtractorTest.java   mapper-signals/src/test/java/io/bytecodemapper/signals/normalized/NormalizedMethodTest.java CLI & matcher tests (unit/integration):   mapper-cli/src/test/java/io/bytecodemapper/core/match/MethodMatcherDistanceTest.java   mapper-cli/src/test/java/io/bytecodemapper/core/match/MethodMatcherRelaxedFilterTest.java   mapper-cli/src/test/java/io/bytecodemapper/cli/method/MethodScorerBoundaryTest.java   mapper-cli/src/test/java/io/bytecodemapper/cli/AcceptanceMarginIT.java   mapper-cli/src/test/java/io/bytecodemapper/cli/DeterminismEndToEndTest.java   mapper-cli/src/test/java/io/bytecodemapper/cli/MapOldNewAcceptanceMarginTest.java`

### CLI Entrypoints & Flags

text
Copy
`mapper-cli/src/main/java/io/bytecodemapper/cli/Main.java             // usage & global flags mapper-cli/src/main/java/io/bytecodemapper/cli/Router.java           // sub-command router mapper-cli/src/main/java/io/bytecodemapper/cli/MapOldNew.java        // --tauAcceptMethods, --marginMethods, --deterministic mapper-cli/src/main/java/io/bytecodemapper/cli/MethodMatch.java mapper-cli/src/main/java/io/bytecodemapper/cli/ClassMatch.java mapper-cli/src/main/java/io/bytecodemapper/cli/FieldMatch.java mapper-cli/src/main/java/io/bytecodemapper/cli/ApplyMappings.java mapper-cli/src/main/java/io/bytecodemapper/cli/Bench.java`

---

## Data-flow Sketch

swift
Copy
`NormalizedMethod     ↓ (CFG build + Dominators + DF/TDF + WL) Candidate generation (MethodMatcher)     ↓ Per-signal scores (calls/strings/micropatterns/opcodes/fields)     ↓ Composite scoring & decision (MethodScorer)     ↓ Accept or Abstain     ↓ (optional) Call-graph refinement (CallGraphRefiner) → updated accept/abstain`

---

## Where to Plug New Signals

Implement new feature extractors in `mapper-signals` and extend scoring in CLI:

- **Extraction:** add a provider under `mapper-signals/src/main/java/io/bytecodemapper/signals/<new-signal>` and expose it via `MethodFeatureExtractor`.
- **Feature container:** extend `MethodFeatures` to store the new signal.
- **Scoring:** update `MethodScorer` to weight the new signal and incorporate it into `scoreVector`/`scoreOne`.
- **Interfaces:** `MethodFeatureExtractor.ClassOwnerMapper`, `MethodScorer.Result`, and `MethodMatcher.CandidateScore` are the primary integration points.
