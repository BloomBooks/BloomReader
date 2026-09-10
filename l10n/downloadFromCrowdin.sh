#!/bin/bash
# Pull Bloom Reader translations from Crowdin via the REST API and apply them to the repo.
# Read-only on Crowdin: it only requests file exports (no content changes).
#
# Usage: BLOOM_CROWDIN_TOKEN=... l10n/downloadFromCrowdin.sh
# Requires: curl, python. Run from anywhere; REPO defaults to the checkout containing this script.
#
# Why per-file exports rather than a project build: Crowdin's project-wide "build" can serve a
# cached export that lags behind recently added suggestions by many minutes, while the
# per-file export endpoint always reflects the current translations.
set -euo pipefail
REPO="${REPO:-$(cd "$(dirname "$0")/.." && pwd)}"
PROJECT=261564
FILE_ID=67   # /master/strings.xml, titled "Bloom Reader" in the SIL-Bloom project
API="https://api.crowdin.com/api/v2/projects/$PROJECT"
H="Authorization: Bearer ${BLOOM_CROWDIN_TOKEN:?set BLOOM_CROWDIN_TOKEN}"

cd "$REPO/app/src/main/res"

echo "Fetching target languages..."
LANGS=$(curl -sf -H "$H" "$API" | python -c "import sys,json; print(' '.join(json.load(sys.stdin)['data']['targetLanguageIds']))")

for lang in $LANGS; do
  # Crowdin's %two_letters_code%: the part before any region/variant (es-ES -> es, zh-CN -> zh, qaa-x-test -> qaa)
  code=${lang%%-*}
  resp=$(curl -sf -X POST -H "$H" -H 'Content-Type: application/json' \
        -d "{\"targetLanguageId\":\"$lang\",\"fileIds\":[$FILE_ID]}" "$API/translations/exports") \
    || { echo "  $lang: export request FAILED, skipping"; continue; }
  url=$(printf '%s' "$resp" | python -c "import sys,json; print(json.load(sys.stdin)['data']['url'])")
  mkdir -p "values-$code"
  # keep CRLF like the rest of the repo working copy
  curl -sf "$url" | python -c "import sys; d=sys.stdin.buffer.read().replace(b'\r\n',b'\n').replace(b'\n',b'\r\n'); open(sys.argv[1],'wb').write(d)" "values-$code/strings.xml"
  echo "  $lang -> values-$code"
done

echo "Running l10n/processLocalizations.sh (mv errors for absent region dirs are harmless)..."
(cd "$REPO/l10n" && bash ./processLocalizations.sh) || true
cd "$REPO"
echo
echo "Tracked files with real changes:"; git diff --stat -- app/src/main/res
echo
echo "NEW (untracked) languages - per l10n/README.md, delete these unless deciding to add them:"
git status --short app/src/main/res | grep '^??' || echo "  (none)"
echo "To drop them: git clean -fd app/src/main/res"
