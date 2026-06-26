#!/usr/bin/env bash
# =====================================================================
# yiti 生产上线脚本生成器（从权威源「实际 yiti 库」导出）
#   产物 1：ddl-yiti-prod-golive.sql      —— 建库建表（全表，排除 *_BAK_* 备份表）
#   产物 2：seed-yiti-prod-golive.sql     —— 数据初始化（基础参数/RBAC组织/审批流程/指标数据）
# 用法：bash docs/schema/gen-yiti-prod-golive.sh
# 说明：业务数据表（客户/线索/贷款/触达/审批运行态等）不导出数据，上线后为空。
# =====================================================================
set -euo pipefail
DB=yiti
DUMP="mysqldump -uroot -pdjdev --single-transaction --no-tablespaces --skip-comments --set-gtid-purged=OFF"
OUT_DDL="docs/schema/ddl-yiti-prod-golive.sql"
OUT_SEED="docs/schema/seed-yiti-prod-golive.sql"

# 排除的备份/临时表
IGNORE=""
for t in PERF_METRIC_DEF_BAK_20260519 PERF_METRIC_DEF_BAK_20260531 \
         PT_ROLE_BAK_20260610 PT_ROLE_BIZ_SCOPE_BAK_20260610 \
         PT_ROLE_RESOURCE_BAK_20260610 PT_USER_ROLE_BAK_20260610; do
  IGNORE="$IGNORE --ignore-table=$DB.$t"
done

# ---------- 产物 1：建库 DDL（全表结构，无数据，无 DROP，FK 关闭便于任意顺序建表） ----------
{
  echo "-- =====================================================================";
  echo "-- yiti 生产上线 · 建库建表脚本（自实际 yiti 库导出，权威源）";
  echo "-- 含全部业务/平台/Flowable(ACT_*)/Quartz(QRTZ_*) 表，排除 *_BAK_* 备份表。";
  echo "-- 首次搭库执行；空库按本脚本一次性建表。";
  echo "-- =====================================================================";
  echo "SET NAMES utf8mb4;";
  echo "SET FOREIGN_KEY_CHECKS=0;";
  $DUMP --no-data --skip-add-drop-table $IGNORE $DB;
  echo "SET FOREIGN_KEY_CHECKS=1;";
} > "$OUT_DDL"

# ---------- 产物 2：数据初始化（按域分组，仅配置/审批/指标，不含业务运行数据） ----------
SEED_OPTS="--no-create-info --complete-insert --hex-blob --skip-extended-insert"
emit_group () {  # $1=标题  $2...=表名
  local title="$1"; shift
  echo "" >> "$OUT_SEED"
  echo "-- ---------------------------------------------------------------------" >> "$OUT_SEED"
  echo "-- $title" >> "$OUT_SEED"
  echo "-- ---------------------------------------------------------------------" >> "$OUT_SEED"
  $DUMP $SEED_OPTS $DB "$@" >> "$OUT_SEED"
}

{
  echo "-- =====================================================================";
  echo "-- yiti 生产上线 · 数据初始化脚本（自实际 yiti 库导出，权威源）";
  echo "-- 分组：① 基础参数  ② RBAC/组织  ③ 审批流程  ④ 指标数据";
  echo "-- 仅含配置/审批/指标初始化数据，不含客户/线索/贷款/触达等业务运行数据。";
  echo "-- 须在已建表（ddl-yiti-prod-golive.sql）之后执行。";
  echo "-- =====================================================================";
  echo "SET NAMES utf8mb4;";
  echo "SET FOREIGN_KEY_CHECKS=0;";
} > "$OUT_SEED"

# ① 基础参数：字典 / KV 配置 / 工作日历
emit_group "① 基础参数（字典/配置/工作日历）" SYS_DICT SYS_CONFIG_KV SYS_CALENDAR_DAY
# ①bis 定时任务：sys_job_conf 配置（Quartz QRTZ_* 运行态由启动期 syncJobsOnStartup 据此重建，无需导出）
emit_group "①bis 定时任务配置（SYS_JOB_CONF；QRTZ_* 启动期自动重建）" SYS_JOB_CONF
# ② RBAC / 组织：机构 → 用户 → 角色 → 绑定（按依赖顺序）
emit_group "② RBAC 与组织（机构/用户/角色/资源/数据范围绑定）" \
  EXT_ORG_INFO EXT_USER_ORG PT_USER PT_ROLE PT_USER_ROLE PT_RESOURCE PT_ROLE_RESOURCE PT_ROLE_BIZ_SCOPE
# ③ 审批流程：超时/候选人/表单配置 + Flowable 已部署流程定义（ACT_RE_*/ACT_GE_BYTEARRAY）
emit_group "③ 审批流程（节点配置 + Flowable 已部署 BPMN 流程定义）" \
  WF_TIMEOUT_RULE WF_NODE_CANDIDATE_CONF WF_NODE_FORM_CONF \
  ACT_GE_BYTEARRAY ACT_RE_DEPLOYMENT ACT_RE_PROCDEF
# ④ 指标数据：版本控制 + 指标定义/引用 + KPI 方案/项 + 目标方案
emit_group "④ 指标数据（版本/指标定义/引用/KPI方案/目标方案）" \
  SYS_CONTROL PERF_METRIC_DEF PERF_METRIC_REF PERF_KPI_SCHEME PERF_KPI_ITEM PERF_TARGET_PLAN

echo "" >> "$OUT_SEED"
echo "SET FOREIGN_KEY_CHECKS=1;" >> "$OUT_SEED"

echo "生成完成："
wc -l "$OUT_DDL" "$OUT_SEED"
