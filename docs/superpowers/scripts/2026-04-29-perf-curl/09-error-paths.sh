#!/usr/bin/env bash
# 09-error-paths.sh — 鉴权 / 校验失败矩阵
#   不需要预先登录；脚本内部会显式新建/失效 cookie 来制造 401
DIR="$(cd "$(dirname "$0")" && pwd)"
source "$DIR/lib.sh"

EMPTY_COOKIE=/tmp/yiti-perf-empty.cookie
rm -f "$EMPTY_COOKIE" && touch "$EMPTY_COOKIE"

section "J.1 未登录访问 (401 / AUTH-40101)"
ORIG_COOKIE="$COOKIE_FILE"
COOKIE_FILE="$EMPTY_COOKIE"
call GET "/api/perf/metrics?pageNo=1&pageSize=10"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "401" ] || [ "$HTTP" = "403" ]; then
  ok "未登录被拦 http=$HTTP code=$CODE"
else
  fail "未登录未被拦 http=$HTTP code=$CODE"
fi
COOKIE_FILE="$ORIG_COOKIE"

section "J.2 已登录但访问未注册 URL (403 / AUTH-40302)"
require_login
call GET "/api/perf/this-url-does-not-exist"
HTTP="$LAST_CODE"; CODE="$(json_get "$LAST_BODY" code)"
if [ "$HTTP" = "403" ] || [ "$HTTP" = "404" ]; then
  ok "未注册资源被拦 http=$HTTP code=$CODE"
else
  fail "未注册资源未被拦 http=$HTTP code=$CODE"
fi

section "J.3 业务校验：必填字段缺失"
call_assert POST /api/perf/metrics 400 - "创建指标缺 metricCode" \
  '{"metricName":"x","baseDim":"EMP","metricLevel":1,"calcFreq":"D","calcMode":"AUTO","calcLogicType":"SQL","sqlText":"SELECT 1"}'

call_assert POST /api/perf/metrics 400 - "metricCode 含非法字符" \
  '{"metricCode":"M_BAD@!","metricName":"x","baseDim":"EMP","metricLevel":1,"calcFreq":"D","calcMode":"AUTO","calcLogicType":"SQL","sqlText":"SELECT 1"}'

call_assert POST /api/perf/metrics 400 - "baseDim 不在枚举内" \
  '{"metricCode":"M_TST_BAD","metricName":"x","baseDim":"XXX","metricLevel":1,"calcFreq":"D","calcMode":"AUTO","calcLogicType":"SQL","sqlText":"SELECT 1"}'

section "J.4 业务校验：reason 必填的高危端点"
call_assert POST /api/perf/recalc 400 - "recalc 完全空 body" "{}"
call_assert POST /api/perf/sys-control/init 400 - "init 缺 reason" "{}"

section "J.5 路径变量不存在（业务异常返回 HTTP 200 + biz code）"
call_assert GET "/api/perf/metrics/M_NOT_EXIST_FOO"   200 PERF-40001 "metric 不存在 → PERF-40001"
call_assert GET "/api/perf/kpi-schemes/NOT_EXIST_FOO" 200 PERF-40003 "kpi 不存在 → PERF-40003"

print_summary
