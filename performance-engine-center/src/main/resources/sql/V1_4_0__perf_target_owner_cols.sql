-- =====================================================================
-- performance-engine-center V1.4 Phase S2.1 Target owner 字段 DDL
-- Version: V1_4_0
-- Date: 2026-04-24
-- Task: S2.1
--
-- 背景：V1.3 R1.1/R1.2 为 TargetValue / TargetPlan 引入了数据范围注入
--   （PerfScopeHelper），但 DDL 侧缺少独立 owner 字段，ScopeColumns 被迫
--   降级到 created_by：
--     SELF    ownerEmpCol  = "created_by"   （语义偏）
--     ORG     ownerOrgCol  = "created_by"   （语义错：created_by 非 org_code）
--   该降级在 V1.3 CLAUDE.md 技术债章节 #5 登记，V1.4 S2 根治。
--
-- 本脚本：
--   1. 为 perf_target_plan / perf_target_value 各加 2 列 + 2 索引
--   2. 回填 owner_emp_id = created_by（仅填 NULL 行，历史兜底）
--
-- 配套：
--   - V1.4 S2.2: Entity + Mapper + Cmd 字段补齐
--   - V1.4 S2.3: ScopeColumns 从 created_by 切到 owner_emp_id / owner_org_code
--   - V1.4 S2.4: CLAUDE.md 技术债 #5 标记已消化
--
-- ---------------------------------------------------------------------
-- 【生产前置检查 runbook（必须运维在应用前人工执行）】
-- ---------------------------------------------------------------------
-- 1) 检查本期两表行数, 决定迁移策略：
--    SELECT 'perf_target_plan' AS tbl, COUNT(*) FROM perf_target_plan
--    UNION ALL
--    SELECT 'perf_target_value' AS tbl, COUNT(*) FROM perf_target_value;
--
--    - 单表 < 10 万：直接 Flyway 跑本脚本即可（ADD COLUMN + ADD INDEX + UPDATE 一把过）
--    - 单表 > 100 万：建议走【分批回填 runbook】或【pt-online-schema-change】
--
-- ---------------------------------------------------------------------
-- 【分批回填 runbook（生产大表 > 100 万行必须）】
-- ---------------------------------------------------------------------
-- Flyway 本脚本 UPDATE 单语句执行，适用于小中数据量场景。
-- 大表应先在 Flyway 只做 ADD COLUMN + ADD INDEX 的 DDL，然后手动执行：
--
-- DELIMITER //
-- CREATE PROCEDURE batch_backfill_target_value_owner()
-- BEGIN
--   DECLARE done INT DEFAULT 0;
--   WHILE done = 0 DO
--     UPDATE perf_target_value
--     SET owner_emp_id = created_by
--     WHERE owner_emp_id IS NULL
--     LIMIT 10000;
--     IF ROW_COUNT() = 0 THEN SET done = 1; END IF;
--     -- 每批间隔 100ms 降主从复制压力
--     SELECT SLEEP(0.1);
--   END WHILE;
-- END//
-- DELIMITER ;
--
-- CALL batch_backfill_target_value_owner();
-- DROP PROCEDURE batch_backfill_target_value_owner;
--
-- perf_target_plan 同款过程（改表名即可，通常 plan 表体量小，可跳过）.
--
-- ---------------------------------------------------------------------
-- 【备选方案：pt-online-schema-change（Percona Toolkit）】
-- ---------------------------------------------------------------------
-- 若 ADD COLUMN + ADD INDEX 在生产触发长 metadata lock（高并发查询阻塞）：
--
-- pt-online-schema-change --alter \
--   "ADD COLUMN owner_emp_id VARCHAR(32) NULL, \
--    ADD COLUMN owner_org_code VARCHAR(50) NULL, \
--    ADD INDEX idx_owner_emp(owner_emp_id), \
--    ADD INDEX idx_owner_org(owner_org_code)" \
--   D=onepl,t=perf_target_value --execute
--
-- 此时 Flyway 脚本应被调整为仅 UPDATE 回填，ADD COLUMN 部分由 DBA 预先完成。
--
-- ---------------------------------------------------------------------
-- 【回滚策略】
-- ---------------------------------------------------------------------
-- 见 undo-scripts/V1_4_0__undo.sql：
--   DROP INDEX + DROP COLUMN（两表各 4 操作）+ 清理 flyway_schema_history 记录
-- 严禁：直接跳到下个 V1_4_x 脚本，否则 ScopeColumns 切列（S2.3）后线上查询会报列不存在。
-- =====================================================================

-- ---------------------------------------------------------------------
-- perf_target_plan：加 owner_emp_id + owner_org_code 字段 + 对应索引
-- ---------------------------------------------------------------------
ALTER TABLE perf_target_plan
    ADD COLUMN owner_emp_id   VARCHAR(32) NULL COMMENT '归属员工（SELF / SELF_ASSIGNED scope 列）' AFTER status,
    ADD COLUMN owner_org_code VARCHAR(50) NULL COMMENT '归属机构（ORG / ORG_SUBTREE scope 列）' AFTER owner_emp_id,
    ADD INDEX idx_owner_emp (owner_emp_id),
    ADD INDEX idx_owner_org (owner_org_code);

-- ---------------------------------------------------------------------
-- perf_target_value：加 owner_emp_id + owner_org_code 字段 + 对应索引
-- ---------------------------------------------------------------------
ALTER TABLE perf_target_value
    ADD COLUMN owner_emp_id   VARCHAR(32) NULL COMMENT '归属员工（SELF / SELF_ASSIGNED scope 列）' AFTER base_value,
    ADD COLUMN owner_org_code VARCHAR(50) NULL COMMENT '归属机构（ORG / ORG_SUBTREE scope 列）' AFTER owner_emp_id,
    ADD INDEX idx_owner_emp (owner_emp_id),
    ADD INDEX idx_owner_org (owner_org_code);

-- ---------------------------------------------------------------------
-- 历史数据回填（小数据量一键版）
-- owner_emp_id 先用 created_by 兜底；owner_org_code 保留 NULL 等运维按业务后续纠正
-- 大表走分批回填 runbook（见脚本头部）
-- ---------------------------------------------------------------------
UPDATE perf_target_plan  SET owner_emp_id = created_by WHERE owner_emp_id IS NULL;
UPDATE perf_target_value SET owner_emp_id = created_by WHERE owner_emp_id IS NULL;
