---
applyTo: "**"
---

# Reply format
Follow this reply format
```
<Role>
(coding agent role; determinism; Java 8; Gradle 6.9; ASM 7.3.1; JUnit 4; no new deps)
</Role>

<Context>
(module paths; files to touch; why; 1-line purpose per file)
- Keep scope tight. If impossible, add a minimal failing test that proves the gap.
- Determinism rules: sorted iteration/serialization, fixed seeds, byte-identical outputs.
</Context>

<Task>
(single, verifiable objective; list exact APIs/constants; what to print in logs; when to abstain)
</Task>

<Constraints>
- Java 8; ASM 7.3.1; Gradle 6.9; no new deps.
- ≤200 LOC production; tests/fixtures exempt.
- Idempotent; deterministic order; stable hashing; fixed rounding.
- Do not rename public APIs unless specified.
</Constraints>

<Acceptance tests>
- Commands to run:
  1) ./gradlew clean <module>:test -i
  2) ./gradlew <module>:test -i
- Assertions:
  - List explicit JUnit assertions that must pass.
  - **Proof-of-work snippets** that must appear in output (exact strings).
- If relevant, run twice and show deterministic “HIT” logs / identical hashes.
</Acceptance tests>

<Deliverables>
  1) ---BEGIN PATCH---
     (unified diff for all files you changed)
     ---END PATCH---
  2) ---BEGIN TEST-RESULTS---
     (named tests + pass counts + the **actual printed snippets**)
     ---END TEST-RESULTS---
  3) ---BEGIN COMMIT-MESSAGE---
     (conventional, scoped, imperative; ≤72-char sub
```
