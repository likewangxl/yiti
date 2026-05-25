-- =========================================================
-- 2026-05-22 清理 ACT_RU_IDENTITYLINK 中 IL_DEMO_* 旁路 seed 行
-- =========================================================
-- 背景：
--   早期 demo seed 脚本向 ACT_RU_IDENTITYLINK 直接插入了 11 条 ID 以 'IL_DEMO_' 开头
--   的 assignee 行（user_id='admin'），对应到旧 BPMN perf_alloc_adjust_corp_v1 v19
--   的 11 个 branch_mgr_review 任务（这些任务 ACT_RU_TASK.ASSIGNEE_ 已是 'admin'，
--   所以这条 IDENTITYLINK 完全冗余）。
--
--   问题：admin 调 POST /api/workflow/tasks/{id}/approve 时，
--   Flowable TaskServiceImpl.complete 内部按 EntityCache 收集 deletes，
--   IL_DEMO_* 行不在 cache 中 → flushDeletes 时 DELETE FROM ACT_RU_TASK 之前
--   未清理它，外键 ACT_FK_TSKASS_TASK 检查失败：
--
--     Cannot delete or update a parent row: a foreign key constraint fails
--     (`yiti`.`ACT_RU_IDENTITYLINK`, CONSTRAINT `ACT_FK_TSKASS_TASK`
--      FOREIGN KEY (`TASK_ID_`) REFERENCES `ACT_RU_TASK` (`ID_`))
--
-- 部署范围：yiti + onepl 双库（onepl 通常没这些行，DELETE 无害）
--
-- 备份：docs/superpowers/sql/backup/2026-05-22-pre-il-demo-cleanup-yiti.sql
--
-- 幂等：WHERE LIKE 'IL_DEMO_%'，重复执行 0 影响
-- =========================================================

DELETE FROM ACT_RU_IDENTITYLINK WHERE ID_ LIKE 'IL_DEMO_%';

-- 验证
--   SELECT COUNT(*) FROM ACT_RU_IDENTITYLINK WHERE ID_ LIKE 'IL_DEMO_%';
--   -- 期望 0
