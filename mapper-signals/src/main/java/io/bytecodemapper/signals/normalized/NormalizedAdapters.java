// >>> AUTOGEN: BYTECODEMAPPER signals NormalizedAdapters BEGIN
package io.bytecodemapper.signals.normalized;

import java.util.Map;

public final class NormalizedAdapters {

    /** Convert sparse opcode histogram to dense array [0..199] (covers all ASM opcodes in practice). */
    public static int[] toDense200(Map<Integer,Integer> sparse) {
        int[] a = new int[200];
        for (Map.Entry<Integer,Integer> e : sparse.entrySet()) {
            int k = e.getKey().intValue();
            if (k >= 0 && k < a.length) a[k] = e.getValue().intValue();
        }
        return a;
    }
You are validating Phase 4 (scoring integration). Do NOT edit code.

Steps (bash; provide PowerShell equivalents after each block):

1) Clean & build tests
----------------------------------------------------------------
./gradlew :mapper-core:test :mapper-signals:test :mapper-cli:test --no-daemon -i
# pwsh:
# ./gradlew.bat :mapper-core:test :mapper-signals:test :mapper-cli:test --no-daemon -i

2) Map one month deterministically; capture baseline runtime and artifacts
----------------------------------------------------------------
OLD=data/weeks/2024-07/old.jar
NEW=data/weeks/2024-07/new.jar
OUT_A=out/v2-phase4-A.tiny
OUT_B=out/v2-phase4-B.tiny
REP_A=out/v2-phase4-A.json
REP_B=out/v2-phase4-B.json
mkdir -p out

# First run → baseline timing (records wall time in ms)
ts0=$(date +%s%3N); \
./gradlew :mapper-cli:run --args="mapOldNew --old $OLD --new $NEW --out $OUT_A --deterministic --use-nsf64=canonical --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos 0.60 --report $REP_A --dump-normalized-features=out/nsf-jsonl" --no-daemon -q; \
ts1=$(date +%s%3N); \
echo $((ts1-ts0)) > out/phase4-baseline-ms.txt

# Second run → determinism & perf budget (<= +20% vs baseline)
ts2=$(date +%s%3N); \
./gradlew :mapper-cli:run --args="mapOldNew --old $OLD --new $NEW --out $OUT_B --deterministic --use-nsf64=canonical --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos 0.60 --report $REP_B --dump-normalized-features=out/nsf-jsonl" --no-daemon -q; \
ts3=$(date +%s%3N); \
echo $((ts3-ts2)) > out/phase4-second-ms.txt

# pwsh equivalents:
# $env:OLD="data/weeks/2024-07/old.jar"; $env:NEW="data/weeks/2024-07/new.jar"
# $env:OUT_A="out/v2-phase4-A.tiny"; $env:OUT_B="out/v2-phase4-B.tiny"
# $env:REP_A="out/v2-phase4-A.json"; $env:REP_B="out/v2-phase4-B.json"
# New-Item -ItemType Directory -Force -Path out | Out-Null
# $ts0=[int64](Get-Date -UFormat %s%3N); ./gradlew.bat :mapper-cli:run --args="mapOldNew --old $env:OLD --new $env:NEW --out $env:OUT_A --deterministic --use-nsf64=canonical --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos 0.60 --report $env:REP_A --dump-normalized-features=out/nsf-jsonl" --no-daemon -q; $ts1=[int64](Get-Date -UFormat %s%3N); ($ts1-$ts0) | Out-File out/phase4-baseline-ms.txt -Encoding ascii
# $ts2=[int64](Get-Date -UFormat %s%3N); ./gradlew.bat :mapper-cli:run --args="mapOldNew --old $env:OLD --new $env:NEW --out $env:OUT_B --deterministic --use-nsf64=canonical --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos 0.60 --report $env:REP_B --dump-normalized-features=out/nsf-jsonl" --no-daemon -q; $ts3=[int64](Get-Date -UFormat %s%3N); ($ts3-$ts2) | Out-File out/phase4-second-ms.txt -Encoding ascii

3) Determinism check (byte-identical Tiny + stable runtime within +20%)
----------------------------------------------------------------
cmp -s "$OUT_A" "$OUT_B" && echo "OK: tiny outputs identical" || (echo "FAIL: tiny outputs differ" && exit 1)
# pwsh: if ((Get-FileHash $env:OUT_A).Hash -eq (Get-FileHash $env:OUT_B).Hash) { 'OK: tiny outputs identical' } else { throw 'FAIL: tiny outputs differ' }

BASE=$(cat out/phase4-baseline-ms.txt)
CURR=$(cat out/phase4-second-ms.txt)
python - <<'PY'
import os,sys
base=int(open('out/phase4-baseline-ms.txt').read().strip())
curr=int(open('out/phase4-second-ms.txt').read().strip())
budget=int(base*1.20)
print("base_ms=",base,"curr_ms=",curr,"budget_ms=",budget)
sys.exit(0 if curr<=budget else 2)
PY
# pwsh alternative: compare integers and throw if curr > base*1.2

4) Evidence that new subscores are active
----------------------------------------------------------------
# Grep compiled scorer for new weights and method calls (basic smoke check)
grep -RIn "W_STACK" mapper-cli/src/main/java || true
grep -RIn "W_LITS"  mapper-cli/src/main/java || true
grep -RIn "cosineStackFixed5" mapper-cli/src/main/java || true
grep -RIn "minhashSimilarity64" mapper-cli/src/main/java || true

# Optional: add a tiny synthetic test mapping (if you have a toy pair) to observe small score shifts in logs.

Output REQUIRED:
- Print: test task summary (all green)
- Print: "OK: tiny outputs identical"
- Print: base_ms, curr_ms, budget_ms and whether perf passes (exit code 0 on pass)
- Print: the four grep hits confirming integration points

    /** Cosine similarity for dense int histograms. */
    public static double cosineDense(int[] a, int[] b) {
        long dot = 0L, na = 0L, nb = 0L;
        int n = Math.min(a.length, b.length);
        for (int i=0;i<n;i++) {
            int ai=a[i], bi=b[i];
            dot += (long) ai * (long) bi;
            na  += (long) ai * (long) ai;
            nb  += (long) bi * (long) bi;
        }
        if (na==0L || nb==0L) return 0.0;
        return dot / (Math.sqrt((double)na) * Math.sqrt((double)nb));
    }

    /**
     * Deterministic cosine over fixed 5-key stack-delta order.
     * Keys are evaluated exactly in order: {"-2","-1","0","+1","+2"}.
     * Missing maps/keys are treated as 0. If either vector is all-zero, returns 0.0.
     */
    public static double cosineStackFixed5(Map<String, Integer> a, Map<String, Integer> b) {
        final String[] order = {"-2", "-1", "0", "+1", "+2"};
        long dot = 0L, na = 0L, nb = 0L;
        for (int i = 0; i < order.length; i++) {
            final String k = order[i];
            final int ai = (a == null) ? 0 : (a.get(k) == null ? 0 : a.get(k).intValue());
            final int bi = (b == null) ? 0 : (b.get(k) == null ? 0 : b.get(k).intValue());
            dot += (long) ai * (long) bi;
            na  += (long) ai * (long) ai;
            nb  += (long) bi * (long) bi;
        }
        if (na == 0L || nb == 0L) return 0.0;
        return dot / (Math.sqrt((double) na) * Math.sqrt((double) nb));
    }

    /**
     * Deterministic 64-bucket minhash similarity.
     * Empty buckets are denoted by Integer.MAX_VALUE. Similarity = matches / denom where
     * denom counts positions where either side is non-empty, and matches counts equal
     * non-empty values. Null arrays yield 0.0. Handles non-64 lengths by treating
     * out-of-bounds positions as empty.
     */
    public static double minhashSimilarity64(int[] a, int[] b) {
        if (a == null || b == null) return 0.0;
        final int MAX = Integer.MAX_VALUE;
        int denom = 0;
        int matches = 0;
        for (int i = 0; i < 64; i++) {
            final int ai = (i < a.length) ? a[i] : MAX;
            final int bi = (i < b.length) ? b[i] : MAX;
            final boolean ae = (ai == MAX);
            final boolean be = (bi == MAX);
            if (!(ae && be)) {
                denom++;
                if (ai == bi && !ae) {
                    matches++;
                }
            }
        }
        return denom == 0 ? 0.0 : (matches * 1.0) / denom;
    }

    private NormalizedAdapters(){}
}
// <<< AUTOGEN: BYTECODEMAPPER signals NormalizedAdapters END
