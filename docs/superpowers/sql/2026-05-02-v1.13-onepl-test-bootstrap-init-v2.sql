-- ============================================================
-- onepl_test_bootstrap 测试库幂等全量初始化 v2（V1.13 # 1，2026-05-02）
-- ============================================================
--
-- 替代：docs/superpowers/sql/2026-04-28-onepl-test-bootstrap-init.sql（v1，注释化指引）
-- 状态：v1 保留作为历史归档，新搭建/重建测试库一律用本脚本
--
-- 本脚本设计目标：
--   1. 幂等可重复执行（DDL 全部 CREATE TABLE IF NOT EXISTS / patch 自带 INFORMATION_SCHEMA 检查）
--   2. 自动化（mysql client `\.` SOURCE 命令一键 source 全量 ddl + seed + patch）
--   3. 与 V1.10 测试库治理 + V1.12 schema/data cleanup + V1.13 lowercase drop 闭环
--
-- 使用方法：
--   cd /path/to/yiti
--   mysql -uroot -pdjdev --default-character-set=utf8mb4 < \
--     docs/superpowers/sql/2026-05-02-v1.13-onepl-test-bootstrap-init-v2.sql
--
-- 重建/重置场景：
--   先备份再 DROP DATABASE，然后重新执行本脚本：
--     mysqldump -uroot -pdjdev --single-transaction onepl_test_bootstrap | gzip > \
--       docs/superpowers/sql/backup/$(date +%Y-%m-%d)-onepl-test-bootstrap-pre-rebuild.sql.gz
--     mysql -uroot -pdjdev -e "DROP DATABASE onepl_test_bootstrap"
--     mysql -uroot -pdjdev < docs/superpowers/sql/2026-05-02-v1.13-onepl-test-bootstrap-init-v2.sql
--
-- 依赖：
--   - 本脚本假设当前工作目录是项目根（cwd = /path/to/yiti）
--   - 所有 SOURCE 路径相对项目根
--   - mysql client 8.0+
--
-- ============================================================
-- 步骤 1：建库（utf8mb4 + general_ci）
-- ============================================================

CREATE DATABASE IF NOT EXISTS onepl_test_bootstrap
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci
    COMMENT 'bootstrap/perf/portal/report 模块 IT 共享测试库（V1.13 # 1 一统）';

USE onepl_test_bootstrap;

-- ============================================================
-- 步骤 2：导入 9 个模块的 DDL（业务表 + Quartz）
-- ============================================================
-- 顺序：auth → governance → workflow → customer → portal → bizapp → performance → report → quartz
-- 全部 CREATE TABLE IF NOT EXISTS，重复执行无副作用
\. docs/schema/ddl-auth.sql
\. docs/schema/ddl-governance.sql
\. docs/schema/ddl-workflow.sql
\. docs/schema/ddl-customer.sql
\. docs/schema/ddl-portal.sql
\. docs/schema/ddl-bizapp.sql
\. docs/schema/ddl-performance.sql
\. docs/schema/ddl-report.sql
\. docs/schema/ddl-quartz.sql

-- ============================================================
-- 步骤 3：初始种子（PT_RESOURCE / PT_ROLE / PT_USER / 字典 / workflow seed）
-- ============================================================
-- 注意：seed-v1.sql 历史 PT_* 数据被 2026-04-10-pt-align-and-test-seed.sql 覆盖，
-- 本步骤只跑 seed-v1.sql 的字典/calendar/portal_nav 等非 PT_* 段（IF EXISTS / 幂等）。
-- 如需完整 PT_* 测试种子，请额外跑：
--   \. docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql
\. docs/schema/seed-v1.sql
\. docs/schema/workflow-seed-v1.sql

-- ============================================================
-- 步骤 4：累计 patch（V1.12 + V1.13 # 1，幂等）
-- ============================================================
-- V1.12 # 5 schema column drift（TOUCH_TASK.sla_warning + PERF_METRIC_DEF V1.7 三列）
-- 全部 INFORMATION_SCHEMA + IF 检查，已在位时跳过
\. docs/superpowers/sql/2026-05-01-v1.12-schema-column-drift-fix.sql

-- V1.12 # 6 DROP 8 张 customer 小写历史表
\. docs/superpowers/sql/2026-05-01-v1.12-drop-customer-lowercase-tables.sql

-- V1.13 # 1 DROP 23 张其他模块小写历史表
\. docs/superpowers/sql/2026-05-02-v1.13-drop-other-lowercase-tables.sql

-- ============================================================
-- 步骤 5：Flowable / ACT_*/FLW_* 元数据（首次搭库时手动）
-- ============================================================
-- Flowable 7.0.1 + Druid 首次启动有 schemaUpdate 死循环（详见旧 init.sql 步骤 5），
-- 规避方案是从生产 onepl 库 dump 回 ACT_*/FLW_* schema + 元数据，本脚本不内联（库特定）。
-- 见旧脚本 docs/superpowers/sql/2026-04-28-onepl-test-bootstrap-init.sql 步骤 5 的 bash 流程。
--
-- 如果当前 onepl_test_bootstrap 已含 ACT_*/FLW_* 全套（典型 35 张）则可跳过本步骤。

-- ============================================================
-- 步骤 6：验证
-- ============================================================
SELECT 'business_tables' AS chk, COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema='onepl_test_bootstrap'
  AND HEX(LEFT(table_name,1)) BETWEEN HEX('A') AND HEX('Z')
  AND table_name NOT LIKE 'ACT_%'
  AND table_name NOT LIKE 'FLW_%'
  AND table_name NOT LIKE 'QRTZ_%';
-- 期望 cnt=59（V1.13 # 1 baseline = 9 模块 ddl 56 张 + V1.x 增量 3 张如 PERF_EXPORT_TASK）

SELECT 'quartz_tables' AS chk, COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema='onepl_test_bootstrap' AND table_name LIKE 'QRTZ_%';
-- 期望 cnt=11

SELECT 'lowercase_residue' AS chk, COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema='onepl_test_bootstrap'
  AND HEX(LEFT(table_name,1)) BETWEEN HEX('a') AND HEX('z')
  AND table_name NOT LIKE 'flyway%';
-- 期望 cnt=0（V1.13 # 1 清理后）

-- ============================================================
-- 与 V1.x 治理的关系
-- ============================================================
-- V1.10：测试库合一（onepl_test_bootstrap 替代 H2 + onepl_test_v103）
-- V1.11 # 1：listener 嵌套 AFTER_COMMIT 修复（不动 schema）
-- V1.12 # 5/# 6：schema column drift + DROP 8 customer 小写
-- V1.13 # 1：DROP 23 其他小写 + 测试库初始化幂等化（本脚本）+ perf/portal 解绑 yiti（test profile）
-- 闭环：V1.10/V1.11 # 1/V1.12/V1.13 # 1 = 完整测试库基础设施治理
