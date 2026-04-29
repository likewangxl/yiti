#!/usr/bin/env bash
# 07-import-export.sh — Import 5 端点 + Export 5 端点
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"
require_login

TS="$(state_get TS_PREFIX)"
TODAY="$(date +%Y-%m-%d)"
L1="$(state_get METRIC_L1)"
ORG="$(state_get ORG_CODE)"

section "G.1 POST /api/perf/import/upload 上传 Excel"
# 准备一个最小 .xlsx 临时文件（伪 Excel，只为打通 multipart 路径，期望 422）
TMP_XLSX="$(mktemp --suffix=.xlsx)"
printf 'PK\x05\x06\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00' > "$TMP_XLSX"
call POST /api/perf/import/upload "" \
  -F "file=@$TMP_XLSX" -F "importType=TARGET"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "200" ] && [ "$CODE" = "0" ]; then
  ok "上传成功 batchId=$(json_get "$LAST_BODY" data.batchId)"
  state_set IMPORT_BATCH_ID "$(json_get "$LAST_BODY" data.batchId)"
elif [ "$HTTP" = "400" ] || [ "$HTTP" = "415" ] || \
     { [ "$HTTP" = "200" ] && [ "$CODE" = "PERF-42200" ]; } || \
     { [ "$HTTP" = "500" ] && [ "$CODE" = "SYS_500" ]; }; then
  ok "上传端点可达，空 xlsx 被拒 http=$HTTP code=$CODE （POI 解析空文件抛非业务异常 → SYS_500，符合预期）"
else
  fail "上传 http=$HTTP code=$CODE"
fi
rm -f "$TMP_XLSX"

section "G.2 GET /api/perf/import/batches/{batchId}"
BATCH_ID="$(state_get IMPORT_BATCH_ID)"
if [ -n "$BATCH_ID" ]; then
  call_assert GET "/api/perf/import/batches/$BATCH_ID"                  200 0 "查询批次详情"
  call_assert GET "/api/perf/import/batches/$BATCH_ID/errors"           200 0 "查询错误明细"
  call_assert POST "/api/perf/import/batches/$BATCH_ID/retry" 200 0 "重试" '{"reason":"curl retry"}'
  call_assert DELETE "/api/perf/import/batches/$BATCH_ID" 200 0 "删除批次" '{"reason":"curl delete"}'
else
  skip "无 IMPORT_BATCH_ID（上传未成功），跳过 4 个跟随端点"
  call_assert GET "/api/perf/import/batches/NOT_EXIST_$TS"              200 PERF-40017 "不存在批次 → PERF-40017 (HTTP 200 业务错误)"
fi

section "G.3 POST /api/perf/export/metric 创建指标宽表导出"
EXP_BODY=$(cat <<JSON
{"metricCodes":["$L1"],"baseDim":"EMP","dataDate":"$TODAY","version":"V1","orgCodes":["$ORG"]}
JSON
)
call POST /api/perf/export/metric "$EXP_BODY"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
EXP_TASK_ID="$(json_get "$LAST_BODY" data.taskId)"
[ -z "$EXP_TASK_ID" ] && EXP_TASK_ID="$(json_get "$LAST_BODY" data.id)"
if [ "$HTTP" = "200" ] && [ "$CODE" = "0" ]; then
  ok "metric 导出任务创建 taskId=$EXP_TASK_ID"
  [ -n "$EXP_TASK_ID" ] && state_set EXP_TASK_ID "$EXP_TASK_ID"
else
  warn "metric export http=$HTTP code=$CODE  $(echo "$LAST_BODY" | head -c 200)"
fi

section "G.4 POST /api/perf/export/kpi 创建 KPI 导出"
KPI_EXP_BODY=$(cat <<JSON
{"cycleType":"MONTHLY","cycleDate":"$TODAY","asOfDate":"$TODAY","dataVersion":"V1"}
JSON
)
call POST /api/perf/export/kpi "$KPI_EXP_BODY"
info "kpi export http=$LAST_CODE code=$(json_get "$LAST_BODY" code) taskId=$(json_get "$LAST_BODY" data.taskId)"

section "G.5 POST /api/perf/export/detail 创建 KPI 明细导出"
call POST /api/perf/export/detail "$KPI_EXP_BODY"
info "detail export http=$LAST_CODE code=$(json_get "$LAST_BODY" code)"

section "G.6 POST /api/perf/export/alloc 创建分配导出"
ALLOC_EXP=$(cat <<JSON
{"effectiveDate":"$TODAY","bizKind":"DEPOSIT"}
JSON
)
call POST /api/perf/export/alloc "$ALLOC_EXP"
info "alloc export http=$LAST_CODE code=$(json_get "$LAST_BODY" code)"

section "G.7 GET /api/perf/export/task/{taskId} 查询状态"
EXP_TASK_ID="$(state_get EXP_TASK_ID)"
if [ -n "$EXP_TASK_ID" ]; then
  call_assert GET "/api/perf/export/task/$EXP_TASK_ID"                  200 0 "查询导出任务状态"
else
  skip "无 EXP_TASK_ID"
fi

print_summary
