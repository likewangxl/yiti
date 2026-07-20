-- =====================================================================
-- 人员标签（全平台通用）—— 建表 + 菜单/资源/角色绑定 种子
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 功能: 系统设置 > 人员标签
--   标签 CRUD（删除级联删关联）、标签成员管理（新增/修改/删除）、
--   全局导入（标签名称+工号，缺标签自动新建，同步原子）、
--   详情导入（工号+姓名，整标签全量覆盖，同步原子）。
--
-- 【幂等】建表用 IF NOT EXISTS；种子先 DELETE 同键再 INSERT，可重复执行。
-- 【执行前务必备份】PT_RESOURCE / PT_ROLE_RESOURCE。
-- =====================================================================

-- ① 建表
CREATE TABLE IF NOT EXISTS `PERSON_TAG` (
  `TAG_ID`      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_NAME`    varchar(100) NOT NULL COMMENT '标签名称',
  `REMARK`      varchar(500) DEFAULT NULL COMMENT '备注',
  `CREATE_BY`   varchar(50)  DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_BY`   varchar(50)  DEFAULT NULL COMMENT '更新人工号',
  `UPDATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`TAG_ID`),
  UNIQUE KEY `UK_PERSON_TAG_NAME` (`TAG_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员标签（全平台通用）';

CREATE TABLE IF NOT EXISTS `PERSON_TAG_REL` (
  `ID`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_ID`      bigint       NOT NULL COMMENT '标签ID（PERSON_TAG.TAG_ID）',
  `USERNAME`    varchar(200) NOT NULL COMMENT '员工工号（PT_USER.USERNAME，注意不是 USER_ID 代理键）',
  `CREATE_BY`   varchar(50)  DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK_PTR_TAG_USER` (`TAG_ID`, `USERNAME`),
  KEY `IDX_PTR_USERNAME` (`USERNAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员标签-人员关联（一人可多标签）';

-- ② 菜单（系统设置组下，排在审批流监控之后）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_SYS_PERSON_TAGS', '/system/person-tags', 'GET', '人员标签', NULL, 15,
   1, '1', 'M_GROUP_SYSTEM', 0, 'PLATFORM', NOW(), 'seed', '人员标签管理页');

-- ③ API 资源（AuthorizationInterceptor 对未登记 URL 一律 403，逐端点登记；Ant 通配 * 匹配单段路径变量）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID LIKE 'G_PTAG_%';
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('G_PTAG_LIST',    '/api/admin/sys/person-tags',                        'GET',    '人员标签-列表',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '标签分页列表(含关联人数)'),
  ('G_PTAG_CREATE',  '/api/admin/sys/person-tags',                        'POST',   '人员标签-新建',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_UPDATE',  '/api/admin/sys/person-tags/*',                      'PUT',    '人员标签-编辑',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_DELETE',  '/api/admin/sys/person-tags/*',                      'DELETE', '人员标签-删除',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '级联删除关联人员'),
  ('G_PTAG_MEMBERS', '/api/admin/sys/person-tags/*/members',              'GET',    '人员标签-成员列表', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '工号/姓名/机构'),
  ('G_PTAG_MEM_ADD', '/api/admin/sys/person-tags/*/members',              'POST',   '人员标签-新增成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_UPD', '/api/admin/sys/person-tags/*/members/*',            'PUT',    '人员标签-修改成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_DEL', '/api/admin/sys/person-tags/*/members/*',            'DELETE', '人员标签-删除成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_IMPORT',  '/api/admin/sys/person-tags/import',                 'POST',   '人员标签-全局导入', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '标签+工号,缺标签自建'),
  ('G_PTAG_TPL',     '/api/admin/sys/person-tags/import-template',        'GET',    '人员标签-全局导入模板', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_IMP', '/api/admin/sys/person-tags/*/import',               'POST',   '人员标签-成员导入', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '整标签全量覆盖'),
  ('G_PTAG_MEM_TPL', '/api/admin/sys/person-tags/member-import-template', 'GET',    '人员标签-成员导入模板', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL);

-- ④ 角色绑定：菜单复用 M_SYS_DICT 的角色集；API 资源复用 G_DICT_CREATE 的角色集（系统管理员）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPTAGM_', ROLE_ID), ROLE_ID, 'M_SYS_PERSON_TAGS', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_DICT';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RR', r.RESOURCE_ID, '_', rr.ROLE_ID), rr.ROLE_ID, r.RESOURCE_ID, 'PLATFORM', NOW()
    FROM PT_RESOURCE r
    JOIN PT_ROLE_RESOURCE rr ON rr.RESOURCE_ID = 'G_DICT_CREATE'
   WHERE r.RESOURCE_ID LIKE 'G_PTAG_%';

-- =====================================================================
-- 部署后自检：
--   SHOW TABLES LIKE 'PERSON_TAG%';                                                   -- 期望 2 张
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';               -- 期望 12
--   SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';          -- 期望 12×角色数
--   SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';    -- 期望 >=1
-- =====================================================================
