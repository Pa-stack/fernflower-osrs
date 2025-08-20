# >>> AUTOGEN: BYTECODEMAPPER SCRIPT SMOKE PS1 BEGIN
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
./gradlew.bat :mapper-cli:smoke
# >>> AUTOGEN: BYTECODEMAPPER SCRIPT SMOKE PS1 END
