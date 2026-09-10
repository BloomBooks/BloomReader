#!/bin/bash
# Pull Bloom Reader translations from Crowdin via the REST API and apply them to the repo.
# Read-only on Crowdin: it only requests file exports (no content changes).
#
# Usage: BLOOM_CROWDIN_TOKEN=... l10n/downloadFromCrowdin.sh
# Requires: curl, python 3. Run from anywhere; REPO defaults to the checkout containing this script.
# Exits nonzero if any language failed to export or download, so a partial refresh is never mistaken
# for a complete one.
#
# Why per-file exports rather than a project build: Crowdin's project-wide "build" can serve a
# cached export that lags behind recently added suggestions by many minutes, while the
# per-file export endpoint always reflects the current translations.
set -euo pipefail
REPO="${REPO:-$(cd "$(dirname "$0")/.." && pwd)}"
PROJECT=261564
FILE_ID=67   # /master/strings.xml, titled "Bloom Reader" in the SIL-Bloom project
API="https://api.crowdin.com/api/v2/projects/$PROJECT"
: "${BLOOM_CROWDIN_TOKEN:?set BLOOM_CROWDIN_TOKEN}"

# pick a Python 3 that actually runs (on Windows, "python3" may be a Store stub that only prints a message)
PY=""
for c in python3 python; do
  if "$c" -c 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)' >/dev/null 2>&1; then PY=$c; break; fi
done
[ -n "$PY" ] || { echo "python 3 is required"; exit 1; }
TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
# keep the token out of the process argument list: curl reads the auth header as config from stdin
api() { printf 'header = "Authorization: Bearer %s"\n' "$BLOOM_CROWDIN_TOKEN" | curl -sf -K - "$@"; }

cd "$REPO/app/src/main/res"

echo "Fetching target languages..."
# sorted so that, if two locales ever share a prefix, which one wins is deterministic (and reported)
LANGS=$(api "$API" | "$PY" -c "import sys,json; print(' '.join(sorted(json.load(sys.stdin)['data']['targetLanguageIds'])))")

declare -A SEEN
FAILED=()
for lang in $LANGS; do
  # Crowdin's %two_letters_code%: the part before any region/variant (es-ES -> es, zh-CN -> zh, qaa-x-test -> qaa)
  code=${lang%%-*}
  if [ -n "${SEEN[$code]:-}" ]; then
    echo "  $lang: SKIPPED - would overwrite values-$code already written from ${SEEN[$code]}; add an explicit mapping"
    FAILED+=("$lang (collides with ${SEEN[$code]})"); continue
  fi
  resp=$(api -X POST -H 'Content-Type: application/json' \
        -d "{\"targetLanguageId\":\"$lang\",\"fileIds\":[$FILE_ID]}" "$API/translations/exports") \
    || { echo "  $lang: export request FAILED"; FAILED+=("$lang (export request)"); continue; }
  url=$(printf '%s' "$resp" | "$PY" -c "import sys,json; print(json.load(sys.stdin)['data']['url'])")
  # download to a temp file first so a failed download can never truncate a tracked strings.xml
  if ! curl -sf "$url" -o "$TMP" || [ ! -s "$TMP" ]; then
    echo "  $lang: download FAILED"; FAILED+=("$lang (download)"); continue
  fi
  mkdir -p "values-$code"
  # keep CRLF like the rest of the repo working copy
  "$PY" -c "import sys; d=open(sys.argv[1],'rb').read().replace(b'\r\n',b'\n').replace(b'\n',b'\r\n'); open(sys.argv[2],'wb').write(d)" "$TMP" "values-$code/strings.xml"
  SEEN[$code]=$lang
  echo "  $lang -> values-$code"
done

echo "Running l10n/processLocalizations.sh..."
bash "$REPO/l10n/processLocalizations.sh"
cd "$REPO"
echo
echo "Tracked files with real changes:"; git diff --stat -- app/src/main/res
echo
echo "NEW (untracked) language folders - per l10n/README.md, delete these unless deciding to add them:"
git status --short -- 'app/src/main/res/values-*' | grep '^??' || echo "  (none)"
echo "To drop only those folders: git clean -fd -- 'app/src/main/res/values-*/'"

if [ ${#FAILED[@]} -gt 0 ]; then
  echo
  echo "INCOMPLETE: ${#FAILED[@]} language(s) were not refreshed:"
  printf '  %s\n' "${FAILED[@]}"
  exit 1
fi
