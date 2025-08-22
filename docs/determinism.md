<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# Determinism Policy

We guarantee bit-for-bit identical outputs when inputs and config are identical.

Sorting and iteration
- Sort maps/sets by natural order for keys and identifiers.
- Stable traversal of CFG nodes, WL buckets, and candidate lists.

Hashing
- StableHash64 note: FNV-1a 64-bit fixed seed
- Cache key formula:
  sha256(oldJarSHA256+"\n"+newJarSHA256+"\n"+algorithmVersion+"\n"+canonicalConfigJson+"\n"+javaVersion+"\n")

Single-thread flag
- `--deterministic` forces single-thread operation and disables non-deterministic scheduling.
