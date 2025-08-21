Param(
  [string]$Week,
  [string]$Old,
  [string]$New
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent
Set-Location $root

$OUT = "out"
$NSF = "mapper-cli/build/nsf-jsonl"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

# Resolve jars
if (-not $Old -or -not $New) {
  if ($Week -and (Test-Path "data/weeks/$Week")) {
    if (Test-Path "data/weeks/$Week/old.jar" -and Test-Path "data/weeks/$Week/new.jar") {
      $Old = "data/weeks/$Week/old.jar"
      $New = "data/weeks/$Week/new.jar"
    }
  }
}
if (-not $Old -or -not $New) {
  $jars = Get-ChildItem -Path "data/weeks" -Filter "osrs-*.jar" -File -ErrorAction SilentlyContinue | Sort-Object Name
  if ($jars.Count -ge 2) {
    $Old = $jars[0].FullName
    $New = $jars[1].FullName
  } else {
    Write-Error "No suitable JARs found. Provide -Week or -Old/-New."
  }
}

Write-Host "Using OLD=$Old"
Write-Host "Using NEW=$New"

# Build CLI dist
./gradlew.bat :mapper-cli:installDist | Out-Null

$cmd = @(
  'mapOldNew','--old', $Old,'--new', $New,'--out', "$OUT/v2-a.tiny",
  '--deterministic','--use-nsf64=canonical','--wl-relaxed-l1=2','--wl-size-band=0.10',
  '--report', "$OUT/report-a.json", '--dump-normalized-features='+$NSF
)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
./gradlew.bat :mapper-cli:run --args="$(($cmd -join ' '))" --no-daemon | Out-Null
$mid = $sw.Elapsed.TotalSeconds
$cmdB = $cmd.Clone()
$cmdB[$cmdB.IndexOf('--out')+1] = "$OUT/v2-b.tiny"
$cmdB[$cmdB.IndexOf('--report')+1] = "$OUT/report-b.json"
./gradlew.bat :mapper-cli:run --args="$(($cmdB -join ' '))" --no-daemon | Out-Null
$sw.Stop()

# Determinism
$det = (Compare-Object (Get-Content -Raw "$OUT/v2-a.tiny") (Get-Content -Raw "$OUT/v2-b.tiny")).Count -eq 0

# NSFv2 checks
$nsfOk = $false; $stackOk = $false; $invkOk = $false
if (Test-Path "$NSF/old.jsonl" -and Test-Path "$NSF/new.jsonl") {
  $nsfOk = Select-String -Path "$NSF/old.jsonl","$NSF/new.jsonl" -Pattern '"nsf_version"\s*:\s*"NSFv2"' -Quiet
  $stackOk = Select-String -Path "$NSF/old.jsonl","$NSF/new.jsonl" -Pattern '"stackHist":\{\"-2\":[0-9]+,\"-1\":[0-9]+,\"0\":[0-9]+,\"\+1\":[0-9]+,\"\+2\":[0-9]+\}' -Quiet
  $invkOk = Select-String -Path "$NSF/old.jsonl","$NSF/new.jsonl" -Pattern '"invokeKindCounts"\s*:\s*\[' -Quiet
}

# WL-relaxed + flattening telemetry
$l1Ok = $false; $bandOk = $false; $wlr = 'not observed'; $flat = 'not observed'
if (Test-Path "$OUT/report-a.json") {
  $l1Ok = Select-String -Path "$OUT/report-a.json" -Pattern '"wl_relaxed_l1"\s*:\s*2' -Quiet
  $bandOk = Select-String -Path "$OUT/report-a.json" -Pattern '"wl_relaxed_size_band"\s*:\s*0\.1' -Quiet
  $wlrMatches = Select-String -Path "$OUT/report-a.json" -Pattern '"wl_relaxed_(gate_passes|candidates|hits|accepted)"\s*:\s*[0-9]+' -AllMatches
  if ($wlrMatches) { $wlr = ($wlrMatches.Matches.Value -join ' ') }
  $flatMatches = Select-String -Path "$OUT/report-a.json" -Pattern '"flattening_detected"\s*:\s*[0-9]+|"near_before_gates"\s*:\s*[0-9]+|"near_after_gates"\s*:\s*[0-9]+' -AllMatches
  if ($flatMatches) { $flat = ($flatMatches.Matches.Value -join ' ') }
}

$runA = [Math]::Round($mid,2)
$runB = [Math]::Round(($sw.Elapsed.TotalSeconds - $mid),2)

Write-Output @"
=== Sanity Check Report (pwsh) ===
A) Results
- Determinism (tiny A==B): $det
- NSFv2 dumps: nsf_version(NSFv2)=$nsfOk, stackHist order=$stackOk, invokeKindCounts=$invkOk
- WL-relaxed defaults: l1=2? $l1Ok, size_band=0.10? $bandOk; counters: $wlr
- Flattening telemetry: $flat
- Perf (secs): runA=$runA, runB=$runB

B) Next steps if fields are "not observed":
- Try different JAR pairs via -Old/-New or a different -Week.
"@
