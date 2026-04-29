#!/usr/bin/env bash
# 01-seed.sh — 测试种子准备
#   * 1 个 SQL 一级指标   (M_TST_<TS>_L1, baseDim=EMP)
#   * 1 个 SUMMARY 二级指标 (M_TST_<TS>_L2, baseDim=EMP, ref L1)
#   * 1 个 KPI 方案 + 2 个 item，发布
#   * sys-control init for EMP
#
# 唯一性靠 TS_PREFIX；多次重跑会使用不同前缀，互不冲突。
# 种子 ID 写入 STATE_FILE，供后续脚本读取。

DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

TS="$(state_get TS_PREFIX)"
[ -z "$TS" ] && TS="$TS_PREFIX"

L1="M_TST_${TS}_L1"
L2="M_TST_${TS}_L2"
SCHEME_CODE="KPI_TST_${TS}"
PLAN_CODE="TP_TST_${TS}"

section "1.1 创建 SQL 一级指标 $L1"
BODY=$(cat <<JSON
{"metricCode":"$L1","metricName":"测试指标L1-${TS}","metricNameEn":"Test L1 ${TS}",
 "metricDesc":"curl test seed L1","baseDim":"EMP","metricLevel":1,
 "calcFreq":"DAY","calcMode":"AUTO","calcLogicType":"SQL",
 "sqlText":"SELECT USER_ID AS subject_id, 100.0 AS metric_value FROM PT_USER LIMIT 10"}
JSON
)
call_assert POST /api/perf/metrics 200 0 "创建 L1 指标" "$BODY"

section "1.2 创建 SUMMARY 二级指标 $L2 (引用 $L1)"
BODY=$(cat <<JSON
{"metricCode":"$L2","metricName":"测试指标L2-${TS}","baseDim":"EMP","metricLevel":2,
 "calcFreq":"DAY","calcMode":"AUTO","calcLogicType":"SUMMARY",
 "summaryRule":"SUM","refMetricCodes":"[\"$L1\"]"}
JSON
)
call_assert POST /api/perf/metrics 200 0 "创建 L2 指标" "$BODY"

section "1.3 创建 KPI 方案 $SCHEME_CODE"
BODY=$(cat <<JSON
{"schemeCode":"$SCHEME_CODE","schemeName":"测试KPI方案-${TS}",
 "cycleType":"MONTHLY","openDetail":true,
 "items":[{"metricCode":"$L1","weight":40},{"metricCode":"$L2","weight":60}]}
JSON
)
call_assert POST /api/perf/kpi-schemes 200 0 "创建 KPI 方案" "$BODY"
SCHEME_ID="$(json_get "$LAST_BODY" data.id)"
[ -z "$SCHEME_ID" ] && SCHEME_ID="$(json_get "$LAST_BODY" data.schemeId)"
[ -n "$SCHEME_ID" ] && state_set SCHEME_ID "$SCHEME_ID"
info "SCHEME_ID=$SCHEME_ID"

section "1.4 发布 KPI 方案"
if [ -n "$SCHEME_ID" ]; then
  call_assert POST "/api/perf/kpi-schemes/$SCHEME_ID/publish" 200 0 "发布方案" '{"reason":"curl test seed"}'
else
  skip "SCHEME_ID 为空，跳过 publish"
fi

section "1.5 创建目标方案 $PLAN_CODE"
TODAY="$(date +%Y-%m-%d)"
BODY=$(cat <<JSON
{"planCode":"$PLAN_CODE","planName":"测试目标方案-${TS}",
 "kpiSchemeId":"$SCHEME_ID","targetDim":"EMP","targetCycle":"QUARTER",
 "effectiveDate":"$TODAY"}
JSON
)
call_assert POST /api/perf/target-plans 200 0 "创建目标方案" "$BODY"
PLAN_ID="$(json_get "$LAST_BODY" data.id)"
[ -z "$PLAN_ID" ] && PLAN_ID="$(json_get "$LAST_BODY" data.planId)"
[ -n "$PLAN_ID" ] && state_set PLAN_ID "$PLAN_ID"
info "PLAN_ID=$PLAN_ID"

section "1.6 上传单个目标值"
EMP="$(state_get EMP_ID)"
if [ -n "$PLAN_ID" ]; then
  CYCLE_KEY="$(date +%Y)Q$(( ($(date +%-m) - 1) / 3 + 1 ))"
  BODY=$(cat <<JSON
{"planId":"$PLAN_ID","subjectType":"EMP","subjectId":"$EMP",
 "cycleKey":"$CYCLE_KEY","metricCode":"$L1","targetValue":1000.50,"baseValue":800.00}
JSON
)
  call_assert POST /api/perf/target-values 200 0 "upsert 目标值" "$BODY"
fi

section "1.7 SysControl 初始化 EMP scope"
call POST /api/perf/sys-control/init '{"reason":"curl test seed init"}'
info "init -> http=$LAST_CODE  code=$(json_get "$LAST_BODY" code)  msg=$(json_get "$LAST_BODY" message)"
# init 在已有版本时通常返回业务错误，不致命，所以不强 assert

# 把 metric/kpi/plan 的 code 写到 state，后续脚本复用
state_set METRIC_L1 "$L1"
state_set METRIC_L2 "$L2"
state_set SCHEME_CODE "$SCHEME_CODE"
state_set PLAN_CODE "$PLAN_CODE"

print_summary
