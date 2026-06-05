-- ============================================================================
-- KPI 分值计算后台任务 —— schema 变更（计分公式持久化 + 任务方案号 + 明细结果表）
-- ----------------------------------------------------------------------------
-- 背景：新增"KPI 分值计算"后台定时任务与前端重算接口。涉及三处 schema：
--   1) PERF_KPI_ITEM.formula  —— 计分公式（前端 KpiRules.vue 已编辑，原仅存 localStorage，
--      现持久化入库）。注意：yiti 库历史上已手工加过该列，onepl 缺；脚本幂等，两库都跑。
--   2) PERF_METRIC_CALC_TASK.kpi_scheme_code —— KPI 分值任务登记的"KPI方案编号"
--      （为空=全部方案）。其余 metric_level 等列对 KPI 任务不适用，留默认。
--   3) PERF_KPI_SCORE —— KPI 计分明细结果表（数据日期·方案·指标·对象·实际值·权重·
--      目标值·基础值·得分），唯一键做 upsert。
--
-- 双库：dev=yiti / prod=onepl 均需执行。脚本幂等（INFORMATION_SCHEMA 预检）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. PERF_KPI_ITEM 加 formula 列（幂等：已存在则跳过）
-- ---------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_KPI_ITEM' AND COLUMN_NAME = 'formula');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE PERF_KPI_ITEM ADD COLUMN formula varchar(500) NULL COMMENT ''计分公式（变量 actual/target/base/weight，支持 min/max）'' AFTER max_score',
    'SELECT ''PERF_KPI_ITEM.formula already exists, skip'' ');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. PERF_METRIC_CALC_TASK 加 kpi_scheme_code 列（幂等）
-- ---------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_CALC_TASK' AND COLUMN_NAME = 'kpi_scheme_code');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE PERF_METRIC_CALC_TASK ADD COLUMN kpi_scheme_code varchar(64) NULL COMMENT ''KPI方案编号（KPI分值任务用，空=全部）'' AFTER data_date',
    'SELECT ''PERF_METRIC_CALC_TASK.kpi_scheme_code already exists, skip'' ');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- PERF_METRIC_CALC_TASK.metric_level 原 NOT NULL；KPI 分值任务无级别概念，放宽为可空
SET @nullable := (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_METRIC_CALC_TASK' AND COLUMN_NAME = 'metric_level');
SET @ddl := IF(@nullable = 'NO',
    'ALTER TABLE PERF_METRIC_CALC_TASK MODIFY COLUMN metric_level int NULL COMMENT ''指标级别1/2/3（KPI分值任务为空）''',
    'SELECT ''PERF_METRIC_CALC_TASK.metric_level already nullable, skip'' ');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 3. PERF_KPI_SCORE 计分明细结果表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `PERF_KPI_SCORE` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_date` date NOT NULL COMMENT '数据日期',
  `scheme_code` varchar(64) NOT NULL COMMENT 'KPI方案编码',
  `metric_code` varchar(64) NOT NULL COMMENT '指标编码',
  `subject_type` varchar(10) NOT NULL COMMENT '对象类型 EMP/ORG/CUST',
  `subject_id` varchar(64) NOT NULL COMMENT '对象ID(emp_id/org_code/cust_id)',
  `actual_value` decimal(20,4) DEFAULT NULL COMMENT '实际值（指标结果表槽位值）',
  `weight` decimal(10,4) DEFAULT NULL COMMENT '权重',
  `target_value` decimal(20,4) DEFAULT NULL COMMENT '目标值（未匹配默认0）',
  `base_value` decimal(20,4) DEFAULT NULL COMMENT '基础值（未匹配默认0）',
  `score` decimal(20,4) DEFAULT NULL COMMENT '得分',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_scheme_metric_subject` (`data_date`, `scheme_code`, `metric_code`, `subject_type`, `subject_id`),
  KEY `idx_date_scheme` (`data_date`, `scheme_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='KPI计分明细结果';

-- ---------------------------------------------------------------------------
-- 4. PT_RESOURCE 登记 KPI 分值计算接口 + 角色绑定（沿用 P_PERF_KPI_PUB 的角色集）
--    URL 唯一，避免与既有资源重叠（防 ResourceMatcher.findFirst 命中窄授权）。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
     MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER,
     UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
    ('P_PERF_KPI_SCORE', '/api/perf/kpi-score/calc', 'POST', '计算KPI得分', 0, 0,
     0, 'M_PERF_KPI_RULES', 0, 'PERF', NOW(), 'seed', NOW(), 'seed', 'kpi-score-calc');

-- 角色绑定：先清后建（幂等），复制 P_PERF_KPI_PUB 的角色集到新资源
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPI_SCORE';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 32)),
       s.ROLE_ID, 'P_PERF_KPI_SCORE', s.SYS_CODE, NOW()
FROM (SELECT ROLE_ID, SYS_CODE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPI_PUB') s;

-- ---------------------------------------------------------------------------
-- 5. SYS_JOB_CONF 注册 KPI 分值计算定时任务（指向 KpiScoreCalcJob）
--    cron=每日 06:00，排在 Level3(05:00) 之后；前置依赖三级指标当日完成由服务自检。
--    幂等：job_key 已存在则跳过。
-- ---------------------------------------------------------------------------
INSERT INTO SYS_JOB_CONF
    (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status,
     allow_manual_trigger, created_by, created_time)
SELECT REPLACE(UUID(),'-',''), 'KPI_SCORE_CALC', 'KPI分值计算', '0 0 6 * * ?',
       'com.bank.branch.platform.performance.job.KpiScoreCalcJob', 'FIRE_ONCE_NOW', 'ACTIVE',
       1, 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM SYS_JOB_CONF WHERE job_key = 'KPI_SCORE_CALC');
