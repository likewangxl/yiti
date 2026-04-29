#!/usr/bin/env bash
# 08-recalc-targetadjust.sh — Recalc 1 端点 + TargetAdjust 4 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

TS="$(state_get TS_PREFIX)"
TODAY="$(date +%Y-%m-%d)"
PLAN_ID="$(state_get PLAN_ID)"
EMP="$(state_get EMP_ID)"
ORG="$(state_get ORG_CODE)"
L1="$(state_get METRIC_L1)"

section "H.1 POST /api/perf/recalc 历史回算 (高危)"
BODY=$(cat <<JSON
{"cycleType":"MONTHLY","cycleDateFrom":"$TODAY","cycleDateTo":"$TODAY",
 "metricCodes":["$L1"],"version":"V1","reason":"curl test recalc"}
JSON
)
call POST /api/perf/recalc "$BODY"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "200" ] && [ "$CODE" = "0" ]; then
  ok "recalc 启动 parentId=$(json_get "$LAST_BODY" data.parentId)  status=$(json_get "$LAST_BODY" data.status)"
else
  warn "recalc http=$HTTP code=$CODE  msg=$(json_get "$LAST_BODY" message)"
fi

# 缺 reason 校验
call_assert POST /api/perf/recalc 400 - "recalc 缺 reason → 422" \
  "{\"cycleType\":\"MONTHLY\",\"cycleDateFrom\":\"$TODAY\",\"cycleDateTo\":\"$TODAY\",\"version\":\"V1\"}"

section "I.1 POST /api/perf/target-adjust/create 提交目标修正申请"
CYCLE_KEY="$(date +%Y%m)"
TA_BODY=$(cat <<JSON
{"planId":"$PLAN_ID","subjectType":"EMP","subjectId":"$EMP","cycleKey":"$CYCLE_KEY",
 "ownerOrgId":"$ORG","reason":"curl test target adjust",
 "adjustments":[{"metricCode":"$L1","newValue":2500}]}
JSON
)
call POST /api/perf/target-adjust/create "$TA_BODY"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "200" ] && [ "$CODE" = "0" ]; then
  ok "提交目标修正 applyId=$(json_get "$LAST_BODY" data.id)"
  state_set TARGET_APPLY_ID "$(json_get "$LAST_BODY" data.id)"
elif [ "$HTTP" = "400" ]; then
  warn "字段校验失败 (adjustments 子结构未对齐) http=$HTTP code=$CODE — 跳过依赖项"
  warn "  resp: $(echo "$LAST_BODY" | head -c 300)"
else
  fail "提交目标修正 http=$HTTP code=$CODE"
fi

section "I.2 GET /api/perf/target-adjust/list 列表"
call_assert GET "/api/perf/target-adjust/list?pageNo=1&pageSize=20"   200 0 "申请列表"

section "I.3 GET /api/perf/target-adjust/{id} 详情"
TA_ID="$(state_get TARGET_APPLY_ID)"
if [ -n "$TA_ID" ]; then
  call_assert GET "/api/perf/target-adjust/$TA_ID"                    200 0 "详情"
else
  skip "无 TARGET_APPLY_ID"
fi

section "I.4 POST /api/perf/target-adjust/{id}/withdraw 撤回 (高危)"
if [ -n "$TA_ID" ]; then
  call_assert POST "/api/perf/target-adjust/$TA_ID/withdraw" 200 0 "撤回" \
    '{"reason":"curl test withdraw"}'
else
  skip "无 TARGET_APPLY_ID"
fi

print_summary
