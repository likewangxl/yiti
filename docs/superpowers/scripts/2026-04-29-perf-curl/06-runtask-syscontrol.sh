#!/usr/bin/env bash
# 06-runtask-syscontrol.sh — PerfRunTask 2 端点 + SysControl 5 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

TS="$(state_get TS_PREFIX)"
TODAY="$(date +%Y-%m-%d)"

section "E.1 GET /api/perf/run-tasks 列表"
call_assert GET "/api/perf/run-tasks?pageNo=1&pageSize=20"          200 0 "默认分页"
call_assert GET "/api/perf/run-tasks?taskType=METRIC_TRIAL"         200 0 "按 taskType 过滤"

section "E.2 GET /api/perf/run-tasks/{id} 详情"
TASK_ID="$(state_get EXEC_TASK_ID)"
if [ -n "$TASK_ID" ]; then
  call_assert GET "/api/perf/run-tasks/$TASK_ID"                    200 0 "命中 02-metric.sh execute 产生的任务"
else
  # 从列表里随便取一条
  call GET "/api/perf/run-tasks?pageNo=1&pageSize=1"
  TID="$(json_get "$LAST_BODY" data.list.0.id)"
  if [ -n "$TID" ]; then
    call_assert GET "/api/perf/run-tasks/$TID"                      200 0 "取列表首条"
  else
    skip "run-tasks 表无数据"
  fi
fi
call_assert GET "/api/perf/run-tasks/NOT_EXIST_$TS"                 200 PERF-40007 "不存在 → PERF-40007 (HTTP 200 业务错误)"

section "F.1 GET /api/perf/sys-control 当前生效（无 valid 版本时返回 PERF-40012 也算端点正常）"
for dim in EMP ORG CUST; do
  call GET "/api/perf/sys-control?scopeDim=$dim"
  c="$(json_get "$LAST_BODY" code)"
  if [ "$LAST_CODE" = "200" ] && { [ "$c" = "0" ] || [ "$c" = "PERF-40012" ]; }; then
    ok "GET sys-control scopeDim=$dim  http=200 code=$c"
  else
    fail "GET sys-control scopeDim=$dim  http=$LAST_CODE code=$c"
  fi
done

section "F.2 GET /api/perf/sys-control/history"
call_assert GET "/api/perf/sys-control/history?scopeDim=EMP&pageNo=1&pageSize=20"  200 0 "EMP 版本历史"

section "F.3 POST /api/perf/sys-control/init 初始化 (幂等性测试)"
call POST /api/perf/sys-control/init '{"reason":"curl test re-init"}'
info "init 二次调用 http=$LAST_CODE code=$(json_get "$LAST_BODY" code) msg=$(json_get "$LAST_BODY" message)"

section "F.4 POST /api/perf/sys-control/switch-version (高危)"
# 取当前 EMP 版本+1 作为新版本号尝试切换；该路径多半因 newVersion 不存在而拒绝，只验证报文路径
call GET "/api/perf/sys-control?scopeDim=EMP"
CUR_VER="$(json_get "$LAST_BODY" data.dataVersion)"
[ -z "$CUR_VER" ] && CUR_VER="$(json_get "$LAST_BODY" data.version)"
SWITCH_BODY=$(cat <<JSON
{"scopeDim":"EMP","dataDate":"$TODAY","newVersion":"V${TS}","reason":"curl test switch (likely fail)"}
JSON
)
call POST /api/perf/sys-control/switch-version "$SWITCH_BODY"
info "switch http=$LAST_CODE code=$(json_get "$LAST_BODY" code) — 期望 4xx (新版本未注册)"

section "F.5 POST /api/perf/sys-control/rollback (高危)"
ROLLBACK_BODY=$(cat <<JSON
{"scopeDim":"EMP","rollbackTo":"NON_EXIST_$TS","reason":"curl test rollback (likely fail)"}
JSON
)
call POST /api/perf/sys-control/rollback "$ROLLBACK_BODY"
info "rollback http=$LAST_CODE code=$(json_get "$LAST_BODY" code) — 期望 4xx"

print_summary
