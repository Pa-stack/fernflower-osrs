# >>> AUTOGEN: BYTECODEMAPPER SCRIPT SMOKE SH BEGIN
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
./gradlew :mapper-cli:smoke
# >>> AUTOGEN: BYTECODEMAPPER SCRIPT SMOKE SH END
