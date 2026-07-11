-- 评价任务待处理明细新增「分组部门」列（汇总优先键，空串回退 be_eval_dept）
-- 目标库：yiti + onepl_test_bootstrap，手工执行（项目已废弃 Flyway）
-- 幂等：仅当列不存在时 ADD
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'EVAL_ASSIGN_ITEM'
      AND COLUMN_NAME = 'GROUP_DEPT'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE EVAL_ASSIGN_ITEM ADD COLUMN GROUP_DEPT VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''分组部门（导入快照，汇总优先键，空串则回退 be_eval_dept）'' AFTER BE_EVAL_DEPT',
    'SELECT ''GROUP_DEPT already exists, skip'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
