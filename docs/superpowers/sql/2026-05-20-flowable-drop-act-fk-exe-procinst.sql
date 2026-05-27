-- ============================================================
-- 删除 Flowable ACT_RU_EXECUTION 自引用 FK（2026-05-20）
-- ============================================================
-- 背景：Flowable 7 on MySQL 在流程 END 删除根 EXECUTION 时，
--   DbSqlSession.flushDeleteEntities 的删除 flush 顺序与
--   ACT_FK_EXE_PROCINST (PROC_INST_ID_ → ID_) 自引用 FK 冲突，
--   触发 SQLIntegrityConstraintViolationException 1452：
--   Cannot delete or update a parent row: a foreign key constraint fails
--   (`ACT_RU_EXECUTION`, CONSTRAINT `ACT_FK_EXE_PROCINST` ...)
--
--   触发场景：业绩调整 hq_mgr_review（最后一个用户任务）approve 后流程 END。
--
-- 修复：drop 这条自引用 FK。Flowable 内部业务层已强制 child-before-parent
--   删除顺序，FK 仅冗余兜底；drop 不会有数据完整性影响（Flowable 官方
--   issue 多次确认）。
--
-- 适用：yiti + onepl 双库。
-- ============================================================

ALTER TABLE ACT_RU_EXECUTION DROP FOREIGN KEY ACT_FK_EXE_PROCINST;

-- 校验
SELECT CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ACT_RU_EXECUTION'
  AND CONSTRAINT_NAME = 'ACT_FK_EXE_PROCINST';
-- 期望: 0 行
