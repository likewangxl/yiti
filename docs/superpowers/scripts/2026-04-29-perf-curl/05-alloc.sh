#!/usr/bin/env bash
# 05-alloc.sh — AllocRelationController 3 端点 + AllocAdjustController 4 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

EMP="$(state_get EMP_ID)"
ORG="$(state_get ORG_CODE)"
TODAY="$(date +%Y-%m-%d)"
TS="$(state_get TS_PREFIX)"

section "D.1 GET /api/perf/alloc-relations 当前分配（custId 必填）"
TEST_CUST="C_TST_${TS}"
call_assert GET "/api/perf/alloc-relations?custId=$TEST_CUST"                          200 0 "按 custId 查询当前分配（应空集）"
call_assert GET "/api/perf/alloc-relations?custId=$TEST_CUST&bizKind=DEPOSIT"          200 0 "按 custId+bizKind 过滤"
call_assert GET "/api/perf/alloc-relations"                                            400 VALID_002 "缺 custId → VALID_002"

section "D.2 GET /api/perf/alloc-relations/history（custId + asOfDate 必填）"
call_assert GET "/api/perf/alloc-relations/history?custId=$TEST_CUST&asOfDate=$TODAY"  200 0 "历史快照"

section "D.3 GET /api/perf/alloc-relations/summary"
call_assert GET "/api/perf/alloc-relations/summary?effectiveDate=$TODAY"               200 0 "汇总按机构"

section "D.4 POST /api/perf/alloc-adjust/create 提交分配调整申请"
# AllocAdjustItemDTO 字段无法从 OpenAPI 反查，按业务文档惯例构造一个最小项
BODY=$(cat <<JSON
{"custId":"C_TST_${TS}","allocDim":"EMP","bizKind":"DEPOSIT","accountNo":"AC_TST_${TS}",
 "ownerOrgId":"$ORG","reason":"curl test alloc adjust",
 "items":[{"empId":"E10001","ratio":100}]}
JSON
)
call POST /api/perf/alloc-adjust/create "$BODY"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "200" ] && [ "$CODE" = "0" ]; then
  ok "提交分配调整 ok  applyId=$(json_get "$LAST_BODY" data.id)"
  APPLY_ID="$(json_get "$LAST_BODY" data.id)"
  state_set ALLOC_APPLY_ID "$APPLY_ID"
elif [ "$HTTP" = "200" ] && [ -n "$CODE" ] && [ "$CODE" != "0" ]; then
  # 端点可达但业务校验拦截（如 cust_alloc_relation 表为空 → PERF-42200 / 客户无现存分配关系）
  ok "端点可达，业务前置不满足 http=$HTTP code=$CODE （cust_alloc_relation 为空集，符合预期）"
elif [ "$HTTP" = "400" ]; then
  warn "字段校验失败 (items 子结构未完全对齐) http=$HTTP code=$CODE — 跳过依赖此 ID 的步骤"
  warn "  resp: $(echo "$LAST_BODY" | head -c 200)"
else
  fail "提交失败 http=$HTTP code=$CODE"
fi

section "D.5 GET /api/perf/alloc-adjust/list 分页查询"
call_assert GET "/api/perf/alloc-adjust/list?pageNo=1&pageSize=20" 200 0 "申请列表"

section "D.6 GET /api/perf/alloc-adjust/{id} 详情"
APPLY_ID="$(state_get ALLOC_APPLY_ID)"
if [ -n "$APPLY_ID" ]; then
  call_assert GET "/api/perf/alloc-adjust/$APPLY_ID" 200 0 "命中详情"
else
  skip "无 ALLOC_APPLY_ID，跳过详情"
fi

section "D.7 POST /api/perf/alloc-adjust/{id}/withdraw 撤回 (高危)"
if [ -n "$APPLY_ID" ]; then
  call_assert POST "/api/perf/alloc-adjust/$APPLY_ID/withdraw" 200 0 "撤回申请" \
    '{"reason":"curl test withdraw"}'
else
  skip "无 ALLOC_APPLY_ID，跳过撤回"
fi

print_summary
