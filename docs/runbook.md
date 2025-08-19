<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook orchestrator-caches BEGIN -->
## Orchestrator, caches, and deterministic mode

**Flow:** `Normalize → CFG → Dominators/DF/TDF → WL → Class match → Method match (+Refine) → Field match → Write Tiny v2`.

- **Normalization:** All downstream features (WL, micropatterns, normalized histogram/strings/calls) are computed from the **analysis CFG** after minimal normalization.
- **Persistent caches:** Per-jar method-feature caches live under `build/cache/<jarSHA>.methods.ser`, keyed by `owner#name(desc)#normalizedBodyHash#IRfp`. Each entry contains WL signature, micropattern bitset, generalized opcode histogram, filtered strings, call-bag, normalized descriptor, and the IR fingerprint.
- **Determinism:** When `--deterministic` is set, the pipeline avoids parallelism and imposes explicit sorting before hashing/serialization. Two identical runs must produce **byte-identical** `mappings.tiny`.

- **WL iterations (K):** Standardized at **WL_K=4** for WL signatures. Cache header bumped to `BMAP:MFC:2-wlK4-20250819` to invalidate stale entries.

**CLI flags:**
--deterministic
--cacheDir <dir> (default: build/cache)
--idf <path> (default: build/idf.properties)
--refine --lambda L --refineIters N
--debug-stats
--debug-normalized [path] --debug-sample N


**Determinism check:**
Run `mapOldNew` twice with the same flags and compare `mappings.tiny` bytes; caches and IDF are reused across runs.

Windows / PowerShell quick check:

``powershell
./gradlew :mapper-cli:run --args="mapOldNew --old testData/jars/old.jar --new testData/jars/new.jar --out mapper-cli/build/m4.tiny --deterministic --cacheDir mapper-cli/build/cache --idf mapper-cli/build/idf.properties"
cp mapper-cli\build\m4.tiny mapper-cli\build\m4a.tiny
./gradlew :mapper-cli:run --args="mapOldNew --old testData/jars/old.jar --new testData/jars/new.jar --out mapper-cli/build/m4b.tiny --deterministic --cacheDir mapper-cli/build/cache --idf mapper-cli/build/idf.properties"
fc.exe mapper-cli\build\m4a.tiny mapper-cli\build\m4b.tiny
``
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook orchestrator-caches END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook mapping-io-remap BEGIN -->
## Mapping I/O and Remapping (Tiny v2 default)

This project emits **Tiny v2** mappings with two namespaces: **`obf` → `deobf`** and performs a real bytecode remap using **ASM ClassRemapper** by default (Java 8 safe). You can later extend `applyMappings` to use TinyRemapper or SpecialSource if needed.

### Tiny v2 layout

Header:
`tiny\t2\t0\tobf\tdeobf`

Body lines:
- Class:  `c  <owner_obf>  <owner_deobf>`
- Field:  `f  <owner_obf>  <desc>  <name_obf>  <name_deobf>`
- Method: `m  <owner_obf>  <desc>  <name_obf>  <name_deobf>`

Entries are written in a **deterministic order** (sorted) to keep outputs stable across runs.

### CLI usage

**Generate mappings (Tiny v2):**
``bash
./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny"
``

**Apply mappings (ASM remapper):**
``bash
./gradlew :mapper-cli:run --args="applyMappings --inJar new.jar --mappings build/mappings.tiny --out build/new-mapped.jar"
``

Supported flags:

* `--format tiny2|enigma` (current build supports **tiny2**)
* `--ns obf,deobf` (reserved; default `obf,deobf`)
* `--remapper asm` (default; future: `tinyremapper|specialsource`)
* `--deterministic`, `--verbose`, `--quiet` (standard behavior)
* `--debug-normalized [path]` to dump normalized-method features during `mapOldNew`

### Windows / PowerShell

Prefer the installed launcher to avoid `--args` quirks:

``powershell
./gradlew :mapper-cli:installDist
mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat mapOldNew `
	--old old.jar `
	--new new.jar `
	--out build/mappings.tiny

mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat applyMappings `
	--inJar new.jar `
	--mappings build/mappings.tiny `
	--out build/new-mapped.jar
``

The remapped jar is written to `mapper-cli/build/new-mapped.jar`. Use `jar tf` to inspect renamed classes.

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook mapping-io-remap END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook NormalizedMethod BEGIN -->
## NormalizedMethod feature (analysis CFG aligned)

`NormalizedMethod` runs on **post-normalization** bytecode to stay aligned with DF/TDF + WL:

- Unwraps whole-method `RuntimeException` wrappers where detectable.
- Excludes wrapper handler strings and known opaque-guard early-exit blocks.
- Produces:
	- **generalized opcode histogram** (sparse → dense[200] for scoring),
	- **string constants** (wrapper-noise filtered),
	- **invoked signatures** (`owner.name(desc)`, `indy:name(desc)`),
	- **normalized descriptor** (opaque-param drop, stub OK),
	- **SHA-256 fingerprint** over descriptor + sorted sets.
- CLI flag `--debug-normalized` writes `mapper-cli/build/normalized_debug.txt` with descriptor, top opcodes, strings, and fingerprint for a deterministic sample.

These signals **augment** (and may replace) legacy opcode histograms in method scoring.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook NormalizedMethod END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook refine BEGIN -->
## Phase-3 Call-graph Refinement (optional)

Enable with `--refine` to reinforce matches using **intra-class** call graphs:

- Build app-only graphs per mapped class pair.
- Update scores iteratively: `S' = (1 − λ)·S + λ·N`, where **N** is neighbor consistency.
- Caps: **−0.05 / +0.10** relative to base S₀; Freeze: keep strong base matches (S₀≥0.80, margin≥0.05).
- CLI prints per-iteration `flips` and `maxΔ`; flips should decrease.

Example:
./gradlew :mapper-cli:run --args="methodMatch --old old.jar --new new.jar --classMap build/classmap.txt --out build/methodmap.txt --refine --lambda 0.70 --refineIters 5"

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook refine END -->
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

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook field-match BEGIN -->
## Phase-4 Field Matching (conservative)

Inputs:

- `--old`, `--new` jars
- `--methodMap` from Phase-2
- `--out` output path

Method:

- For each matched method pair, collect field uses (GET/PUT, static/instance).
- Aggregate votes: for each old field, count co-occurrences with new fields, and track read/write ratios.
- Accept only if: support ≥ 3, margin ≥ 2, RW-ratio similarity ≥ 0.60, and the owner matches via the class map. Otherwise abstain.

Usage:

```bash
./gradlew :mapper-cli:run --args="fieldMatch --old old.jar --new new.jar --methodMap build/methodmap.txt --out build/fieldmap.txt"
```
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook field-match END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook methodmap-format BEGIN -->
### Method Map Format (tolerant)

The CLI accepts either of the following per-line formats (comments `#` ignored):

- `owner#name(desc) -> owner#name(desc) [score=…]`
- `owner/name desc -> owner/name desc [score=…]`

Any trailing `score=…` token is ignored during parsing.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook methodmap-format END -->
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook windows-launcher BEGIN -->
### Windows / PowerShell note

When invoking `:mapper-cli:run` on PowerShell, Gradle may swallow `--old/--new/...` arguments.
Prefer the application distribution launcher:

```powershell
# Build launcher
./gradlew :mapper-cli:installDist

# Run CLI
mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat mapOldNew `
    --old testData/jars/old.jar `
<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook verification-workflow BEGIN -->
## Verifying mappings and remaps

When `mappings.tiny` is header-only (no `c/f/m` lines), the remapper has nothing to rename. Use these tools to diagnose:

1. **Stats**
``bash
./gradlew :mapper-cli:run --args="tinyStats --in build/mappings.tiny"
./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny --debug-stats"
``

Tune acceptance thresholds (methods)
``bash
./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny --tauAcceptMethods 0.50 --marginMethods 0.02 --debug-stats"
``

Smoke options

Include identity class lines:
``bash
./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny --includeIdentity"
``

Force a small demo rename (visual remap):
``bash
./gradlew :mapper-cli:run --args="mapOldNew --old old.jar --new new.jar --out build/mappings.tiny --demoRemapCount 3 --demoRemapPrefix zz/demo"
``

Apply & verify remap
``bash
./gradlew :mapper-cli:run --args="applyMappings --inJar new.jar --mappings build/mappings.tiny --out build/new-mapped.jar --verifyRemap"
# Inspect entries
jar tf mapper-cli/build/new-mapped.jar | head -n 20
``

Windows/PowerShell:
Use the installed launcher to avoid --args quirks:
``powershell
./gradlew :mapper-cli:installDist
mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat mapOldNew `
	--old old.jar --new new.jar --out build/mappings.tiny --debug-stats
mapper-cli/build/install/mapper-cli/bin/mapper-cli.bat applyMappings `
	--inJar new.jar --mappings build/mappings.tiny --out build/new-mapped.jar --verifyRemap
``
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook verification-workflow END -->
    --new testData/jars/new.jar `
    --out build/mappings.tiny `
    --debug-normalized
```

The debug dump is written to `mapper-cli/build/normalized_debug.txt` by default,
or to the path supplied after `--debug-normalized <path>`.

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook windows-launcher END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook remap-order-identity BEGIN -->
### Remap ordering & identity mappings

- **Case-sensitive entry order:** The remapper writes jar entries in a deterministic, **case-sensitive** order. This prevents collisions that could occur with case-insensitive sorting on exotic inputs.
- **MANIFEST first:** If present, `META-INF/MANIFEST.MF` is emitted first; all other entries follow in stable order.
- **Identity mappings:** `tinyStats` now reports **identity vs non-identity** counts and prints a few sample pairs. If you see only identity pairs, the remapper will not rename entries (expected).
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook remap-order-identity END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook cache-fingerprint BEGIN -->
### Cache key & invalidation policy

- **Key structure:** `owner#name(desc)#normalizedBodyHash#IRfp`
	- `normalizedBodyHash`: SHA-256 over normalized (post-normalization) opcodes/operands, ignoring labels/frames/line numbers.
	- `IRfp` (IR fingerprint): concatenation/hash of `NormalizerFingerprint` and `ReducedCfgFingerprint` so that both normalization semantics and reduced-CFG build options are part of the cache key.
- **Per-jar metadata:** For each input JAR, a `*.meta.properties` file is written under `--cacheDir` with:
	- `normalizerVersion`, `normalizerOptionsFingerprint`, and `reducedCfgOptionsFingerprint`
- **When to bump:**
	- Any change in normalization that can affect CFG/DF/TDF/WL, or feature extraction ordering → bump `NormalizerFingerprint.NORMALIZER_VERSION`.
	- Any change in reduced-CFG construction options (e.g., linear-chain merge policy) → update `ReducedCfgFingerprint` content.
	- Any change to WL iterations (**WL_K**) → cache header bump and meta token `wl.iterations` updated.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook cache-fingerprint END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook cache-ir-fingerprint BEGIN -->
### IR cache fingerprint (Normalizer + CFG)

Cache keys include a composite **IR fingerprint**:


```text
owner#name(desc)#normalizedBodyHash#<normalizerFp||cfgFp>
```

- **normalizerFp:** versioned options string (e.g., `v=1;opaque=true;unwrapRTE=true;detectFlatten=true`)
- **cfgFp:** versioned CFG options string (e.g., `cv=1;excEdges=LOOSE;mergeLinear=true`)

**When to bump versions:**


- `NORMALIZER_VERSION` — any normalization change that can affect IR/CFG shape.
- `CFG_VERSION` — any ReducedCFG change (e.g., exception edge policy) that affects DF/TDF/WL or micropatterns.

This ensures caches invalidate when either **Normalizer** or **CFG** behavior changes.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook cache-ir-fingerprint END -->


<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook bench BEGIN -->
## Bench harness

Evaluate stability over consecutive builds (weekly, monthly, etc.) laid out in `--in`:

```text
data/weeks/osrs-1.jar
data/weeks/osrs-2.jar
...
data/weeks/osrs-232.jar
```
Pairs are built by sorting ascending and forming adjacent pairs: `(1→2), (2→3), ...`.

**Run:**

```bash
./gradlew :mapper-cli:run --args="bench --in data/weeks --out build/bench.json"
```

**Ablation:**

```bash
# turn off calls + micropatterns
./gradlew :mapper-cli:run --args="bench --in data/weeks --out build/bench.calls_off.json --ablate calls,micro"
```

**Metrics written:**

- `churnJaccard` — Jaccard of method-coverage on the shared middle jar vs previous pair.
- `osc3Coverage` — symmetric-diff rate of middle-jar coverage across a triple (proxy for 3-period flip).
- `ambiguousPairF1` — `null` unless ground-truth is provided; `ambiguousCount` is emitted for scale.
- `acceptedMethods`, `abstainedMethods`, `acceptedClasses`, `elapsedMs`, `usedMB`.

The top-level JSON also includes `totalMs`, `peakMB`, and the `ablate` list.

**Windows / PowerShell:**

```powershell
./gradlew :mapper-cli:run --args="bench --in data/weeks --out build/bench.json"
Get-Content build/bench.json -TotalCount 1
```

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook bench END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC runbook weekly-ops BEGIN -->
## Weekly workflow & drift checklist

### Weekly workflow

1. Obtain `old.jar` and `new.jar`.
1. Run mapping in deterministic mode:

```bash
./gradlew :mapper-cli:installDist
mapper-cli/build/install/mapper-cli/bin/mapper-cli mapOldNew --old data/weeks/osrs-231.jar --new data/weeks/osrs-232.jar --out mapper-cli/build/mappings.tiny --deterministic --cacheDir mapper-cli/build/cache --idf mapper-cli/build/idf.properties --refine --lambda 0.7 --refineIters 5 --debug-stats
```

1. Optionally remap:

```bash
mapper-cli/build/install/mapper-cli/bin/mapper-cli applyMappings --inJar data/weeks/osrs-232.jar --mappings mapper-cli/build/mappings.tiny --out mapper-cli/build/new-mapped.jar --verifyRemap
```

### Cache hygiene

- Caches live under `build/cache` (configurable via `--cacheDir`).
- Normalizer fingerprint is embedded in cache keys; toggle Normalizer options → safe cache invalidation.
- If in doubt, wipe only the affected jar cache files; avoid global wipes to preserve determinism.

### If drift spikes

- **Flattening detector:** check flagged methods; bypass WL/micro if dispatcher present.
- **IDF health:** `printIdf` and inspect outliers; clamps `[0.5, 3.0]`.
- **Abstentions:** inspect `unmapped.txt`; if many leaf methods, review `w_calls` and class anchoring.
- **Normalization:** ensure opaque predicate strip and trivial RTE wrapper removal behaved as expected.

### Ablations

- To probe signal impact:

```bash
./gradlew :mapper-cli:run --args="bench --in data/weeks --out mapper-cli/build/bench.calls_off.json --ablate calls"
```

- Compare churn/oscillation vs. baseline to guide weight tuning.

<!-- <<< AUTOGEN: BYTECODEMAPPER DOC runbook weekly-ops END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOC vscode-tasks-launch BEGIN -->
## VS Code tasks & launch

The repo includes VS Code tasks and launchers to run common flows without typing Gradle commands.

### Tasks (Terminal → Run Task…)

- **Mapper: installDist** — builds the CLI launcher.
- **Mapper: mapOldNew** — prompts for OLD/NEW jars and writes `mapper-cli/build/mappings.tiny`.
- **Mapper: applyMappings** — remaps the NEW jar using the latest mappings.
- **Mapper: bench** — runs the multi-pair benchmark into `mapper-cli/build/bench.json`.
- **Mapper: printIdf** — writes `mapper-cli/build/idf.properties`.

> Tips:
>
> - Place week jars under `data/weeks` at repo root.
> - On Windows/PowerShell, tasks use the installed launcher (`mapper-cli.bat`).

### Launch (Run → Start Debugging)

- **Mapper: mapOldNew (Java Main)** — runs `io.bytecodemapper.cli.Main` with defaults.
- **Mapper: mapOldNew (Installed CLI)** — shell-based fallback that uses the installed launcher.

All configs are patched **inside AUTOGEN markers**, so they’re safe to re-run without clobbering your custom entries.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC vscode-tasks-launch END -->
