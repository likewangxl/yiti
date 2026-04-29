# performance-engine-center curl 测试通用库
# 必须以 `source lib.sh` 方式引入；不要直接执行
#
# 约定：
#   * BASE              — 服务地址，默认 http://127.0.0.1:8080
#   * COOKIE_FILE       — 登录态 cookie jar
#   * STATE_FILE        — 跨脚本传递种子 ID 的 KV 文件（key=value 一行一条）
#   * RESULT_DIR        — 输出目录，每次调用 1 个 .resp.json 文件
#   * TS_PREFIX         — 本次执行的全局时间戳前缀，用于唯一化测试编码
#   * PASS / FAIL 计数  — 由 call_assert / json_assert 维护
#   * --noproxy '*'     — 绕开 shell HTTP_PROXY 直连本机

set -uo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"
COOKIE_FILE="${COOKIE_FILE:-/tmp/yiti-perf.cookie}"
STATE_FILE="${STATE_FILE:-/tmp/yiti-perf.state}"
RESULT_DIR="${RESULT_DIR:-/tmp/yiti-perf-results}"
TS_PREFIX="${TS_PREFIX:-$(date +%Y%m%d_%H%M%S)}"

mkdir -p "$RESULT_DIR"
touch "$STATE_FILE"

# ---------- 颜色输出 ----------
if [ -t 1 ]; then
  CG=$'\e[32m'; CR=$'\e[31m'; CY=$'\e[33m'; CB=$'\e[36m'; CN=$'\e[0m'
else
  CG=''; CR=''; CY=''; CB=''; CN=''
fi

PASS=0; FAIL=0; SKIPPED=0

ok()   { printf '%s[ OK ]%s %s\n'   "$CG" "$CN" "$*"; PASS=$((PASS+1)); }
fail() { printf '%s[FAIL]%s %s\n'   "$CR" "$CN" "$*"; FAIL=$((FAIL+1)); }
warn() { printf '%s[WARN]%s %s\n'   "$CY" "$CN" "$*"; }
info() { printf '%s[ -- ]%s %s\n'   "$CB" "$CN" "$*"; }
skip() { printf '%s[SKIP]%s %s\n'   "$CY" "$CN" "$*"; SKIPPED=$((SKIPPED+1)); }
section() { printf '\n%s== %s ==%s\n' "$CB" "$*" "$CN"; }

# ---------- state 文件 KV ----------
state_set() {
  local k="$1" v="$2"
  # 删除旧值再追加
  grep -v "^${k}=" "$STATE_FILE" > "$STATE_FILE.tmp" 2>/dev/null || true
  printf '%s=%s\n' "$k" "$v" >> "$STATE_FILE.tmp"
  mv "$STATE_FILE.tmp" "$STATE_FILE"
}

state_get() {
  local k="$1"
  grep "^${k}=" "$STATE_FILE" | tail -1 | cut -d= -f2-
}

# ---------- JSON 工具（无 jq，全用 python） ----------
# json_get '<json string>' '<dot path>'  例如: json_get "$body" "data.taskId"
json_get() {
  python3 -c '
import json, sys
path = sys.argv[1]
try:
    obj = json.load(sys.stdin)
except Exception:
    print(""); sys.exit(0)
cur = obj
for seg in path.split("."):
    if seg == "":
        continue
    if isinstance(cur, list):
        try:
            cur = cur[int(seg)]
        except Exception:
            print(""); sys.exit(0)
    elif isinstance(cur, dict):
        cur = cur.get(seg)
        if cur is None:
            print(""); sys.exit(0)
    else:
        print(""); sys.exit(0)
if isinstance(cur, (dict, list)):
    print(json.dumps(cur, ensure_ascii=False))
else:
    print(cur if cur is not None else "")
' "$2" <<<"$1"
}

# 漂亮打印 JSON（截断超长输出）
pp() {
  python3 -c '
import json,sys
try:
    s = sys.stdin.read()
    obj = json.loads(s)
    out = json.dumps(obj, ensure_ascii=False, indent=2)
    if len(out) > 1500:
        out = out[:1500] + "\n... [truncated]"
    print(out)
except Exception:
    sys.stdout.write(sys.stdin.read()[:1500])
'
}

# ---------- HTTP 调用 ----------
# call <method> <path> [body_json] [extra_curl_opts...]
# 输出：将响应体写入 $LAST_BODY、HTTP code 写入 $LAST_CODE，并保存到 $RESULT_DIR/<seq>.resp.json
SEQ=0
call() {
  SEQ=$((SEQ+1))
  local method="$1" path="$2" body="${3:-}"
  shift 3 2>/dev/null || shift $#
  local opts=("$@")
  local out_file
  out_file="$(printf '%s/%03d_%s_%s.resp.json' "$RESULT_DIR" "$SEQ" "$method" "$(echo "$path" | tr '/?&=' '____')")"

  local args=(--noproxy '*' -sS -b "$COOKIE_FILE" -c "$COOKIE_FILE"
              -o "$out_file" -w '%{http_code}'
              -X "$method")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  args+=("${opts[@]}")
  args+=("$BASE$path")

  LAST_CODE="$(curl "${args[@]}" 2>/dev/null || echo '000')"
  LAST_BODY="$(cat "$out_file" 2>/dev/null || true)"
  LAST_FILE="$out_file"
  return 0
}

# call_assert <method> <path> <expected_http_code> <expected_biz_code_or_dash> <description> [body_json] [extra_opts...]
# expected_biz_code 写 "-" 表示不检查 code 字段（仅 HTTP 码）
call_assert() {
  local method="$1" path="$2" exp_http="$3" exp_code="$4" desc="$5"
  shift 5
  local body="${1:-}"; shift 2>/dev/null || true
  call "$method" "$path" "$body" "$@"
  local biz_code
  biz_code="$(json_get "$LAST_BODY" code)"
  if [ "$LAST_CODE" = "$exp_http" ] && { [ "$exp_code" = "-" ] || [ "$biz_code" = "$exp_code" ]; }; then
    ok "$desc  [$method $path  http=$LAST_CODE code=$biz_code]"
    return 0
  else
    fail "$desc  [$method $path  http=$LAST_CODE code=$biz_code  expected http=$exp_http code=$exp_code]"
    printf '       resp: '; printf '%s' "$LAST_BODY" | head -c 400; echo
    return 1
  fi
}

# ---------- 登录前置 ----------
require_login() {
  if ! grep -q SESSION "$COOKIE_FILE" 2>/dev/null; then
    info "未发现 SESSION cookie，自动登录 admin/123456"
    do_login admin 123456
  fi
}

do_login() {
  local user="${1:-admin}" pwd="${2:-123456}"
  rm -f "$COOKIE_FILE"
  local resp
  resp="$(curl --noproxy '*' -sS -c "$COOKIE_FILE" \
        -H 'Content-Type: application/json' \
        -X POST "$BASE/api/auth/login" \
        -d "{\"username\":\"$user\",\"password\":\"$pwd\"}")"
  local code; code="$(json_get "$resp" code)"
  if [ "$code" = "0" ]; then
    ok "登录成功 username=$user empId=$(json_get "$resp" data.empId)"
    return 0
  else
    fail "登录失败 username=$user code=$code resp=$resp"
    return 1
  fi
}

# ---------- 收尾打印 ----------
print_summary() {
  printf '\n'
  printf '====================================================\n'
  printf '  PASS=%s%d%s   FAIL=%s%d%s   SKIP=%s%d%s\n' \
    "$CG" "$PASS" "$CN" "$CR" "$FAIL" "$CN" "$CY" "$SKIPPED" "$CN"
  printf '  responses dir: %s\n' "$RESULT_DIR"
  printf '====================================================\n'
  return $FAIL
}
