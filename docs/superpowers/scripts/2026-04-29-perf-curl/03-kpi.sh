#!/usr/bin/env bash
# 03-kpi.sh — KpiSchemeController 9 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

L1="$(state_get METRIC_L1)";  [ -z "$L1" ] && { fail "缺少 METRIC_L1，先跑 01-seed"; exit 1; }
SCHEME_ID="$(state_get SCHEME_ID)"
SCHEME_CODE="$(state_get SCHEME_CODE)"
TS="$(state_get TS_PREFIX)"

section "B.1 GET /api/perf/kpi-schemes 列表"
call_assert GET "/api/perf/kpi-schemes?pageNo=1&pageSize=20"        200 0 "默认分页"
call_assert GET "/api/perf/kpi-schemes?cycleType=MONTHLY"           200 0 "按 cycleType 过滤"

section "B.2 GET /api/perf/kpi-schemes/{id} 详情"
if [ -n "$SCHEME_ID" ]; then
  call_assert GET "/api/perf/kpi-schemes/$SCHEME_ID"                200 0 "命中详情(应含 items[])"
fi
call_assert GET "/api/perf/kpi-schemes/NON_EXIST_${TS}"             200 PERF-40003 "不存在 → PERF-40003 (HTTP 200 业务错误)"

section "B.3 POST 新增 KPI 方案 (副本，用于 update/delete 路径测试)"
EXTRA_CODE="KPI_TST_${TS}_2"
BODY=$(cat <<JSON
{"schemeCode":"$EXTRA_CODE","schemeName":"测试KPI方案副本-${TS}",
 "cycleType":"QUARTERLY","openDetail":false}
JSON
)
call_assert POST /api/perf/kpi-schemes 200 0 "创建副本方案" "$BODY"
EXTRA_ID="$(json_get "$LAST_BODY" data.id)"
[ -z "$EXTRA_ID" ] && EXTRA_ID="$(json_get "$LAST_BODY" data.schemeId)"
state_set KPI_EXTRA_ID "$EXTRA_ID"
info "EXTRA_ID=$EXTRA_ID"

# 重复 schemeCode：HTTP 200 + PERF-40005
call_assert POST /api/perf/kpi-schemes 200 PERF-40005 "schemeCode 重复 → PERF-40005" "$BODY"

section "B.4 PUT 编辑 KPI 方案"
if [ -n "$EXTRA_ID" ]; then
  call_assert PUT "/api/perf/kpi-schemes/$EXTRA_ID" 200 0 "改名" \
    '{"schemeName":"副本-rename-'"$TS"'","cycleType":"QUARTERLY","openDetail":true}'
fi

section "B.5 POST items 添加指标项"
if [ -n "$EXTRA_ID" ]; then
  call_assert POST "/api/perf/kpi-schemes/$EXTRA_ID/items" 200 0 "向副本方案加 item" \
    "{\"metricCode\":\"$L1\",\"weight\":50,\"multiplier\":1,\"minScore\":0,\"maxScore\":100}"
  ITEM_ID="$(json_get "$LAST_BODY" data.id)"
  [ -z "$ITEM_ID" ] && ITEM_ID="$(json_get "$LAST_BODY" data.itemId)"
  state_set KPI_EXTRA_ITEM_ID "$ITEM_ID"

  # 重复 (schemeId, metricCode)：HTTP 200 + PERF-40901
  call_assert POST "/api/perf/kpi-schemes/$EXTRA_ID/items" 200 PERF-40901 "同 metricCode 重复 → PERF-40901" \
    "{\"metricCode\":\"$L1\",\"weight\":30}"
fi

section "B.6 PUT items 编辑指标项"
if [ -n "${ITEM_ID:-}" ]; then
  call_assert PUT "/api/perf/kpi-schemes/$EXTRA_ID/items/$ITEM_ID" 200 0 "调整权重" \
    '{"weight":80,"multiplier":1.2}'
fi

section "B.7 DELETE items 删除指标项 (高危)"
if [ -n "${ITEM_ID:-}" ]; then
  call_assert DELETE "/api/perf/kpi-schemes/$EXTRA_ID/items/$ITEM_ID" 200 0 "删 item" \
    '{"reason":"curl test delete item"}'
fi

section "B.8 POST publish 发布 KPI 方案 (高危)"
if [ -n "$EXTRA_ID" ]; then
  call_assert POST "/api/perf/kpi-schemes/$EXTRA_ID/publish" 200 0 "发布副本方案" \
    '{"reason":"curl test publish copy"}'
fi

section "B.9 DELETE 删除 KPI 方案 (高危) — 留到 cleanup 阶段"
info "EXTRA_ID=$EXTRA_ID 在 99-cleanup.sh 删除，避免影响后续 target 测试"

print_summary
