<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS TROUBLESHOOTING SMOKE BEGIN -->
# Troubleshooting: `MapOldNewSmokeTest`

Common causes of failures and quick fixes:

- **Missing fixtures**: Ensure `data/weeks/osrs-170.jar` and `osrs-171.jar` exist. If your copies live elsewhere, run the CLI with absolute paths or set `DATA_ROOT`.
- **CWD-dependent paths**: The smoke test writes to `build/smoke/mapoldnew/…`. Do not rely on the current working directory. Use absolute fixture paths.
- **Determinism flags**: Always pass `--deterministic`, set explicit thresholds (`--tauAcceptMethods`, `--marginMethods`) if tuning, and throttle with `--maxMethods` for fast CI.
- **Feature cache invalidation**: If WL iteration count (`WL_K`) changes, clear cache directories or bump the cache fingerprint (e.g., `wlK4-YYYYMMDD`).
- **Java/ASM**: Build with Java 1.8 and ASM 7.3.1. Newer ASM versions are unsupported.
- **Normalized debug**: The smoke test asserts the normalized debug dump exists; pass `--debug-normalized <file>` and `--debug-sample <N>`.

If you still see non-deterministic outputs, repack jars with fixed timestamps and sorted entries (enabled by default in `applyMappings`).
<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS TROUBLESHOOTING SMOKE END -->

<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS CI SMOKE TRIAGE BEGIN -->
## CI smoke failures — quick triage

1. **Fixture jars missing:** Ensure `data/weeks/osrs-170.jar` and `osrs-171.jar` exist in repo/CI context.
2. **Non-determinism:** Verify `--deterministic` is passed; confirm repack sets sorted entries and timestamp `0L`.
3. **Threshold tuning:** If acceptance flips across runs, check `--tauAcceptMethods` and `--marginMethods`; use default `0.60/0.05`.
4. **Cache invalidation:** When WL_K changes, clear caches or bump cache fingerprint.
5. **JDK drift:** Must be **Java 8**; the workflow pins Temurin 8.
Artifacts `smoke-artifacts` include mappings, debug dumps, remapped jars, and metrics for offline comparison.
<!-- >>> AUTOGEN: BYTECODEMAPPER DOCS CI SMOKE TRIAGE END -->
