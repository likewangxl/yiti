-- =====================================================================
-- KPI 方案「员工范围」由角色改为人员标签
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 变更：PERF_KPI_SCHEME.emp_role_scope(角色编码CSV) → emp_tag_scope(标签ID CSV)
--   标签 ID 指向 PERSON_TAG.TAG_ID（人员标签页面维护，见
--   docs/superpowers/sql/2026-07-20-person-tag-tables-and-menu.sql）。
--   方案生效员工范围 = 所选标签关联员工的并集（多选，空=不限定全员）。
--
-- 【数据处理】旧角色范围无法自动映射为标签，改列后置空（本库仅 2 条测试数据
--   KPI_0610 / KPI_TEST_1 配过角色范围），上线后由业务在页面重新选标签。
-- 【幂等】用 information_schema 判断，可重复执行。
-- 【执行前务必备份】PERF_KPI_SCHEME、PT_RESOURCE。
-- =====================================================================

-- ① 改列名并置空（旧值为角色编码，与标签 ID 语义不兼容，不做迁移）
SET @has_old := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'PERF_KPI_SCHEME'
                   AND COLUMN_NAME = 'emp_role_scope');
SET @sql := IF(@has_old > 0,
  'ALTER TABLE PERF_KPI_SCHEME CHANGE COLUMN `emp_role_scope` `emp_tag_scope` varchar(500) DEFAULT NULL COMMENT ''员工标签范围(PERSON_TAG.TAG_ID CSV，空=不限定全员)''',
  'SELECT ''emp_role_scope 不存在，跳过改列'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 若为全新库（既无旧列也无新列）则补建新列
SET @has_new := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'PERF_KPI_SCHEME'
                   AND COLUMN_NAME = 'emp_tag_scope');
SET @sql := IF(@has_new = 0,
  'ALTER TABLE PERF_KPI_SCHEME ADD COLUMN `emp_tag_scope` varchar(500) DEFAULT NULL COMMENT ''员工标签范围(PERSON_TAG.TAG_ID CSV，空=不限定全员)''',
  'SELECT ''emp_tag_scope 已存在，跳过建列'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧角色编码值清空（改列会保留原值，须清掉否则被当成标签 ID 解析）
UPDATE PERF_KPI_SCHEME
   SET emp_tag_scope = NULL
 WHERE emp_tag_scope IS NOT NULL
   AND emp_tag_scope REGEXP '[^0-9,[:space:]]';

-- ② 下线角色下拉端点资源（改用人员标签下拉 G_PTAG_LIST）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPI_ROLES';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'P_PERF_KPI_ROLES';

-- =====================================================================
-- 部署后自检：
--   SHOW COLUMNS FROM PERF_KPI_SCHEME LIKE 'emp_%_scope';                       -- 期望只有 emp_tag_scope
--   SELECT COUNT(*) FROM PERF_KPI_SCHEME WHERE emp_tag_scope REGEXP '[^0-9,]';  -- 期望 0
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID='P_PERF_KPI_ROLES';      -- 期望 0
-- =====================================================================
