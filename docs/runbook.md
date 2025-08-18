<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook io-paths BEGIN -->
### CLI Input/Output Path Semantics

Inputs (`--old`, `--new`, `--classMap`):

1. Resolved relative to the **current working directory** (CWD).
2. If not found, resolved **relative to repo root**.
3. Otherwise kept as the absolute of the provided relative path (no module anchoring).

Outputs (`--out`):

- If absolute → used as-is.
- If invoked from **repo root** (has `mapper-cli/`), relative outputs are anchored under `repoRoot/mapper-cli/…`.
- Otherwise, outputs are CWD-relative (e.g., inside `:mapper-cli`).

This keeps artifact locations predictable while allowing flexible invocation.

#### Examples

```bash
# From repo root:
./gradlew :mapper-cli:run --args="classMatch --old testData/jars/old.jar --new testData/jars/new.jar --out build/classmap.txt"
# Output: mapper-cli/build/classmap.txt

# From module dir:
(cd mapper-cli && ../gradlew run --args="methodMatch --old ../testData/jars/old.jar --new ../testData/jars/new.jar --classMap build/classmap.txt --out build/methodmap.txt")
# Output: mapper-cli/build/methodmap.txt
```

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook io-paths END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC classMatch path semantics BEGIN -->
### classMatch path semantics

The `classMatch` subcommand resolves `--old`, `--new`, and `--out` paths in this order:

1. As given (relative to current working directory).
2. Relative to the **repo root**.
3. Relative to the **parent of `mapper-cli/`** (when invoked via `:mapper-cli:run`).

This makes the command resilient whether it is run from the repo root or inside module directories.

Example:

```bash
./gradlew :mapper-cli:run --args="classMatch --old testdata/old.jar --new testdata/new.jar --out build/classmap.txt"
# writes mapper-cli/build/classmap.txt

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC classMatch path semantics END -->
```
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

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook methodMatch BEGIN -->
## Phase-2 Method Matching

**Inputs:** `--old`, `--new`, `--classMap`, `--out`
**Candidate generation:** per mapped class pair, top-K (default 7) nearest by **WL signature Hamming**.

**Composite score:**

```text
S_total = 0.45*S_calls
	+ 0.25*S_micro(α_mp=0.60)
	+ 0.15*S_opcode
	+ 0.10*S_strings
	+ 0.05*S_fields  # (stub for now)
```


**Smart filters:**

- Leaf vs non-Leaf penalty (−0.05)
- Recursive mismatch penalty (−0.03)

**Abstention:** if `(best − secondBest) < 0.05` **or** `best < τ_accept (0.60)`.

**Owner normalization (calls):** old-side call owners are mapped through the **class map** so both sides compare in **new-space**.

**CLI:**

```bash
./gradlew :mapper-cli:run --args="methodMatch --old old.jar --new new.jar --classMap build/classmap.txt --out build/methodmap.txt"
```

Output includes accepted pairs and # abstain … audit lines.

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook methodMatch END -->
