-- =====================================================================
-- 审批流监控：放开「未签收候选组任务」的转交（指派语义）
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 背景：候选组任务（如 branch_approve_l2 机构负责人会签）在无人签收时 assignee 为 NULL。
--   正常审批链路对此无要求（PC 端 approve 前自动 claim、手机端 approveTaskByEmp 不校验 assignee），
--   但转交链路此前硬性要求 assignee 非空（WF-40917 任务尚未签收），导致秘书岗无法把
--   「还没人认领的任务」指派给指定负责人——两条链路口径不一致。
--
-- 变更：WF_TASK_TRANSFER.from_emp_id 由 NOT NULL 改为可空。
--   from_emp_id 非空 = 转交（从原办理人 A 手上转给 B）
--   from_emp_id 为空 = 指派（任务尚在候选池无人签收，由发起人直接指派给 B）
--   两者均仍走两阶段（接收人认领后才真正 setAssignee）与发起即锁定（待认领期间原任务只读）。
--
-- 【幂等】用 information_schema 判断，可重复执行。
-- 【执行前务必备份】WF_TASK_TRANSFER。
-- =====================================================================

SET @is_notnull := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'WF_TASK_TRANSFER'
                      AND COLUMN_NAME = 'from_emp_id'
                      AND IS_NULLABLE = 'NO');
SET @sql := IF(@is_notnull > 0,
  'ALTER TABLE WF_TASK_TRANSFER MODIFY COLUMN `from_emp_id` varchar(32) COLLATE utf8mb4_general_ci NULL COMMENT ''原办理人工号；NULL=发起时任务尚未签收(候选池指派)''',
  'SELECT ''from_emp_id 已可空，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 部署后自检：
--   SHOW COLUMNS FROM WF_TASK_TRANSFER LIKE 'from_emp_id';   -- 期望 Null=YES
-- 说明：存量记录的 from_emp_id 均有值（旧逻辑强制非空），无需回填。
-- =====================================================================
