# FILE: bench/run_bench.ps1
# CODEGEN-BEGIN: bench-run-ps1
$ErrorActionPreference = "Stop"

$CLI = ".\mapper-cli\build\install\mapper-cli\bin\mapper-cli.bat"
$JARS_DIR = $env:JARS_DIR
if ([string]::IsNullOrEmpty($JARS_DIR)) { $JARS_DIR = "/data/weeks" }
$OUT = "bench\out"
$CACHE = "bench\cache"
New-Item -Force -ItemType Directory $OUT  | Out-Null
New-Item -Force -ItemType Directory $CACHE| Out-Null

function Run-Cfg([string]$pair,[string]$cfg,[string]$old,[string]$new) {
  $outp = Join-Path $OUT ("{0}_{1}" -f $pair,$cfg)
  $tiny = "$outp.tiny"
  $json = "$outp.json"
  $common = "--deterministic --use-nsf64=canonical --report $json --cacheDir $CACHE"
  switch ($cfg) {
    "baseline_surrogate" { $args="--use-nsf64=surrogate --wl-relaxed-l1=0 --nsf-near=0" }
    "phase3"            { $args="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0" }
    "phase4"            { $args="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.60" }
    "phase4_strict"     { $args="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.80" }
    "canonical_v1nsf"   { $args="--enable-nsfv2=false --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0" }
    default { throw "Unknown cfg $cfg" }
  }
  Write-Host "[RUN] $pair $cfg"
  & $CLI mapOldNew --old $old --new $new --out $tiny $common $args | Out-Null
}

# Collect jars matching osrs-<int>.jar and sort numerically
$jars = Get-ChildItem -Path $JARS_DIR -Filter "osrs-*.jar" -File | Sort-Object Name
$entries = @()
foreach ($j in $jars) {
  if ($j.Name -match "^osrs-([0-9]+)\.jar$") {
    $entries += [PSCustomObject]@{ Ver = [int]$Matches[1]; Path = $j.FullName }
  }
}
$entries = $entries | Sort-Object Ver
if ($entries.Count -lt 2) { throw "Need at least two jars in $JARS_DIR" }

for ($i=1; $i -lt $entries.Count; $i++) {
  $old = $entries[$i-1]
  $new = $entries[$i]
  $pair = "osrs-$($old.Ver)-$($new.Ver)"
  Run-Cfg $pair "baseline_surrogate" $old.Path $new.Path
  Run-Cfg $pair "phase3"            $old.Path $new.Path
  Run-Cfg $pair "phase4"            $old.Path $new.Path
  # Optional:
  # Run-Cfg $pair "phase4_strict"   $old.Path $new.Path
  # Run-Cfg $pair "canonical_v1nsf" $old.Path $new.Path
}

Write-Host "[DONE] Reports in $OUT"
# CODEGEN-END: bench-run-ps1
