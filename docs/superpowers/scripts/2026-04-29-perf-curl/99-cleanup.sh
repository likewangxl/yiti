#!/usr/bin/env bash
# 99-cleanup.sh — 删除本次 TS_PREFIX 创建的种子数据，让脚本可重跑
#
# 顺序：先删依赖 (target-value 由 plan 删除时连带；adjust 申请已撤回；KPI publish 后无法删除，软处理)
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

L1="$(state_get METRIC_L1)"
L2="$(state_get METRIC_L2)"
SCHEME_ID="$(state_get SCHEME_ID)"
EXTRA_ID="$(state_get KPI_EXTRA_ID)"
PLAN_ID="$(state_get PLAN_ID)"
EXTRA_PLAN_ID="$(state_get EXTRA_PLAN_ID)"

section "Cleanup target plans"
info "TargetPlanController 无 DELETE 端点（仅 GET/POST/PUT），仅依赖 TS_PREFIX 隔离"
info "  保留的 plan: PLAN_ID=$PLAN_ID  EXTRA_PLAN_ID=$EXTRA_PLAN_ID"

section "Cleanup KPI schemes"
for sid in "$EXTRA_ID" "$SCHEME_ID"; do
  [ -z "$sid" ] && continue
  call DELETE "/api/perf/kpi-schemes/$sid" '{"reason":"curl test cleanup"}'
  info "DELETE scheme $sid http=$LAST_CODE code=$(json_get "$LAST_BODY" code)"
done

section "Cleanup metrics"
# 先 disable 再 delete（02-metric.sh 已 delete L2，但重跑可能漏）
for mc in "$L2" "$L1"; do
  [ -z "$mc" ] && continue
  call PUT "/api/perf/metrics/$mc/status" '{"status":"DISABLED","reason":"cleanup"}'
  info "DISABLE $mc http=$LAST_CODE"
  call DELETE "/api/perf/metrics/$mc" '{"reason":"curl test cleanup"}'
  info "DELETE $mc http=$LAST_CODE code=$(json_get "$LAST_BODY" code)"
done

section "Reset state file"
> "$STATE_FILE"
ok "state file cleared"

print_summary
