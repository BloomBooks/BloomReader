#!/bin/bash
# Pull Bloom Reader translations from Crowdin via the REST API and apply them to the repo.
# Read-only on Crowdin except for triggering a translation export build (no content changes).
# Usage: BLOOM_CROWDIN_TOKEN=... ./update-l10n-from-crowdin.sh [--reuse-build ID]
# Requires: curl, python3. Run from anywhere; REPO below is the BloomReader checkout.
set -euo pipefail
REPO="${REPO:-$(cd "$(dirname "$0")/.." && pwd)}"
PROJECT=261564
API="https://api.crowdin.com/api/v2/projects/$PROJECT"
H="Authorization: Bearer ${BLOOM_CROWDIN_TOKEN:?set BLOOM_CROWDIN_TOKEN}"
WORK="${WORK:-$(mktemp -d)}"

if [ "${1:-}" = "--reuse-build" ]; then
  BID="$2"
else
  echo "Requesting translation build..."
  BID=$(curl -sf -X POST -H "$H" -H 'Content-Type: application/json' -d '{}' "$API/translations/builds" \
        | python -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
  while :; do
    ST=$(curl -sf -H "$H" "$API/translations/builds/$BID" | python -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
    echo "  build $BID: $ST"; [ "$ST" = finished ] && break; sleep 3
  done
fi
URL=$(curl -sf -H "$H" "$API/translations/builds/$BID/download" | python -c "import sys,json; print(json.load(sys.stdin)['data']['url'])")
ZIP="$WORK/crowdin-build-$BID.zip"; curl -sf "$URL" -o "$ZIP"; echo "Downloaded $ZIP"

cd "$REPO"
python - "$ZIP" <<'PY'
import zipfile,sys,os
z=zipfile.ZipFile(sys.argv[1]); n=0
for name in z.namelist():
    if name.startswith('master/app/src/main/res/values-') and name.endswith('strings.xml'):
        dest=name[len('master/'):]; os.makedirs(os.path.dirname(dest),exist_ok=True)
        # keep CRLF like the rest of the repo working copy
        open(dest,'wb').write(z.read(name).replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')); n+=1
print('extracted',n,'strings.xml files')
PY
echo "Running l10n/processLocalizations.sh (mv errors for absent region dirs are harmless)..."
(cd l10n && bash ./processLocalizations.sh) || true
echo
echo "Tracked files with real changes:"; git diff --stat
echo
echo "NEW (untracked) languages - per l10n/README.md, delete these unless deciding to add them:"
git status --short app/src/main/res | grep '^??' || echo "  (none)"
echo "To drop them: git clean -fd app/src/main/res"
