#!/usr/bin/env bash
# yiti 内网部署导出脚本
# 产出：全部表结构（剔除外键约束）+ 指定 12 张表的数据
# 用法：  bash export-deploy.sh            # 用下面默认连接
#        DB_PASS=xxx DB_HOST=10.x bash export-deploy.sh   # 覆盖连接参数
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-djdev}"
DB_NAME="${DB_NAME:-yiti}"
OUT="${OUT:-yiti_deploy_$(date +%Y%m%d).sql}"

# 需要带数据的表（其余表只导结构）
DATA_TABLES="\
PT_RESOURCE PT_ROLE PT_ROLE_RESOURCE PT_ROLE_BIZ_SCOPE PT_USER PT_USER_ROLE \
EXT_ORG_INFO EXT_USER_ORG \
SYS_DICT SYS_CONFIG_KV PORTAL_NAV SYS_CALENDAR_DAY \
PERF_METRIC_DEF SYS_CONTROL \
WF_FLOW_DEF WF_FLOW_NODE WF_FLOW_EDGE WF_FLOW_NODE_APPROVER \
WF_NODE_CANDIDATE_CONF WF_NODE_FORM_CONF WF_TIMEOUT_RULE \
SYS_JOB_CONF \
PERF_KPI_SCHEME PERF_KPI_ITEM PERF_TARGET_PLAN PERF_TARGET_VALUE \
PRODUCT_INFO ANNOUNCEMENT ANNOUNCEMENT_FILE ADDRBOOK_EMPLOYEE \
EVAL_TAG EVAL_USER_TAG EVAL_RULE_GROUP"

DUMP_OPTS="--single-transaction --skip-comments --set-gtid-purged=OFF --default-character-set=utf8mb4"

# 1) 头部：统一字符集 + 关外键检查（导入按任意顺序都不报错）
{
  echo "-- yiti 内网部署导出  生成时间 $(date '+%F %T')"
  echo "-- 含：全部表结构（已剔除外键约束） + 12 张基础表数据"
  echo "SET NAMES utf8mb4;"
  echo "SET FOREIGN_KEY_CHECKS=0;"
  echo ""
} > "$OUT"

# 2) 全部表结构，用 perl 剔除 CREATE TABLE 里的 FOREIGN KEY 约束行（含前/后逗号修正）
echo ">> 导出表结构..."
mysqldump -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" $DUMP_OPTS --no-data "$DB_NAME" \
  | perl -0777 -pe 's/,?\n\s*CONSTRAINT `[^`]+` FOREIGN KEY [^\n]+//g' \
  >> "$OUT"

# 3) 指定表数据（列名显式，跨 schema 漂移更稳）
echo ">> 导出 12 张表数据..."
mysqldump -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" $DUMP_OPTS --no-create-info --complete-insert \
  "$DB_NAME" $DATA_TABLES \
  >> "$OUT"

# 4) 尾部：恢复外键检查
echo "SET FOREIGN_KEY_CHECKS=1;" >> "$OUT"

echo ">> 完成：$OUT  （$(wc -l < "$OUT") 行，剩余 FOREIGN KEY 约束 $(grep -c 'FOREIGN KEY' "$OUT" || true) 条）"
