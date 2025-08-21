#!/usr/bin/env bash
set -euo pipefail

# Sanity check script for Phase 1–3 (NSFv2, WL-relaxed L1, Flattening Gates)
# No external deps. Works without jq. Uses grep/sed/awk.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$ROOT_DIR"

WEEK="${WEEK:-}"
OLD_JAR="${OLD:-${OLD_JAR:-}}"
NEW_JAR="${NEW:-${NEW_JAR:-}}"
OUT_DIR="out"
NSF_DIR="mapper-cli/build/nsf-jsonl"
mkdir -p "$OUT_DIR"

# Resolve jars
resolve_jars() {
  if [[ -n "$OLD_JAR" && -n "$NEW_JAR" ]]; then
    return 0
  fi
  if [[ -n "$WEEK" && -d "data/weeks/$WEEK" ]]; then
    if [[ -f "data/weeks/$WEEK/old.jar" && -f "data/weeks/$WEEK/new.jar" ]]; then
      OLD_JAR="data/weeks/$WEEK/old.jar"
      NEW_JAR="data/weeks/$WEEK/new.jar"
      export OLD_JAR NEW_JAR
      return 0
    fi
  fi
  # Fallback: pick two osrs-* jars
  mapfile -t JARS < <(ls -1 data/weeks/osrs-*.jar 2>/dev/null | sort -V || true)
  if [[ ${#JARS[@]} -ge 2 ]]; then
    OLD_JAR="${JARS[0]}"
    NEW_JAR="${JARS[1]}"
    export OLD_JAR NEW_JAR
    return 0
  fi
  echo "No suitable JARs found. Set WEEK or OLD/NEW env vars." >&2
  exit 1
}

resolve_jars

echo "Using OLD=$OLD_JAR"
echo "Using NEW=$NEW_JAR"

# Ensure CLI builds
./gradlew :mapper-cli:installDist -q

# Deterministic runs (A and B)
CMD_ARGS=(
  mapOldNew --old "$OLD_JAR" --new "$NEW_JAR" --out "$OUT_DIR/v2-a.tiny" \
  --deterministic --use-nsf64=canonical --wl-relaxed-l1=2 --wl-size-band=0.10 \
  --report "$OUT_DIR/report-a.json" --dump-normalized-features="$NSF_DIR"
)
start_ts=$(date +%s)
./gradlew :mapper-cli:run --args="${CMD_ARGS[*]}" --no-daemon
mid_ts=$(date +%s)
./gradlew :mapper-cli:run --args="${CMD_ARGS[@]/v2-a.tiny/v2-b.tiny}" --no-daemon \
  --args="${CMD_ARGS[@]/report-a.json/report-b.json}"
end_ts=$(date +%s)

# Determinism check
DET="N"
if cmp -s "$OUT_DIR/v2-a.tiny" "$OUT_DIR/v2-b.tiny"; then DET="Y"; fi

# NSFv2 checks
NSF_OK="N"; STACK_OK="N"; INVK_OK="N"
if [[ -f "$NSF_DIR/old.jsonl" && -f "$NSF_DIR/new.jsonl" ]]; then
  if grep -q '"nsf_version"\s*:\s*"NSFv2"' "$NSF_DIR/old.jsonl" "$NSF_DIR/new.jsonl"; then NSF_OK="Y"; fi
  # Check stackHist ordering pattern strictly on any line
  if grep -E '"stackHist":\{\"-2\":[0-9]+,\"-1\":[0-9]+,\"0\":[0-9]+,\"\+1\":[0-9]+,\"\+2\":[0-9]+\}' "$NSF_DIR/old.jsonl" "$NSF_DIR/new.jsonl" >/dev/null; then STACK_OK="Y"; fi
  if grep -q '"invokeKindCounts"\s*:\s*\[' "$NSF_DIR/old.jsonl" "$NSF_DIR/new.jsonl"; then INVK_OK="Y"; fi
fi

# WL-relaxed & flattening telemetry in report
L1_OK="N"; BAND_OK="N"; WLR_CTRS="not observed"
FLAT_KEYS="not observed"
if [[ -f "$OUT_DIR/report-a.json" ]]; then
  grep -q '"wl_relaxed_l1"\s*:\s*2' "$OUT_DIR/report-a.json" && L1_OK="Y"
  grep -q '"wl_relaxed_size_band"\s*:\s*0\.1' "$OUT_DIR/report-a.json" && BAND_OK="Y"
  if grep -Eq '"wl_relaxed_(gate_passes|candidates|hits|accepted)"\s*:\s*[0-9]+' "$OUT_DIR/report-a.json"; then
    WLR_CTRS=$(grep -Eo '"wl_relaxed_(gate_passes|candidates|hits|accepted)"\s*:\s*[0-9]+' "$OUT_DIR/report-a.json" | tr '\n' ' ')
  fi
  if grep -Eq '"flattening_detected"|"near_before_gates"|"near_after_gates"' "$OUT_DIR/report-a.json"; then
    FLAT_KEYS=$(grep -Eo '"flattening_detected"\s*:\s*[0-9]+|"near_before_gates"\s*:\s*[0-9]+|"near_after_gates"\s*:\s*[0-9]+' "$OUT_DIR/report-a.json" | tr '\n' ' ')
  fi
fi

# Timings
run_a=$((mid_ts - start_ts))
run_b=$((end_ts - mid_ts))

# Summary
cat <<EOF

=== Sanity Check Report (bash) ===
A) Results
- Determinism (tiny A==B): $DET
- NSFv2 dumps: nsf_version(NSFv2)=$NSF_OK, stackHist order=$STACK_OK, invokeKindCounts=$INVK_OK
- WL-relaxed defaults: l1=2? $L1_OK, size_band=0.10? $BAND_OK; counters: $WLR_CTRS
- Flattening telemetry: $FLAT_KEYS
- Perf (secs): runA=$run_a, runB=$run_b

B) Next steps if fields are "not observed":
- Try different JAR pairs via OLD/NEW env vars or a different WEEK folder.

EOF
