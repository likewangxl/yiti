#!/usr/bin/env bash
# run-all.sh — 顺序执行 00 ~ 09 + 99
#
# 用法：
#   ./run-all.sh                 # 全部执行
#   ./run-all.sh --skip-cleanup  # 跑完保留种子，便于人工二次验证
#   ./run-all.sh 02 03           # 只跑 02-metric + 03-kpi（默认 require_login，会自动登录）
DIR="$(cd "$(dirname "$0")" && pwd)"

SKIP_CLEANUP=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --skip-cleanup) SKIP_CLEANUP=1 ;;
    *) ARGS+=("$a") ;;
  esac
done

# 默认全部
DEFAULT=(00 01 02 03 04 05 06 07 08 09)
if [ "${#ARGS[@]}" -gt 0 ]; then
  STEPS=("${ARGS[@]}")
else
  STEPS=("${DEFAULT[@]}")
fi

declare -A NAMES=(
  [00]=00-login.sh
  [01]=01-seed.sh
  [02]=02-metric.sh
  [03]=03-kpi.sh
  [04]=04-target.sh
  [05]=05-alloc.sh
  [06]=06-runtask-syscontrol.sh
  [07]=07-import-export.sh
  [08]=08-recalc-targetadjust.sh
  [09]=09-error-paths.sh
)

TOTAL_PASS=0; TOTAL_FAIL=0; TOTAL_SKIP=0
SUMMARY=()

for s in "${STEPS[@]}"; do
  script="${NAMES[$s]:-}"
  [ -z "$script" ] && { echo "[SKIP] step $s 未定义"; continue; }
  printf '\n#################### %s ####################\n' "$script"
  bash "$DIR/$script"
  rc=$?
  if [ $rc -eq 0 ]; then
    SUMMARY+=("[ OK ]  $script")
  else
    SUMMARY+=("[FAIL] $script (rc=$rc)")
  fi
done

if [ "$SKIP_CLEANUP" -eq 0 ] && [ "${#ARGS[@]}" -eq 0 ]; then
  printf '\n#################### 99-cleanup.sh ####################\n'
  bash "$DIR/99-cleanup.sh"
  SUMMARY+=("[ -- ] 99-cleanup.sh executed")
fi

printf '\n=================== run-all summary ===================\n'
for line in "${SUMMARY[@]}"; do echo "$line"; done
echo "responses: /tmp/yiti-perf-results"
echo "state:     /tmp/yiti-perf.state"
echo "cookie:    /tmp/yiti-perf.cookie"
