-- =====================================================================
-- V1_4_0 紧急回滚脚本（undo）
-- 撤销 V1_4_0 的 perf_target_plan / perf_target_value DDL 变更
--
-- 背景：V1_4_0 为两表各自增加了 owner_emp_id + owner_org_code 字段和
--   idx_owner_emp / idx_owner_org 索引。若应用失败或需紧急回滚 S2.3 切列，
--   运维按此脚本手动撤销.
--
-- 警告：
--   1. 执行前务必备份数据（mysqldump 相关表）
--   2. 本脚本由 Flyway Community Edition 不支持自动 undo，仅供 DBA 手动执行
--   3. 执行后 owner_emp_id / owner_org_code 字段中的数据将丢失
--   4. S2.3 已切列后若回滚本脚本，查询路径会报 "Unknown column 'owner_emp_id'",
--      因此 undo 应与 S2.3 Service / Mapper 回滚同时执行
-- =====================================================================

-- perf_target_plan：先删索引，再删列（避免索引依赖残留）
ALTER TABLE PERF_TARGET_PLAN DROP INDEX idx_owner_emp;
ALTER TABLE PERF_TARGET_PLAN DROP INDEX idx_owner_org;
ALTER TABLE PERF_TARGET_PLAN
    DROP COLUMN owner_org_code,
    DROP COLUMN owner_emp_id;

-- perf_target_value：先删索引，再删列
ALTER TABLE PERF_TARGET_VALUE DROP INDEX idx_owner_emp;
ALTER TABLE PERF_TARGET_VALUE DROP INDEX idx_owner_org;
ALTER TABLE PERF_TARGET_VALUE
    DROP COLUMN owner_org_code,
    DROP COLUMN owner_emp_id;

-- 清理 Flyway 历史（让后续 migrate 重新尝试应用）
DELETE FROM flyway_schema_history WHERE version = '1.4.0';
