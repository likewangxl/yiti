#!/usr/bin/env bash
set -u

session_name="$1"
shift
eval_function="$(<artifacts/ui-ux-pro-max-final/scripts/eval-route.js)"
cli="node_modules/.bin/playwright-cli"

for route_path in "$@"; do
  printf '### ROUTE %s\n' "$route_path"
  NO_UPDATE_NOTIFIER=1 PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright "$cli" "-s=$session_name" goto "http://127.0.0.1:8091/#$route_path"
  NO_UPDATE_NOTIFIER=1 PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright "$cli" "-s=$session_name" run-code "async page => { await page.waitForTimeout(900); }"
  NO_UPDATE_NOTIFIER=1 PLAYWRIGHT_BROWSERS_PATH=/home/djdev/.cache/ms-playwright "$cli" "-s=$session_name" --raw eval "$eval_function"
done
