#!/usr/bin/env bash
# 04-target.sh — TargetPlanController + TargetValueController 7 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

PLAN_ID="$(state_get PLAN_ID)"
PLAN_CODE="$(state_get PLAN_CODE)"
SCHEME_ID="$(state_get SCHEME_ID)"
L1="$(state_get METRIC_L1)"
EMP="$(state_get EMP_ID)"
TS="$(state_get TS_PREFIX)"

section "C.1 GET /api/perf/target-plans 列表"
call_assert GET "/api/perf/target-plans?pageNo=1&pageSize=20"           200 0 "默认分页"
call_assert GET "/api/perf/target-plans?status=ACTIVE"                  200 0 "按状态过滤"

section "C.2 GET /api/perf/target-plans/{id} 详情"
if [ -n "$PLAN_ID" ]; then
  call_assert GET "/api/perf/target-plans/$PLAN_ID"                     200 0 "命中详情"
fi
call_assert GET "/api/perf/target-plans/NOT_EXIST_${TS}"                200 PERF-40004 "不存在 → PERF-40004 (HTTP 200 业务错误)"

section "C.3 POST 新增目标方案 (副本)"
EXTRA_CODE="TP_TST_${TS}_2"
TODAY="$(date +%Y-%m-%d)"
BODY=$(cat <<JSON
{"planCode":"$EXTRA_CODE","planName":"目标副本-${TS}",
 "kpiSchemeId":"$SCHEME_ID","targetDim":"EMP","targetCycle":"QUARTER",
 "effectiveDate":"$TODAY"}
JSON
)
call_assert POST /api/perf/target-plans 200 0 "创建副本方案" "$BODY"
EXTRA_PLAN_ID="$(json_get "$LAST_BODY" data.id)"
[ -z "$EXTRA_PLAN_ID" ] && EXTRA_PLAN_ID="$(json_get "$LAST_BODY" data.planId)"
state_set EXTRA_PLAN_ID "$EXTRA_PLAN_ID"

section "C.4 PUT 编辑目标方案"
if [ -n "$EXTRA_PLAN_ID" ]; then
  call_assert PUT "/api/perf/target-plans/$EXTRA_PLAN_ID" 200 0 "改名" \
    '{"planName":"副本-renamed-'"$TS"'"}'
fi

section "C.5 GET /api/perf/target-values 列表（planId 必填）"
if [ -n "$PLAN_ID" ]; then
  call_assert GET "/api/perf/target-values?planId=$PLAN_ID&pageNo=1&pageSize=20"  200 0 "按 planId 默认分页"
  call_assert GET "/api/perf/target-values?planId=$PLAN_ID&subjectType=EMP"       200 0 "按 planId+subjectType 过滤"
else
  skip "无 PLAN_ID"
fi
call_assert GET "/api/perf/target-values?pageNo=1&pageSize=20"            400 VALID_002 "缺 planId → VALID_002"

section "C.6 POST 单值 upsert"
CYCLE_KEY="$(date +%Y)Q$(( ($(date +%-m) - 1) / 3 + 1 ))"
if [ -n "$PLAN_ID" ]; then
  BODY=$(cat <<JSON
{"planId":"$PLAN_ID","subjectType":"EMP","subjectId":"$EMP",
 "cycleKey":"$CYCLE_KEY","metricCode":"$L1","targetValue":2000.00,"baseValue":1500.00}
JSON
)
  call_assert POST /api/perf/target-values 200 0 "覆盖更新已存在 (UPSERT)" "$BODY"
fi

section "C.7 POST batch upsert (≤500)"
if [ -n "$PLAN_ID" ]; then
  BODY=$(cat <<JSON
{"values":[
  {"planId":"$PLAN_ID","subjectType":"EMP","subjectId":"E10001","cycleKey":"$CYCLE_KEY","metricCode":"$L1","targetValue":1100,"baseValue":900},
  {"planId":"$PLAN_ID","subjectType":"EMP","subjectId":"E10002","cycleKey":"$CYCLE_KEY","metricCode":"$L1","targetValue":1200,"baseValue":950}
]}
JSON
)
  call_assert POST /api/perf/target-values/batch 200 0 "批量 upsert 2 条" "$BODY"

  # 空 batch 应失败
  call_assert POST /api/perf/target-values/batch 400 - "空 batch 应失败" '{"values":[]}'
fi

print_summary
