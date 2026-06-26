#!/usr/bin/env bash
# =====================================================================
# yiti 生产上线脚本生成器（从权威源「实际 yiti 库」导出）
#   产物 1：ddl-yiti-prod-golive.sql      —— 建库建表（全表，排除 *_BAK_* 备份表）
#   产物 2：seed-yiti-prod-golive.sql     —— 数据初始化（基础参数/RBAC组织/审批流程/指标数据）
# 用法：bash docs/schema/gen-yiti-prod-golive.sh
# 说明：业务数据表（客户/线索/贷款/触达/审批运行态等）不导出数据，上线后为空。
#
# 连接：必须走 TCP（-h127.0.0.1），因为 root@localhost 是 auth_socket 插件，
#       默认 socket 连接会 access denied；root@127.0.0.1 才认密码。
# =====================================================================
set -euo pipefail
DB=yiti
HOST_OPTS="-h127.0.0.1 -P3306"
DUMP="mysqldump -uroot -pdjdev $HOST_OPTS --single-transaction --no-tablespaces --skip-comments --set-gtid-purged=OFF"
MYSQLC="mysql -uroot -pdjdev $HOST_OPTS -N"
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
emit_table_where () {  # $1=标题  $2=表名  $3=WHERE条件
  echo "" >> "$OUT_SEED"
  echo "-- ---------------------------------------------------------------------" >> "$OUT_SEED"
  echo "-- $1" >> "$OUT_SEED"
  echo "-- ---------------------------------------------------------------------" >> "$OUT_SEED"
  $DUMP $SEED_OPTS --where="$3" $DB "$2" >> "$OUT_SEED"
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

# ③ 审批流程：节点配置（设计器节点表）
emit_group "③ 审批流程节点配置（超时/候选人/表单）" \
  WF_TIMEOUT_RULE WF_NODE_CANDIDATE_CONF WF_NODE_FORM_CONF

# ③bis 设计器已部署流程定义（Flowable ACT_*）—— 只导业绩调整对公/零售设计器流程的「生效版本」
#   原因：业绩调整(分配调整)审批主代码走设计器路由 resolveDesignerProcDefKey →
#         DSN_alloc_corp_designer / DSN_alloc_retail_designer，这两个流程 classpath 没有文件、
#         只存在于 ACT_* 表，不导则上线后 fail-fast 发不起审批。
#   刻意排除：静态流程(lead/loan/perf_alloc_*_v1，由 classpath 启动自动部署) + DSN_IT_PUB_*(IT测试流程)。
DESIGNER_KEYS="'DSN_alloc_corp_designer','DSN_alloc_retail_designer'"
# 取每个设计器流程「最新版本」的 DEPLOYMENT_ID_（Flowable 默认按 latest version 发起）
RAW_DEP_IDS=$($MYSQLC -e "
  SELECT p.DEPLOYMENT_ID_ FROM ${DB}.ACT_RE_PROCDEF p
  WHERE p.KEY_ IN ($DESIGNER_KEYS)
    AND p.VERSION_ = (SELECT MAX(p2.VERSION_) FROM ${DB}.ACT_RE_PROCDEF p2 WHERE p2.KEY_ = p.KEY_);")
if [ -z "$RAW_DEP_IDS" ]; then
  echo "ERROR: 库中未找到 DSN_alloc_corp_designer / DSN_alloc_retail_designer 的部署。" >&2
  echo "       请先在设计器发布对公/零售分配关系调整审批后再运行本脚本。" >&2
  exit 1
fi
# 拼成 SQL 列表：'id1','id2'
DEP_IN=$(echo "$RAW_DEP_IDS" | sed "s/.*/'&'/" | paste -sd, -)
echo "[gen] 设计器流程生效版部署 ID = $DEP_IN" >&2

# 三张表按 deployment 精确过滤导出（顺序：部署 → 二进制 → 流程定义）
emit_table_where "③bis 设计器流程部署 ACT_RE_DEPLOYMENT（仅对公/零售分配调整生效版）" \
  ACT_RE_DEPLOYMENT "ID_ IN ($DEP_IN)"
emit_table_where "③bis 设计器流程二进制 ACT_GE_BYTEARRAY（BPMN/流程图）" \
  ACT_GE_BYTEARRAY "DEPLOYMENT_ID_ IN ($DEP_IN)"
emit_table_where "③bis 设计器流程定义 ACT_RE_PROCDEF（DSN_alloc_*_designer 生效版）" \
  ACT_RE_PROCDEF "DEPLOYMENT_ID_ IN ($DEP_IN)"

# ④ 指标数据：版本控制 + 指标定义/引用 + KPI 方案/项 + 目标方案
emit_group "④ 指标数据（版本/指标定义/引用/KPI方案/目标方案）" \
  SYS_CONTROL PERF_METRIC_DEF PERF_METRIC_REF PERF_KPI_SCHEME PERF_KPI_ITEM PERF_TARGET_PLAN

echo "" >> "$OUT_SEED"
echo "SET FOREIGN_KEY_CHECKS=1;" >> "$OUT_SEED"

echo "生成完成："
wc -l "$OUT_DDL" "$OUT_SEED"
