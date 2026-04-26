#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   GITHUB_TOKEN=... GITHUB_REPO=owner/repo [GITHUB_BRANCH=main] [WORKFLOW_FILE=android-debug-apk.yml] bash scripts/auto_build_install_shizuku.sh

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd python3
require_cmd unzip
require_cmd shizuku

: "${GITHUB_TOKEN:?Set GITHUB_TOKEN with repo/workflow permissions}"
: "${GITHUB_REPO:?Set GITHUB_REPO as owner/repo}"
GITHUB_BRANCH="${GITHUB_BRANCH:-main}"
WORKFLOW_FILE="${WORKFLOW_FILE:-android-debug-apk.yml}"
APK_NAME="${APK_NAME:-app-debug.apk}"
WORKDIR="${WORKDIR:-/tmp/hola_ci}"

API="https://api.github.com/repos/${GITHUB_REPO}"
HEADERS=(
  -H "Authorization: Bearer ${GITHUB_TOKEN}"
  -H "Accept: application/vnd.github+json"
  -H "X-GitHub-Api-Version: 2022-11-28"
)

mkdir -p "$WORKDIR"

echo "1) Trigger workflow: ${WORKFLOW_FILE} on ${GITHUB_BRANCH}"
curl -fsS -X POST "${API}/actions/workflows/${WORKFLOW_FILE}/dispatches" \
  "${HEADERS[@]}" \
  -d "{\"ref\":\"${GITHUB_BRANCH}\"}" >/dev/null

sleep 4

echo "2) Find latest workflow run"
RUNS_JSON="$(curl -fsS "${API}/actions/workflows/${WORKFLOW_FILE}/runs?branch=${GITHUB_BRANCH}&event=workflow_dispatch&per_page=5" "${HEADERS[@]}")"
RUN_ID="$(python3 - <<'PY' "$RUNS_JSON"
import json,sys
obj=json.loads(sys.argv[1])
runs=obj.get('workflow_runs',[])
print(runs[0]['id'] if runs else '')
PY
)"

if [ -z "$RUN_ID" ]; then
  echo "No run found after dispatch." >&2
  exit 1
fi

echo "Run id: $RUN_ID"

echo "3) Wait for completion"
for _ in $(seq 1 120); do
  RUN_JSON="$(curl -fsS "${API}/actions/runs/${RUN_ID}" "${HEADERS[@]}")"
  STATUS="$(python3 - <<'PY' "$RUN_JSON"
import json,sys
o=json.loads(sys.argv[1]); print(o.get('status',''))
PY
)"
  CONCLUSION="$(python3 - <<'PY' "$RUN_JSON"
import json,sys
o=json.loads(sys.argv[1]); print(o.get('conclusion',''))
PY
)"
  echo "status=${STATUS} conclusion=${CONCLUSION}"
  if [ "$STATUS" = "completed" ]; then
    if [ "$CONCLUSION" != "success" ]; then
      echo "Workflow failed: ${CONCLUSION}" >&2
      html_url="$(python3 - <<'PY' "$RUN_JSON"
import json,sys
o=json.loads(sys.argv[1]); print(o.get('html_url',''))
PY
)"
      echo "Logs: ${html_url}" >&2
      exit 1
    fi
    break
  fi
  sleep 10
done

echo "4) Download artifact"
ART_JSON="$(curl -fsS "${API}/actions/runs/${RUN_ID}/artifacts" "${HEADERS[@]}")"
ART_ID="$(python3 - <<'PY' "$ART_JSON"
import json,sys
o=json.loads(sys.argv[1]); arts=o.get('artifacts',[])
print(arts[0]['id'] if arts else '')
PY
)"

if [ -z "$ART_ID" ]; then
  echo "No artifacts found in run ${RUN_ID}" >&2
  exit 1
fi

ZIP_PATH="${WORKDIR}/artifact.zip"
curl -fsSL "${API}/actions/artifacts/${ART_ID}/zip" "${HEADERS[@]}" -o "$ZIP_PATH"

EXTRACT_DIR="${WORKDIR}/artifact"
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"
unzip -o "$ZIP_PATH" -d "$EXTRACT_DIR" >/dev/null

APK_PATH="$(find "$EXTRACT_DIR" -type f -name "$APK_NAME" | head -n 1)"
if [ -z "$APK_PATH" ]; then
  echo "${APK_NAME} not found in artifact" >&2
  exit 1
fi

echo "APK downloaded: $APK_PATH"

echo "5) Install via Shizuku"
REMOTE_APK="/data/local/tmp/${APK_NAME}"
shizuku sh -c "cat > '${REMOTE_APK}'" < "$APK_PATH"
shizuku pm install -r "$REMOTE_APK"

echo "Done. Installed ${APK_NAME} from CI run ${RUN_ID}."
