# FILE: bench/run_bench.sh
# CODEGEN-BEGIN: bench-run-bash
#!/usr/bin/env bash
# Note: uses bash array 'mapfile' and 'find -maxdepth' (GNU). For macOS default bash/BSD find,
#       use Homebrew bash/findutils or run this on Linux. A portable variant can be added if needed.
set -euo pipefail

# CODEGEN-BEGIN: bench-jars-dir-bash
# Resolve repo root (directory above bench/)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Accept optional CLI arg as jars dir, else env JARS_DIR, else default to repo-root/data/weeks
CLI_JARS_DIR="${1:-}"
if [[ -n "$CLI_JARS_DIR" ]]; then
  JARS_DIR="$CLI_JARS_DIR"
elif [[ -n "${JARS_DIR:-}" ]]; then
  JARS_DIR="${JARS_DIR}"
else
  JARS_DIR="data/weeks"
fi

# If JARS_DIR is relative, make it absolute relative to repo root
case "$JARS_DIR" in
  /*) ;; # absolute
  *) JARS_DIR="${REPO_ROOT}/${JARS_DIR}" ;;
esac

echo "[INFO] Using JARS_DIR=$JARS_DIR"
if [[ ! -d "$JARS_DIR" ]]; then
  echo "[ERROR] JARS_DIR does not exist: $JARS_DIR" >&2
  exit 1
fi
# CODEGEN-END: bench-jars-dir-bash

# Path to your installed CLI; adjust if needed.
CLI="./mapper-cli/build/install/mapper-cli/bin/mapper-cli"
OUT_DIR="bench/out"
CACHE_DIR="bench/cache"
mkdir -p "$OUT_DIR" "$CACHE_DIR"

# Deterministic common args (self-describing reports)
COMMON_ARGS=(--deterministic --use-nsf64=canonical)

run_cfg() {
  local pair="$1" cfg="$2" old="$3" new="$4"
  local outp="$OUT_DIR/${pair}_${cfg}"
  local tiny="${outp}.tiny"
  local json="${outp}.json"
  local args=()

  case "$cfg" in
    baseline_surrogate)
      args=(--use-nsf64=surrogate --wl-relaxed-l1=0 --nsf-near=0)
      ;;
    phase3)
      args=(--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0)
      ;;
    phase4)
      args=(--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.60)
      ;;
    phase4_strict)
      args=(--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.80)
      ;;
    canonical_v1nsf)
      args=(--enable-nsfv2=false --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0)
      ;;
    *)
      echo "Unknown cfg: $cfg" >&2; exit 2;;
  esac

  echo "[RUN] $pair $cfg"
  "$CLI" mapOldNew \
    --old "$old" --new "$new" \
    --out "$tiny" \
    "${COMMON_ARGS[@]}" --report "$json" \
    --cacheDir "$CACHE_DIR" \
    "${args[@]}"
}

# Discover jars like osrs-231.jar, osrs-232.jar; sort numerically; make consecutive pairs.
# CODEGEN-BEGIN: bench-jars-find-bash
mapfile -t jars < <(find "$JARS_DIR" -type f -name 'osrs-*.jar' | sort)
# CODEGEN-END: bench-jars-find-bash
if [[ ${#jars[@]} -lt 2 ]]; then
  echo "Need at least two jars in $JARS_DIR (e.g., osrs-231.jar, osrs-232.jar)" >&2
  exit 1
fi

# Extract numeric versions and sort deterministically by number
declare -A path_by_ver=()
versions=()
for p in "${jars[@]}"; do
  fn="$(basename "$p")"
  if [[ "$fn" =~ ^osrs-([0-9]+)\.jar$ ]]; then
    v="${BASH_REMATCH[1]}"
    versions+=("$v")
    path_by_ver["$v"]="$p"
  fi
done

IFS=$'\n' versions_sorted=($(printf "%s\n" "${versions[@]}" | sort -n))
unset IFS

# CODEGEN-BEGIN: bench-jars-sanity-bash
if [[ "${#versions_sorted[@]}" -lt 2 ]]; then
  echo "[ERROR] Found fewer than two osrs-*.jar in $JARS_DIR" >&2
  exit 1
fi
# CODEGEN-END: bench-jars-sanity-bash

# Build consecutive pairs: v[i-1] -> v[i]
for ((i=1; i<${#versions_sorted[@]}; i++)); do
  old_v="${versions_sorted[$i-1]}"
  new_v="${versions_sorted[$i]}"
  old_jar="${path_by_ver[$old_v]}"
  new_jar="${path_by_ver[$new_v]}"
  pair="osrs-${old_v}-${new_v}"
  run_cfg "$pair" baseline_surrogate "$old_jar" "$new_jar"
  run_cfg "$pair" phase3            "$old_jar" "$new_jar"
  run_cfg "$pair" phase4            "$old_jar" "$new_jar"
  # Optional:
  # run_cfg "$pair" phase4_strict   "$old_jar" "$new_jar"
  # run_cfg "$pair" canonical_v1nsf "$old_jar" "$new_jar"
done

echo "[DONE] Reports in $OUT_DIR"
# CODEGEN-END: bench-run-bash
