#!/usr/bin/env bash
set -euo pipefail

# >>>> BEGIN arrange-weeks.sh (idempotent) >>>>
# ---- CONFIG ----
SRC="${SRC:-/data/weeks}"           # absolute source dir with osrs-<N>.jar
DEST="${DEST:-./data/weeks}"        # destination under repo
YEAR="${YEAR:-2025}"                # target calendar year
CURRENT_WEEK="${CURRENT_WEEK:-34}"  # current week number
LATEST_JAR="${LATEST_JAR:-232}"     # latest jar index (osrs-<N>.jar)
OVERWRITE="${OVERWRITE:-0}"         # 1 to overwrite existing old.jar/new.jar

# base offset so that: week = jar - BASE
# Given: 232 -> week 34  => BASE = 232 - 34 = 198
BASE=$((LATEST_JAR - CURRENT_WEEK))

echo "Arrange OSRS jars:"
echo "  SRC=$SRC"
echo "  DEST=$DEST"
echo "  YEAR=$YEAR  CURRENT_WEEK=$CURRENT_WEEK  LATEST_JAR=$LATEST_JAR  BASE=$BASE"
echo

mkdir -p "$DEST"

# helper copy (idempotent unless OVERWRITE=1)
cp_one() {
  local src="$1" dst="$2"
  if [[ -e "$dst" && "$OVERWRITE" != "1" ]]; then
    echo "SKIP (exists): $dst"
    return 0
  fi
  if [[ ! -f "$src" ]]; then
    echo "MISS (no source): $src"
    return 1
  fi
  mkdir -p "$(dirname "$dst")"
  cp -f "$src" "$dst"
  echo "OK  $src -> $dst"
}

# Process weeks 1..CURRENT_WEEK (1-based, ISO-like week count within the year)
# For each week w: newJar = BASE + w, oldJar = newJar - 1
# Example: w=34 -> new=232 old=231  => 2025-34/{old.jar,new.jar}
fail=0
for (( w=1; w<=CURRENT_WEEK; w++ )); do
  newJar=$((BASE + w))
  oldJar=$((newJar - 1))
  week=$(printf "%02d" "$w")
  weekDir="$DEST/$YEAR-$week"

  srcNew="$SRC/osrs-$newJar.jar"
  srcOld="$SRC/osrs-$oldJar.jar"

  dstNew="$weekDir/new.jar"
  dstOld="$weekDir/old.jar"

  # Only create if at least new exists; we still try old if present
  if [[ -f "$srcNew" ]]; then
    cp_one "$srcNew" "$dstNew" || fail=1
    cp_one "$srcOld" "$dstOld" || true
  else
    echo "SKIP week $YEAR-$week (missing new: $srcNew)"
  fi

done

# Summary
printf "\nSummary:\n"
echo "  Expected mapping:  $LATEST_JAR -> $YEAR-$(printf "%02d" "$CURRENT_WEEK") (new), $((LATEST_JAR-1)) (old)"
echo "  Example check:"
echo "    $SRC/osrs-$LATEST_JAR.jar  => $DEST/$YEAR-$(printf "%02d" "$CURRENT_WEEK")/new.jar"
echo "    $SRC/osrs-$((LATEST_JAR-1)).jar => $DEST/$YEAR-$(printf "%02d" "$CURRENT_WEEK")/old.jar"
echo
if [[ "$fail" -eq 0 ]]; then
  echo "DONE (some olds may be missing if the prior jar doesn't exist)."
else
  echo "DONE with missing sources. Review 'MISS' lines."
fi
# <<<< END arrange-weeks.sh (idempotent) <<<<
