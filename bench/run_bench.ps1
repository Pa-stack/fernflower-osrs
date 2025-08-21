# FILE: bench/run_bench.ps1
# CODEGEN-BEGIN: bench-run-ps1
# CODEGEN-BEGIN: bench-jars-dir-ps1
$ErrorActionPreference = "Stop"

# Repo root = parent of bench\
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot  = Split-Path -Parent $ScriptDir

# CLI arg takes precedence; else env:JARS_DIR; else data/weeks under repo root
param([string]$CliJarsDir = $null)

if ($CliJarsDir -and $CliJarsDir.Trim().Length -gt 0) {
  $JARS_DIR = $CliJarsDir
} elseif ($env:JARS_DIR) {
  $JARS_DIR = $env:JARS_DIR
} else {
  $JARS_DIR = "data/weeks"
}

# If relative, resolve against repo root
if (-not [System.IO.Path]::IsPathRooted($JARS_DIR)) {
  $JARS_DIR = Join-Path $RepoRoot $JARS_DIR
}

if (-not (Test-Path $JARS_DIR -PathType Container)) {
  throw "JARS_DIR does not exist: $JARS_DIR"
}
Write-Host "[INFO] Using JARS_DIR=$JARS_DIR"
# CODEGEN-END: bench-jars-dir-ps1

$CLI = ".\mapper-cli\build\install\mapper-cli\bin\mapper-cli.bat"
$OUT = "bench\out"
$CACHE = "bench\cache"
New-Item -Force -ItemType Directory $OUT  | Out-Null
New-Item -Force -ItemType Directory $CACHE| Out-Null

function Invoke-Cfg([string]$pair,[string]$cfg,[string]$old,[string]$new) {
  $outp = Join-Path $OUT ("{0}_{1}" -f $pair,$cfg)
  $tiny = "$outp.tiny"
  $json = "$outp.json"
  $common = "--deterministic --use-nsf64=canonical --report $json --cacheDir $CACHE"
  switch ($cfg) {
    "baseline_surrogate" { $cfgArgs="--use-nsf64=surrogate --wl-relaxed-l1=0 --nsf-near=0" }
    "phase3"            { $cfgArgs="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0" }
    "phase4"            { $cfgArgs="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.60" }
    "phase4_strict"     { $cfgArgs="--wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=2 --stack-cos=0.80" }
    "canonical_v1nsf"   { $cfgArgs="--enable-nsfv2=false --wl-relaxed-l1=2 --wl-size-band=0.10 --nsf-near=0" }
    default { throw "Unknown cfg $cfg" }
  }
  Write-Host "[RUN] $pair $cfg"
  & $CLI mapOldNew --old $old --new $new --out $tiny $common $cfgArgs | Out-Null
}

# Collect jars matching osrs-<int>.jar and sort numerically
# CODEGEN-BEGIN: bench-jars-find-ps1
$jars = Get-ChildItem -Path $JARS_DIR -Filter "osrs-*.jar" -File | Sort-Object Name
# CODEGEN-END: bench-jars-find-ps1
# CODEGEN-BEGIN: bench-jars-sanity-ps1
$entries = @()
foreach ($j in $jars) {
  if ($j.Name -match "^osrs-([0-9]+)\.jar$") {
    $entries += [PSCustomObject]@{ Ver = [int]$Matches[1]; Path = $j.FullName }
  }
}
$entries = $entries | Sort-Object Ver
if ($entries.Count -lt 2) { throw "Found fewer than two osrs-*.jar in $JARS_DIR" }
# CODEGEN-END: bench-jars-sanity-ps1

for ($i=1; $i -lt $entries.Count; $i++) {
  $old = $entries[$i-1]
  $new = $entries[$i]
  $pair = "osrs-$($old.Ver)-$($new.Ver)"
  Invoke-Cfg $pair "baseline_surrogate" $old.Path $new.Path
  Invoke-Cfg $pair "phase3"            $old.Path $new.Path
  Invoke-Cfg $pair "phase4"            $old.Path $new.Path
  # Optional:
  # Invoke-Cfg $pair "phase4_strict"   $old.Path $new.Path
  # Invoke-Cfg $pair "canonical_v1nsf" $old.Path $new.Path
}

Write-Host "[DONE] Reports in $OUT"
# CODEGEN-END: bench-run-ps1
