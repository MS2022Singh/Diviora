#!/usr/bin/env bash
# Usage: scripts/verify-restore.sh <tag-or-commit> [remote-url]
# Fresh-clones the remote (default: origin), checks out the ref, builds and runs unit tests.
set -euo pipefail
REF="${1:?tag or commit required}"
URL="${2:-$(git remote get-url origin)}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo ">> cloning $URL into $TMP"
git clone --quiet "$URL" "$TMP/repo"
cd "$TMP/repo"
git -c advice.detachedHead=false checkout --quiet "$REF"
echo ">> HEAD $(git rev-parse --short HEAD) ($REF)"
if [ -x ./gradlew ]; then
  ./gradlew --no-daemon -q testDebugUnitTest test assembleDebug
  ls -1 app/build/outputs/apk/debug/*.apk
else
  echo ">> no gradle wrapper at this ref (bootstrap); checking tree only"; git ls-files | head -50
fi
echo "RESTORE-VERIFY PASS: $REF"
