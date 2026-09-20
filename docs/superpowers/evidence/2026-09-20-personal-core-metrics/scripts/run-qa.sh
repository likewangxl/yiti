#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../../../../../xanzc_frontend" && pwd)"
 CLI="$FRONTEND_DIR/node_modules/.bin/playwright-cli"
 NODE_BIN="/Users/likewang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin"
 EVIDENCE_DIR="${PERSONAL_CORE_METRICS_EVIDENCE_DIR:-/tmp/personal-core-metrics-evidence}"
 BASE_URL="${PERSONAL_CORE_METRICS_BASE_URL:-http://127.0.0.1:8092}"
 SESSION="personal-core-metrics"

 export PATH="$NODE_BIN:$PATH"
 mkdir -p "$EVIDENCE_DIR"
 cd "$FRONTEND_DIR"
 exec > >(tee "$EVIDENCE_DIR/commands.log") 2>&1

 echo "# 个人核心指标官方 CLI 验收"
 echo "# 模式：仅开发态 mock，非联调"
 echo "# 页面：$BASE_URL/#/personal-dashboard"
 echo "# 视口：1440x900"
 echo "# CLI：$CLI"
 echo "# 会话：$SESSION"

 curl -fsS --max-time 5 "$BASE_URL/" >/dev/null
 "$CLI" -s="$SESSION" close >/dev/null 2>&1 || true
 "$CLI" -s="$SESSION" open about:blank --browser=chrome
 "$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/register-mocks.js" > "$EVIDENCE_DIR/routes.raw.txt"
 "$CLI" -s="$SESSION" console --clear
 "$CLI" -s="$SESSION" requests --clear

 "$CLI" -s="$SESSION" goto "$BASE_URL/?qa=core#/personal-dashboard"
 "$CLI" -s="$SESSION" resize 1440 900
 "$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-core.js" > "$EVIDENCE_DIR/assertions-core.json"
 "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/core-1440x900.png"
 "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/core-1440x900-full.png" --full-page

 "$CLI" -s="$SESSION" goto "$BASE_URL/?qa=empty#/personal-dashboard"
 "$CLI" -s="$SESSION" resize 1440 900
 "$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-empty.js" > "$EVIDENCE_DIR/assertions-empty.json"
 "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/empty-1440x900.png"
 "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/empty-1440x900-full.png" --full-page

"$CLI" -s="$SESSION" route-list > "$EVIDENCE_DIR/routes-list.raw.txt"
"$CLI" -s="$SESSION" goto "$BASE_URL/?qa=dated-empty#/personal-dashboard"
"$CLI" -s="$SESSION" resize 1440 900
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-dated-empty.js" > "$EVIDENCE_DIR/assertions-dated-empty.json"
"$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/dated-empty-1440x900.png"
"$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/dated-empty-1440x900-full.png" --full-page
 "$CLI" -s="$SESSION" console > "$EVIDENCE_DIR/console.raw.txt" || true
 "$CLI" -s="$SESSION" requests --filter '/api/' > "$EVIDENCE_DIR/requests.raw.txt" || true

 if rg -n '^### Error|^Error:' "$EVIDENCE_DIR/commands.log" "$EVIDENCE_DIR"/assertions-*.json; then
   echo 'CLI 验收断言失败，保留原始证据并停止。' >&2
   exit 1
 fi
 echo "证据目录：$EVIDENCE_DIR"
