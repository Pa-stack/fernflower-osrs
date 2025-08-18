<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook classMatch BEGIN -->
## Phase-1 Class Matching

**Inputs:** two jars (`--old`, `--new`).

**Features per class:**

- WL method signatures histogram (top-N = 32)
- Micropattern class histogram (17)
- Counts: method/field
- Type info: super, interfaces

**Scoring (fixed weights):**

- WL cosine (0.70)
- Micropattern cosine (0.15)
- Count similarity (0.10)
- Super/interfaces overlap (0.05)

**Assignment:** deterministic greedy with threshold `τ_class=0.55` and margin `0.02`.

**CLI:**

```bash
./gradlew :mapper-cli:run --args="classMatch --old <old.jar> --new <new.jar> --out build/classmap.txt"
```

Output lines:

`a/b/OldClass -> x/y/NewClass score=0.8123`

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook classMatch END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook printIdf BEGIN -->
# Runbook

## Printing the Micropattern IDF Table

The `printIdf` command runs in the context of the `:mapper-cli` module.
Paths passed to `--out` are **relative to `mapper-cli/`**, not the repo root.

### Usage

```bash
./gradlew :mapper-cli:run --args="printIdf --out build/idf.properties"
# Output path: mapper-cli/build/idf.properties
```

### Optional flags

- `--from <existing.properties>`: load an existing store before writing
- `--lambda <0<λ<1>`: override the EMA decay (default 0.9)

### Acceptance check

```bash
./gradlew :mapper-cli:run --args="printIdf --out build/idf.properties"
test -f mapper-cli/build/idf.properties && echo "IDF file created"
```
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook printIdf END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook signals BEGIN -->
## Secondary signals: where they apply

During **Phase-2 method matching**, after WL + DF/TDF candidate generation:

- **Call-bag TF-IDF**: build token bags per method (excluding `java.*`, `javax.*`);

**Normalize owners after class matching** using the class map before scoring.
- **String TF-IDF**: lightweight, stable only when strings aren’t obfuscated.
- **Opcode histogram**: order-invariant stability.

**Optional** 2–3-gram features provide order sensitivity for tie-breaks.


Scoring blend (defaults):
`S_total = 0.45*S_calls + 0.25*S_micro(α_mp) + 0.15*S_opcode + 0.10*S_strings + 0.05*S_fields`
Use `τ_accept` for final acceptance; abstain on low margin.

See `mapper-signals/README.md` for API details.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook signals END -->
