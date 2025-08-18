<!-- >>> AUTOGEN: BYTECODEMAPPER DOC bitset.md BEGIN -->
# Micropattern Bitset (17 bits) — Frozen ABI

Canonical order (indices) — **do not reorder**:

0. **NoParams** — takes no arguments (descriptor has empty parameter list).
1. **NoReturn** — returns `void` (descriptor return type `V`).
2. **Recursive** — calls itself (exact owner+name+desc).
3. **SameName** — calls another method (not itself) with the same name; ignore `<init>/<clinit>`.
4. **Leaf** — no method calls; treat `INVOKEDYNAMIC` as a call.
5. **ObjectCreator** — creates new objects (`NEW`).
6. **FieldReader** — reads fields (`GETFIELD/GETSTATIC`).
7. **FieldWriter** — writes fields (`PUTFIELD/PUTSTATIC`).
8. **TypeManipulator** — uses `CHECKCAST`/`INSTANCEOF`.
9. **StraightLine** — **true** by default; set **false** on any branch (`IF*`, `GOTO`) or switch.
10. **Looping** — **true** iff the CFG contains a **dominator back-edge**: an edge `u→v` where `v` dominates `u`.
11. **Exceptions** — presence of `ATHROW`. (If “escapes method” is needed, add `ThrowsOut` separately.)
12. **LocalReader** — local loads (`ILOAD/LLOAD/FLOAD/DLOAD/ALOAD`).
13. **LocalWriter** — local stores (`ISTORE/LSTORE/FSTORE/DSTORE/ASTORE`).
14. **ArrayCreator** — `NEWARRAY/ANEWARRAY/MULTIANEWARRAY`.
15. **ArrayReader** — `*ALOAD` (**IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD**).
16. **ArrayWriter** — `*ASTORE` (**IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE**).

**Notes & corrections (policy):**

- `Exceptions` is keyed solely off `ATHROW` (catalogue recommends simple presence).
- `ArrayReader/ArrayWriter` are based on `*ALOAD/*ASTORE` (not local loads).
- `Looping` uses the DF/TDF backbone: detect **back-edges** via dominators.
- `SameName` excludes self-calls and ignores `<init>/<clinit>` to avoid conflation with recursion/ctors.
- Constructors/clinits participate in all patterns **except** `SameName`.

_Source catalogue: Singer et al., “Fundamental Nano-Patterns to Characterize and Classify Java Methods,” Table 1 (17 patterns)._
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC bitset.md END -->
