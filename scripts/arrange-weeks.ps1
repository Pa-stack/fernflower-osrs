<#
BEGIN-COPILOT PATCH: Anchor SRC/DEST to repo, restore overwrite guard, add -DryRun, and improve logging.
This patch makes the script independent of the current working directory by defaulting
SRC/DEST to the repository's data/weeks folder via $PSScriptRoot. It also re-enables the
overwrite skip behavior, and adds -DryRun for safe verification.
#>

Param(
  # Defaults anchored to repo root regardless of current working directory
  [string]$SRC = (Join-Path $PSScriptRoot "..\data\weeks"),
  [string]$DEST = (Join-Path $PSScriptRoot "..\data\weeks"),

  [int]$YEAR = 2025,
  [int]$CURRENT_WEEK = 34,
  [int]$LATEST_JAR = 232,

  # 1 = overwrite existing files; 0 = skip
  [int]$OVERWRITE = 0,

  # If set, simulate file operations
  [switch]$DryRun
)

function Resolve-Abs([string]$p) {
  try { return (Resolve-Path -LiteralPath $p).Path } catch { return [System.IO.Path]::GetFullPath($p) }
}

# Normalize to absolute paths for display and downstream ops
$SRC = Resolve-Abs $SRC
$DEST = Resolve-Abs $DEST

# BASE so that week = jar - BASE ; 232 -> 34 => 198
$BASE = $LATEST_JAR - $CURRENT_WEEK

Write-Host "Arrange OSRS jars:"
Write-Host "  SRC=$(Resolve-Abs $SRC)"
Write-Host "  DEST=$(Resolve-Abs $DEST)"
Write-Host "  YEAR=$YEAR  CURRENT_WEEK=$CURRENT_WEEK  LATEST_JAR=$LATEST_JAR  BASE=$BASE"
if ($DryRun) { Write-Host "  MODE=DRY-RUN (no changes will be made)" }
Write-Host ""

if (-not $DryRun) {
  New-Item -ItemType Directory -Force -Path $DEST | Out-Null
}

function Copy-One {
  param([string]$src, [string]$dst)

  if ((Test-Path -LiteralPath $dst) -and $OVERWRITE -ne 1) {
    Write-Host "SKIP (exists): $dst"
    return $true
  }
  if (!(Test-Path -LiteralPath $src)) {
    Write-Host "MISS (no source): $src"
    return $false
  }

  $dir = Split-Path -Parent $dst
  if ($DryRun) {
    Write-Host "DRY  mkdir -p $dir"
    Write-Host "DRY  copy $src -> $dst"
  } else {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Copy-Item -Force -LiteralPath $src -Destination $dst
    Write-Host "OK   $src -> $dst"
  }
  return $true
}

$fail = $false
for ($w = 1; $w -le $CURRENT_WEEK; $w++) {
  $newJar = $BASE + $w
  $oldJar = $newJar - 1
  $week = "{0:D2}" -f $w
  $weekDir = Join-Path $DEST "$YEAR-$week"

  $srcNew = Join-Path $SRC ("osrs-{0}.jar" -f $newJar)
  $srcOld = Join-Path $SRC ("osrs-{0}.jar" -f $oldJar)

  $dstNew = Join-Path $weekDir "new.jar"
  $dstOld = Join-Path $weekDir "old.jar"

  if (Test-Path -LiteralPath $srcNew) {
    if (-not (Copy-One -src $srcNew -dst $dstNew)) { $fail = $true }
    # old may not exist; that's okay
    Copy-One -src $srcOld -dst $dstOld | Out-Null
  } else {
    Write-Host ("SKIP week {0}-{1} (missing new: {2})" -f $YEAR, $week, $srcNew)
  }
}

Write-Host ""
Write-Host "Summary:"
Write-Host ("  Expected mapping:  {0} -> {1}-{2} (new), {3} (old)" -f $LATEST_JAR, $YEAR, ("{0:D2}" -f $CURRENT_WEEK), ($LATEST_JAR-1))
Write-Host ("  Example check:")
Write-Host ("    {0} => {1}" -f (Join-Path $SRC ("osrs-{0}.jar" -f $LATEST_JAR)), (Join-Path (Join-Path $DEST ("{0}-{1}" -f $YEAR, ("{0:D2}" -f $CURRENT_WEEK))) "new.jar"))
Write-Host ("    {0} => {1}" -f (Join-Path $SRC ("osrs-{0}.jar" -f ($LATEST_JAR-1))), (Join-Path (Join-Path $DEST ("{0}-{1}" -f $YEAR, ("{0:D2}" -f $CURRENT_WEEK))) "old.jar"))
Write-Host ""
if ($fail) { Write-Host "DONE with missing sources. Review 'MISS' lines." } else { Write-Host ("DONE.{0}" -f ($(if ($DryRun) { ' (dry-run)' } else { '' }))) }

# END-COPILOT PATCH
