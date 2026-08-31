#!/usr/bin/env bash
set -euo pipefail

# 只读验收脚本：禁止使用于 yiti 或正在使用的 yiti_test。
# 该脚本只查询 INFORMATION_SCHEMA 和固定表的 COUNT(*)，不执行任何 DDL/DML。

: "${REDENGINE_TASK_DB_USERNAME:?请设置 REDENGINE_TASK_DB_USERNAME}"
: "${REDENGINE_TASK_DB_PASSWORD:?请设置 REDENGINE_TASK_DB_PASSWORD}"

schema="${REDENGINE_TASK_DB_SCHEMA:-yit_test}"
if [[ "${schema}" != "yit_test" ]]; then
  echo "拒绝执行：验收脚本目标必须是 yit_test，实际为 ${schema}" >&2
  exit 2
fi

mysql_args=(
  --batch
  --skip-column-names
  --raw
  --host="${REDENGINE_TASK_DB_HOST:-127.0.0.1}"
  --port="${REDENGINE_TASK_DB_PORT:-3306}"
  --user="${REDENGINE_TASK_DB_USERNAME}"
  --database="${schema}"
)

query_batch() {
  # 避免把密码放到 mysql 命令行参数中；密码仍只来自调用方环境变量。
  MYSQL_PWD="${REDENGINE_TASK_DB_PASSWORD}" mysql "${mysql_args[@]}" --execute="$1"
}

assert_scalar() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "验收失败：${label}，期望 ${expected}，实际 ${actual}" >&2
    exit 1
  fi
}

task_tables=(
  RE_TASK
  RE_TASK_FILE_TYPE
  RE_TASK_TARGET
  RE_TASK_INSTANCE
  RE_TASK_BRANCH_ASSIGNMENT
  RE_TASK_TODO
  RE_TASK_SUBMISSION
  RE_TASK_SUBMISSION_FILE
  RE_TASK_RE_SUBMIT_REL
  RE_TASK_STATUS_HISTORY
  RE_TASK_DIMENSION_PROGRESS
  RE_TASK_DEDUCTION
  RE_TASK_EXPORT_TASK
)
task_table_sql="'RE_TASK','RE_TASK_FILE_TYPE','RE_TASK_TARGET','RE_TASK_INSTANCE','RE_TASK_BRANCH_ASSIGNMENT','RE_TASK_TODO','RE_TASK_SUBMISSION','RE_TASK_SUBMISSION_FILE','RE_TASK_RE_SUBMIT_REL','RE_TASK_STATUS_HISTORY','RE_TASK_DIMENSION_PROGRESS','RE_TASK_DEDUCTION','RE_TASK_EXPORT_TASK'"

# 每个批次只建立一个连接，避免验收本身制造大量短连接；批次中仍全部是只读查询。
metadata_output="$(query_batch "
SELECT DATABASE();
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql});
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql}) AND CONSTRAINT_TYPE='PRIMARY KEY';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql}) AND CONSTRAINT_TYPE='UNIQUE';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql}) AND CONSTRAINT_TYPE='FOREIGN KEY';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql}) AND CONSTRAINT_TYPE='CHECK';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME IN (${task_table_sql}) AND ENGINE='InnoDB' AND TABLE_COLLATION='utf8mb4_unicode_ci';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME='RE_TASK' AND COLUMN_NAME IN ('TASK_NO','TITLE','DESCRIPTION','NATURE','TYPE_CODE','CYCLE','DURATION_DAYS','START_AT','END_AT','REQUIRES_FILE','STATUS');
SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME='RE_TASK_SUBMISSION' AND COLUMN_NAME IN ('ASSIGNMENT_ID','VERSION_NO','STATUS','CONTENT_TEXT','FORM_DATA','SUBMITTER_ID','REVIEW_OPINION');
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME='RE_TASK_EXPORT_TASK' AND CONSTRAINT_NAME='CK_RE_TASK_EXPORT_TASK_SHEET_LIMIT' AND CONSTRAINT_TYPE='CHECK';
SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='${schema}' AND TABLE_NAME='RE_TASK_SUBMISSION' AND CONSTRAINT_NAME='CK_RE_TASK_SUBMISSION_REJECT_OPINION' AND CONSTRAINT_TYPE='CHECK';
")"
mapfile -t metadata_values <<< "${metadata_output}"
assert_scalar "yit_test" "${metadata_values[0]}" "当前 schema"
assert_scalar "13" "${metadata_values[1]}" "任务域表数量"
assert_scalar "13" "${metadata_values[2]}" "任务域主键数量"
assert_scalar "11" "${metadata_values[3]}" "任务域唯一约束数量"
assert_scalar "44" "${metadata_values[4]}" "任务域外键数量"
assert_scalar "28" "${metadata_values[5]}" "任务域检查约束数量"
assert_scalar "13" "${metadata_values[6]}" "任务域存储引擎/排序规则"

# 关键业务列校验：其余列由 05-表结构DDL.md 的模型说明覆盖。
assert_scalar "11" "${metadata_values[7]}" "RE_TASK 核心列"
assert_scalar "7" "${metadata_values[8]}" "RE_TASK_SUBMISSION 核心列"
assert_scalar "1" "${metadata_values[9]}" "Excel 单 Sheet 5000 行限制"
assert_scalar "1" "${metadata_values[10]}" "驳回意见检查约束"

task_rows_sql=""
for table in "${task_tables[@]}"; do
  task_rows_sql+="SELECT COUNT(*) FROM \`${table}\`;"
done
task_rows_output="$(query_batch "${task_rows_sql}")"
mapfile -t task_rows <<< "${task_rows_output}"
for index in "${!task_tables[@]}"; do
  assert_scalar "0" "${task_rows[index]}" "${task_tables[index]} 初始行数"
done

runtime_tables=(
  SPRING_SESSION
  SPRING_SESSION_ATTRIBUTES
  PT_LOCK
  QRTZ_BLOB_TRIGGERS
  QRTZ_CALENDARS
  QRTZ_CRON_TRIGGERS
  QRTZ_FIRED_TRIGGERS
  QRTZ_JOB_DETAILS
  QRTZ_LOCKS
  QRTZ_PAUSED_TRIGGER_GRPS
  QRTZ_SCHEDULER_STATE
  QRTZ_SIMPLE_TRIGGERS
  QRTZ_SIMPROP_TRIGGERS
  QRTZ_TRIGGERS
  SYS_JOB_CONF
  SYS_JOB_RUN_LOG
  USER_NOTIFICATION
)
runtime_sql=""
for table in "${runtime_tables[@]}"; do
  runtime_sql+="SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME='${table}';"
  runtime_sql+="SELECT COUNT(*) FROM \`${table}\`;"
done
runtime_sql+="SELECT COUNT(*) FROM INFORMATION_SCHEMA.EVENTS WHERE EVENT_SCHEMA='${schema}';"
runtime_sql+="SELECT COUNT(*) FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA='${schema}';"
runtime_output="$(query_batch "${runtime_sql}")"
mapfile -t runtime_values <<< "${runtime_output}"
for index in "${!runtime_tables[@]}"; do
  offset=$((index * 2))
  assert_scalar "1" "${runtime_values[offset]}" "运行态表存在：${runtime_tables[index]}"
  assert_scalar "0" "${runtime_values[offset + 1]}" "运行态表行数：${runtime_tables[index]}"
done
runtime_tail_offset=$((${#runtime_tables[@]} * 2))
assert_scalar "0" "${runtime_values[runtime_tail_offset]}" "数据库事件"
assert_scalar "0" "${runtime_values[runtime_tail_offset + 1]}" "数据库触发器"

echo "TASK_SCHEMA_READONLY_OK schema=${schema} task_tables=13 runtime_tables=17"
