#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVIDENCE_DIR="${PERSONAL_DASHBOARD_EVIDENCE_DIR:-/tmp/personal-dashboard-evidence}"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/../../../../../xanzc_frontend" && pwd)"
CLI="$FRONTEND_DIR/node_modules/.bin/playwright-cli"
NODE_BIN="/Users/likewang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin"
SESSION="personal-qa"
BASE_URL="${PERSONAL_DASHBOARD_BASE_URL:-http://127.0.0.1:8092}"

export PATH="$NODE_BIN:$PATH"
mkdir -p "$EVIDENCE_DIR"
cd "$FRONTEND_DIR"

exec > >(tee "$EVIDENCE_DIR/commands.log") 2>&1

echo "# 个人经营驾驶舱官方 CLI 验收"
echo "# 时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
echo "# 模式：仅开发态 mock，非联调"
echo "# 前端：$BASE_URL"
echo "# CLI：$CLI"
echo "# 会话：$SESSION"

curl -fsS --max-time 5 "$BASE_URL/" >/dev/null
"$CLI" -s="$SESSION" close >/dev/null 2>&1 || true
"$CLI" -s="$SESSION" open about:blank --browser=chrome
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/register-mocks.js" > "$EVIDENCE_DIR/routes.raw.txt"

capture_base() {
  local width="$1"
  local height="$2"
  local suffix="${width}x${height}"
  "$CLI" -s="$SESSION" goto "$BASE_URL/?qa=base#/personal-dashboard"
  "$CLI" -s="$SESSION" resize "$width" "$height"
  "$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/inject-qa-label.js"
  "$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-base.js" > "$EVIDENCE_DIR/assertions-base-${suffix}.json"
  "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/base-${suffix}.png"
  "$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/base-${suffix}-full.png" --full-page
}

capture_base 1920 1080
capture_base 1440 900
capture_base 390 844

"$CLI" -s="$SESSION" console --clear
"$CLI" -s="$SESSION" requests --clear
"$CLI" -s="$SESSION" goto "$BASE_URL/?qa=base#/personal-dashboard"
"$CLI" -s="$SESSION" resize 1920 1080
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/inject-qa-label.js"
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-interactions.js" > "$EVIDENCE_DIR/assertions-interactions.json"
"$CLI" -s="$SESSION" console > "$EVIDENCE_DIR/console-interactions.raw.txt" || true
"$CLI" -s="$SESSION" requests > "$EVIDENCE_DIR/requests-interactions.raw.txt" || true

"$CLI" -s="$SESSION" console --clear
"$CLI" -s="$SESSION" requests --clear
"$CLI" -s="$SESSION" goto "$BASE_URL/?qa=partial#/personal-dashboard"
"$CLI" -s="$SESSION" resize 1920 1080
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/inject-qa-label.js"
"$CLI" -s="$SESSION" run-code --filename "$SCRIPT_DIR/assert-partial.js" > "$EVIDENCE_DIR/assertions-partial.json"
"$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/partial-1920x1080.png"
"$CLI" -s="$SESSION" screenshot --filename "$EVIDENCE_DIR/partial-1920x1080-full.png" --full-page
"$CLI" -s="$SESSION" console > "$EVIDENCE_DIR/console-partial.raw.txt" || true
"$CLI" -s="$SESSION" requests > "$EVIDENCE_DIR/requests-partial.raw.txt" || true

"$CLI" -s="$SESSION" console --clear
"$CLI" -s="$SESSION" requests --clear
"$CLI" -s="$SESSION" goto "$BASE_URL/?qa=unauth#/personal-dashboard"
"$CLI" -s="$SESSION" resize 390 844
"$CLI" -s="$SESSION" run-code 'async page => { await page.waitForTimeout(800); const hash = await page.evaluate(() => location.hash); const welcome = await page.getByText("欢迎登录", { exact: true }).count(); const uias = await page.getByRole("button", { name: "统一认证登录" }).count(); if (!hash.startsWith("#/login") || (!welcome && !uias)) throw new Error(`unauth guard assertion failed: ${hash}`); return { url: page.url(), hash, welcomeVisible: welcome > 0, unifiedAuthButton: uias > 0 }; }' > "$EVIDENCE_DIR/assertions-unauth.json"
"$CLI" -s="$SESSION" console > "$EVIDENCE_DIR/console-unauth.raw.txt" || true
"$CLI" -s="$SESSION" requests > "$EVIDENCE_DIR/requests-unauth.raw.txt" || true

if rg -n '^### Error|^Error:' "$EVIDENCE_DIR/commands.log" "$EVIDENCE_DIR"/assertions-*.json; then
  echo 'CLI 验收断言或命令出现错误，停止并保留原始日志。' >&2
  exit 1
fi

echo "证据目录：$EVIDENCE_DIR"
