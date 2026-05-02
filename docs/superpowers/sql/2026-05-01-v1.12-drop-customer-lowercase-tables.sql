-- V1.12 # 6 DROP 8 customer 小写历史表（2026-05-01）
--
-- 问题描述：
-- mysql Linux 默认 lower_case_table_names=0（case-sensitive），onepl_test_bootstrap
-- 同时存在 8 张 customer 大写表（V1.10 治理后业务用）+ 8 张小写历史表（V1.10 治理前手工创建残留）。
-- 业务代码（mapper）仅用大写表，小写表为冗余历史数据。
--
-- 处置审批（A1）：
-- - cust_master 小写表 1 行（id='cust-seed-001'）= 与大写 CUST_MASTER seed 重复（同一份测试种子）
-- - 其他 7 张小写表 = 0 行
-- - dump backup 已生成：docs/superpowers/sql/backup/2026-05-01-v1.12-customer-lowercase-tables-backup.sql
-- - 安全 DROP（无业务依赖 + 数据已 backup + 重复种子无独立价值）
--
-- 使用方法：
--   mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-05-01-v1.12-drop-customer-lowercase-tables.sql

USE onepl_test_bootstrap;

-- DROP 顺序无依赖（无外键约束）
DROP TABLE IF EXISTS `cust_master`;
DROP TABLE IF EXISTS `cust_lead`;
DROP TABLE IF EXISTS `cust_claim`;
DROP TABLE IF EXISTS `cust_tag`;
DROP TABLE IF EXISTS `cust_tag_rel`;
DROP TABLE IF EXISTS `touch_task`;
DROP TABLE IF EXISTS `touch_log`;
DROP TABLE IF EXISTS `lead_import_batch`;

-- 验证：8 张小写表全部不存在
SELECT 'lowercase_customer_tables_remaining' AS check_item,
       COUNT(*) AS result
FROM information_schema.tables
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name IN (
      'cust_master', 'cust_lead', 'cust_claim',
      'cust_tag', 'cust_tag_rel',
      'touch_task', 'touch_log', 'lead_import_batch'
  );
-- 期望 result=0

-- 验证：大写业务表仍存在（业务正常）
SELECT 'uppercase_customer_tables_present' AS check_item,
       COUNT(*) AS result
FROM information_schema.tables
WHERE table_schema = 'onepl_test_bootstrap'
  AND BINARY table_name IN (
      'CUST_MASTER', 'CUST_LEAD', 'CUST_CLAIM',
      'CUST_TAG', 'CUST_TAG_REL',
      'TOUCH_TASK', 'TOUCH_LOG', 'LEAD_IMPORT_BATCH'
  );
-- 期望 result=8

-- 回滚方法（如需恢复小写表）：
--   mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/backup/2026-05-01-v1.12-customer-lowercase-tables-backup.sql
-- 注意：backup 仅含数据 INSERT，需先用 customer-marketing-schema.sql 重建表结构（小写）
