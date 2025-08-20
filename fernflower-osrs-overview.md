# Fernflower OSRS Project Overview

1) **Repo at a glance**
- Build system: Gradle 4.0 wrapper, Java plugin; sources/targets at Java 8
- Main module: single Gradle project producing `fernflower-<version>.jar` with `ConsoleDecompiler` as the main class
- External deps: only test-scope JUnit 4.12 and AssertJ 3.12.2—no ASM, TinyRemapper, or Runelite mapping libraries

2) **Module inventory**

| :module | primary packages | key entrypoints (main classes/CLI) | tests of interest | outputs |
| --- | --- | --- | --- | --- |
| `:fernflower` | `org.jetbrains.java.decompiler.*` (code, decompiler, renamer, struct) | `main.decompiler.ConsoleDecompiler` CLI | `BulkDecompilationTest`, `SingleClassesTest`, `ConverterHelperTest` under `test/org/jetbrains/java/decompiler` | `build/libs/fernflower-<ver>.jar`, decompiled `.java` files |

3) **Mapping pipeline surfaces**
- Renaming: `modules/renamer/IdentifierConverter` orchestrates class/field/method renames via `IIdentifierRenamer` and `PoolInterceptor`
- Deobfuscation: `modules/decompiler/deobfuscator/ExceptionDeobfuscator` (only basic exception flow cleaning; no annotation support)
- CFG/Dominators: `code/cfg/ControlFlowGraph` builds block graphs and manages edges; `modules/decompiler/decompose/DominatorEngine` computes immediate dominators
- No MethodMatcher or orchestrator beyond the decompiler’s internal pipeline; no remapper libraries (ASM/TinyRemapper) present

4) **Annotation ground-truth integration points**
- The codebase has no references to `net.runelite.mapping` or Runelite-specific annotations (search yields none)
- Annotation parsing already exists (`StructAnnotationAttribute.parseAnnotations`) for standard JVM attributes
- Potential insertion points:
  - Extend `StructClass`, `StructField`, and `StructMethod` to surface `@ObfuscatedName`/`@ObfuscatedSignature` parsed via `StructAnnotationAttribute`
  - Add an `AnnotationSource` that reads a deobfuscated gamepack JAR (using ASM visit to gather annotation values) and populates a mapping before the renamer runs
  - Integrate a `GroundTruthMerger` inside `IdentifierConverter.renameClassIdentifiers` or via a custom `IIdentifierRenamer` to prefer annotation-supplied names over generated ones
- No annotated JAR or deobfuscated gamepack is included; please provide the path to such an artifact to proceed with integration

5) **CLI & config**
- CLI command: `java -jar fernflower.jar [-option=value]* [source]+ destination`
- Options defined in `IFernflowerPreferences` (e.g., `ren`, `mpm`, `log`, etc.)
- To add `--annotations-jar`, `--deob-source`, or `--prefer-annotations`, define new preference keys in `IFernflowerPreferences`, parse them in `ConsoleDecompiler`, and propagate to the renamer
- No external config files or environment-variable overrides observed

6) **Tests & determinism**
- Tests cover basic decompilation and converter logic (`BulkDecompilationTest`, `SingleClassesTest`, etc.)
- `DecompilerTestFixture` sets consistent options (e.g., `LITERALS_AS_IS`, `REMOVE_BRIDGE`) to stabilize output; no explicit deterministic seed mechanism
- No tests exercise renaming with external mappings or annotations—fixtures for annotated jars and conflict-resolution cases are missing

7) **Outputs & artifacts**
- Build output: `build/libs/fernflower-21062020.jar` via Gradle’s `jar` task
- CLI writes decompiled `.java` files or archives to user-specified directories through `IResultSaver` methods like `saveFolder` and `copyFile`
- Mapping tables are kept in-memory (`PoolInterceptor`); no mapping files or remapped jars are produced or versioned
- `testData` contains reference classes and expected decompiled sources but no mapping artifacts

8) **Risks & unknowns**
- **No annotation source**: repository lacks deobfuscated gamepack or `net.runelite.mapping` annotations—ground-truth integration can’t be validated without them
- **Missing libraries**: ingestion of annotations may require adding ASM or similar; build script currently has no compile dependencies
- **Pipeline fit**: existing renamer is designed for algorithmic renaming; merging external mappings may require significant refactoring
- **Testing gap**: absence of fixtures and golden mappings means new features risk regressions without dedicated tests

*To proceed, please supply the path to an annotated OSRS gamepack or repository so an `AnnotationSource` reader can be wired and exercised.*
