#!/usr/bin/env bash
# 契约文档新鲜度检查
#
# 用途:守护"接口契约同步红线"——凡改动模块 controller/、api/ 包,必须同一提交内
# 更新 docs/modules/<模块>/03-接口设计*、04-对外API契约*(见根 CLAUDE.md 文档维护约定)。
#
# 判定逻辑(两层):
#   1. 已提交层:模块 controller/api 目录的最后提交时间晚于其 03/04 文档 → STALE
#   2. 工作区层:controller/api 目录有未提交改动而 03/04 文档没有 → WARN(提交前提醒)
# 任一模块 STALE 则退出码 1(可接入 CI/提交前自检)。
#
# 用法: scripts/check-contract-docs.sh          # 检查全部模块
#       scripts/check-contract-docs.sh <模块名>  # 只检查指定模块
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2

# 模块 → 契约文档位置。soap-gateway-center 无 docs/modules 目录,契约收口在其 CLAUDE.md;
# common 无 REST 端点,契约文档为 03-关键组件设计(无 04),仅比对该文件。
MODULES=(
  "auth-permission-center|docs/modules/auth-permission-center"
  "system-governance-center|docs/modules/system-governance-center"
  "workflow-center|docs/modules/workflow-center"
  "customer-marketing-center|docs/modules/customer-marketing-center"
  "business-application-center|docs/modules/business-application-center"
  "portal-content-center|docs/modules/portal-content-center"
  "performance-engine-center|docs/modules/performance-engine-center"
  "report-analytics-center|docs/modules/report-analytics-center"
  "red-engine-center|docs/modules/red-engine-center"
  "common|docs/modules/common"
  "soap-gateway-center|soap-gateway-center"
)

only="${1:-}"
stale=0

for entry in "${MODULES[@]}"; do
  mod="${entry%%|*}"; docdir="${entry##*|}"
  [ -n "$only" ] && [ "$only" != "$mod" ] && continue

  # 契约代码面:controller/ 与 api/ 目录(含 DTO/错误码所在包)
  mapfile -t code_dirs < <(find "$mod/src/main/java" -type d \( -name controller -o -name api \) 2>/dev/null)
  [ ${#code_dirs[@]} -eq 0 ] && code_dirs=("$mod/src/main/java")

  # 契约文档面
  if [ "$mod" = "soap-gateway-center" ]; then
    docs=("$mod/CLAUDE.md")
  elif [ "$mod" = "common" ]; then
    docs=("$docdir"/03-*.md)
  else
    docs=("$docdir"/03-*.md "$docdir"/04-*.md)
  fi
  if [ ! -e "${docs[0]}" ]; then
    echo "STALE  $mod: 契约文档缺失($docdir)"
    stale=1
    continue
  fi

  code_ts=$(git log -1 --format=%ct -- "${code_dirs[@]}" 2>/dev/null || echo 0)
  doc_ts=$(git log -1 --format=%ct -- "${docs[@]}" 2>/dev/null || echo 0)
  code_dirty=$(git status --porcelain -- "${code_dirs[@]}" 2>/dev/null)
  doc_dirty=$(git status --porcelain -- "${docs[@]}" 2>/dev/null)

  if [ -n "$code_dirty" ] && [ -z "$doc_dirty" ]; then
    echo "WARN   $mod: controller/api 有未提交改动但契约文档未动——若改了接口契约,提交前先更新 03/04"
  fi
  if [ "${code_ts:-0}" -gt "${doc_ts:-0}" ]; then
    last_commit=$(git log -1 --format='%h %ad %s' --date=short -- "${code_dirs[@]}")
    echo "STALE  $mod: 契约代码最后提交($(date -d @"$code_ts" +%F))晚于契约文档($(date -d @"$doc_ts" +%F)) ← $last_commit"
    stale=1
  else
    [ -z "$code_dirty" ] && echo "OK     $mod"
  fi
done

exit $stale
