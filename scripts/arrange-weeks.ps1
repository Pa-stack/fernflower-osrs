Param(
  [string]$SRC = "/data/weeks",
  [string]$DEST = "./data/weeks",
  [int]$YEAR = 2025,
  [int]$CURRENT_WEEK = 34,
  [int]$LATEST_JAR = 232,
  [int]$OVERWRITE = 0
)

# BASE so that week = jar - BASE ; 232 -> 34 => 198
$BASE = $LATEST_JAR - $CURRENT_WEEK

Write-Host "Arrange OSRS jars:"
Write-Host "  SRC=$SRC"
Write-Host "  DEST=$DEST"
Write-Host "  YEAR=$YEAR  CURRENT_WEEK=$CURRENT_WEEK  LATEST_JAR=$LATEST_JAR  BASE=$BASE"
Write-Host ""

New-Item -ItemType Directory -Force -Path $DEST | Out-Null

function Copy-One {
  param([string]$src, [string]$dst)
  if ((Test-Path $dst) -and $OVERWRITE -ne 1) {
    Write-Host "SKIP (exists): $dst"
    return $true
  }
  if (!(Test-Path $src)) {
    Write-Host "MISS (no source): $src"
    return $false
  }
  $dir = Split-Path -Parent $dst
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  Copy-Item -Force $src $dst
  Write-Host "OK  $src -> $dst"
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

  if (Test-Path $srcNew) {
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
Write-Host ("    {0}/osrs-{1}.jar  => {2}/{3}-{4}/new.jar" -f $SRC, $LATEST_JAR, $DEST, $YEAR, ("{0:D2}" -f $CURRENT_WEEK))
Write-Host ("    {0}/osrs-{1}.jar  => {2}/{3}-{4}/old.jar" -f $SRC, ($LATEST_JAR-1), $DEST, $YEAR, ("{0:D2}" -f $CURRENT_WEEK))
Write-Host ""
if ($fail) { Write-Host "DONE with missing sources. Review 'MISS' lines." } else { Write-Host "DONE." }
