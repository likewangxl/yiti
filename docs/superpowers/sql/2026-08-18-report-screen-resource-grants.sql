-- 报表分析：大屏数据源、大屏设计器、机构经营画像、命名机构组资源及授权（2026-08-18）
--
-- 仅处理 PT_RESOURCE、PT_ROLE_RESOURCE 中的资源登记与授权：不建表、不撤权，且不创建临时或中转对象。
-- 投产前后盘点、资源身份冲突处理和授权验收均应在本文件外按获批流程完成。
-- 幂等策略：缺失资源/绑定才插入；已存在资源按当前控制器契约对齐。
--
-- 授权边界：
-- 1. 常规大屏管理能力继承已启用 R_RPT_SCR_CFG_SAVE 的角色集合；
-- 2. 数据源试跑为高危能力，仅继承已启用 R_RPT_SQL_EXEC 的角色集合；
-- 3. 屏级白名单保存、机构画像/机构组 API 和四个菜单精确授予启用 SYS_ADMIN；
-- 4. 数据源列探测 R_RPT_SCR_DS_PROBE 只登记资源，默认不授权，须走单独审批。

START TRANSACTION;

-- 资源登记：大屏数据源（7）、大屏设计器（15）、机构经营画像/命名机构组（7）、菜单（4）。
UPDATE PT_RESOURCE target_resource
JOIN (
    SELECT 'R_RPT_SCR_DS_LIST' AS resource_id, '/api/screen/admin/datasources' AS resource_url, 'GET' AS resource_method, '大屏-数据源列表' AS menu_name, 0 AS menu_rank_no, 0 AS ismenu, '0' AS menu_endflag, NULL AS parent_resource_id, 0 AS status, 'RPT' AS sys_code, '大屏数据源列表' AS remark
    UNION ALL SELECT 'R_RPT_SCR_DS_SAVE', '/api/screen/admin/datasources', 'POST', '大屏-数据源新建', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源新建'
    UNION ALL SELECT 'R_RPT_SCR_DS_UPD', '/api/screen/admin/datasources/*', 'PUT', '大屏-数据源更新', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源更新'
    UNION ALL SELECT 'R_RPT_SCR_DS_DEL', '/api/screen/admin/datasources/*', 'DELETE', '大屏-数据源删除', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源删除'
    UNION ALL SELECT 'R_RPT_SCR_DS_TRY', '/api/screen/admin/datasources/try-run', 'POST', '大屏-数据源试跑', 0, 0, '0', NULL, 0, 'RPT', '高危大屏数据源试跑'
    UNION ALL SELECT 'R_RPT_SCR_DS_PROBE', '/api/screen/admin/datasources/*/probe-columns', 'POST', '大屏-数据源列探测', 0, 0, '0', NULL, 0, 'RPT', '高危列探测，默认不授权'
    UNION ALL SELECT 'R_RPT_SCR_KPI_SCH', '/api/screen/admin/kpi-schemes', 'GET', '大屏-KPI方案下拉', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源KPI方案下拉'
    UNION ALL SELECT 'R_RPT_SCR_CFG_LIST', '/api/screen/admin/screens', 'GET', '大屏-屏列表', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器屏列表'
    UNION ALL SELECT 'R_RPT_SCR_CFG_GET', '/api/screen/admin/screens/*', 'GET', '大屏-屏详情', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器屏详情'
    UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE', '/api/screen/admin/screens', 'POST', '大屏-屏保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器新建屏'
    UNION ALL SELECT 'R_RPT_SCR_CFG_DEL', '/api/screen/admin/screens/*', 'DELETE', '大屏-屏删除', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器删除屏'
    UNION ALL SELECT 'R_RPT_SCR_META_SAVE', '/api/screen/admin/screens/*/metadata', 'PUT', '大屏-元数据范围更新', 0, 0, '0', NULL, 0, 'RPT', '更新屏元数据与机构范围'
    UNION ALL SELECT 'R_RPT_SCR_MAP_LIST', '/api/screen/admin/map-points', 'GET', '大屏-点位列表', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器点位列表'
    UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE', '/api/screen/admin/map-points', 'PUT', '大屏-点位保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器点位保存'
    UNION ALL SELECT 'R_RPT_SCR_AR_LIST', '/api/screen/admin/screens/*/access-roles', 'GET', '大屏-查看角色列表', 0, 0, '0', NULL, 0, 'RPT', '查询屏级查看角色白名单'
    UNION ALL SELECT 'R_RPT_SCR_AR_SAVE', '/api/screen/admin/screens/*/access-roles', 'PUT', '大屏-查看角色覆盖保存', 0, 0, '0', NULL, 0, 'RPT', '高危屏级查看角色白名单保存'
    UNION ALL SELECT 'R_RPT_SCR_CV_GET', '/api/screen/admin/canvas/*', 'GET', '大屏-画布加载', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器画布加载'
    UNION ALL SELECT 'R_RPT_SCR_CV_SAVE', '/api/screen/admin/canvas/save', 'POST', '大屏-画布保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器草稿保存'
    UNION ALL SELECT 'R_RPT_SCR_CV_PUB', '/api/screen/admin/canvas/publish', 'POST', '大屏-画布发布', 0, 0, '0', NULL, 0, 'RPT', '高危大屏画布发布'
    UNION ALL SELECT 'R_RPT_SCR_CV_RB', '/api/screen/admin/canvas/rollback', 'POST', '大屏-画布回滚', 0, 0, '0', NULL, 0, 'RPT', '高危大屏画布回滚'
    UNION ALL SELECT 'R_RPT_SCR_CV_DISC', '/api/screen/admin/canvas/discard', 'POST', '大屏-放弃草稿', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器放弃草稿'
    UNION ALL SELECT 'R_RPT_SCR_CV_LOG', '/api/screen/admin/canvas/*/publish-logs', 'GET', '大屏-发布归档', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器发布归档'
    UNION ALL SELECT 'A_ORG_PROF_LIST', '/api/admin/org-profiles', 'GET', '机构画像-列表', 0, 0, '0', NULL, 0, 'PLATFORM', '机构经营画像列表'
    UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', '机构画像-保存', 0, 0, '0', NULL, 0, 'PLATFORM', '机构经营画像保存'
    UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', '机构组-列表', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组列表'
    UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', '机构组-新建', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组新建'
    UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', '机构组-修改', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组基本信息修改'
    UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', '机构组-成员覆盖', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组成员覆盖保存'
    UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', '机构组-角色绑定', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组角色绑定保存'
    UNION ALL SELECT 'M_RPT_SCR_DS', '/screen-admin/datasources', 'MENU', '大屏数据源', 7, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏数据源管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_DSN', '/screen-admin/designer', 'MENU', '大屏设计器', 8, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏设计器管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_PROF', '/screen-admin/org-profiles', 'MENU', '机构经营画像', 9, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏机构经营画像管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_GRP', '/screen-admin/org-groups', 'MENU', '命名机构组', 10, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏命名机构组管理菜单'
) expected_resource ON target_resource.RESOURCE_ID = expected_resource.resource_id
SET target_resource.RESOURCE_URL = expected_resource.resource_url,
    target_resource.RESOURCE_METHOD = expected_resource.resource_method,
    target_resource.MENU_NAME = expected_resource.menu_name,
    target_resource.MENU_ICON_URL = NULL,
    target_resource.MENU_RANK_NO = expected_resource.menu_rank_no,
    target_resource.ISMENU = expected_resource.ismenu,
    target_resource.MENU_ENDFLAG = expected_resource.menu_endflag,
    target_resource.PARENT_RESOURCE_ID = expected_resource.parent_resource_id,
    target_resource.STATUS = expected_resource.status,
    target_resource.SYS_CODE = expected_resource.sys_code,
    target_resource.UPDATE_TIME = NOW(),
    target_resource.UPDATE_USER = '2026-08-18-screen-rbac',
    target_resource.REMARK = expected_resource.remark;

INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
     ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE,
     CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
SELECT expected_resource.resource_id, expected_resource.resource_url, expected_resource.resource_method,
       expected_resource.menu_name, NULL, expected_resource.menu_rank_no,
       expected_resource.ismenu, expected_resource.menu_endflag, expected_resource.parent_resource_id,
       expected_resource.status, expected_resource.sys_code,
       NOW(), '2026-08-18-screen-rbac', NOW(), '2026-08-18-screen-rbac', expected_resource.remark
FROM (
    SELECT 'R_RPT_SCR_DS_LIST' AS resource_id, '/api/screen/admin/datasources' AS resource_url, 'GET' AS resource_method, '大屏-数据源列表' AS menu_name, 0 AS menu_rank_no, 0 AS ismenu, '0' AS menu_endflag, NULL AS parent_resource_id, 0 AS status, 'RPT' AS sys_code, '大屏数据源列表' AS remark
    UNION ALL SELECT 'R_RPT_SCR_DS_SAVE', '/api/screen/admin/datasources', 'POST', '大屏-数据源新建', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源新建'
    UNION ALL SELECT 'R_RPT_SCR_DS_UPD', '/api/screen/admin/datasources/*', 'PUT', '大屏-数据源更新', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源更新'
    UNION ALL SELECT 'R_RPT_SCR_DS_DEL', '/api/screen/admin/datasources/*', 'DELETE', '大屏-数据源删除', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源删除'
    UNION ALL SELECT 'R_RPT_SCR_DS_TRY', '/api/screen/admin/datasources/try-run', 'POST', '大屏-数据源试跑', 0, 0, '0', NULL, 0, 'RPT', '高危大屏数据源试跑'
    UNION ALL SELECT 'R_RPT_SCR_DS_PROBE', '/api/screen/admin/datasources/*/probe-columns', 'POST', '大屏-数据源列探测', 0, 0, '0', NULL, 0, 'RPT', '高危列探测，默认不授权'
    UNION ALL SELECT 'R_RPT_SCR_KPI_SCH', '/api/screen/admin/kpi-schemes', 'GET', '大屏-KPI方案下拉', 0, 0, '0', NULL, 0, 'RPT', '大屏数据源KPI方案下拉'
    UNION ALL SELECT 'R_RPT_SCR_CFG_LIST', '/api/screen/admin/screens', 'GET', '大屏-屏列表', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器屏列表'
    UNION ALL SELECT 'R_RPT_SCR_CFG_GET', '/api/screen/admin/screens/*', 'GET', '大屏-屏详情', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器屏详情'
    UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE', '/api/screen/admin/screens', 'POST', '大屏-屏保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器新建屏'
    UNION ALL SELECT 'R_RPT_SCR_CFG_DEL', '/api/screen/admin/screens/*', 'DELETE', '大屏-屏删除', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器删除屏'
    UNION ALL SELECT 'R_RPT_SCR_META_SAVE', '/api/screen/admin/screens/*/metadata', 'PUT', '大屏-元数据范围更新', 0, 0, '0', NULL, 0, 'RPT', '更新屏元数据与机构范围'
    UNION ALL SELECT 'R_RPT_SCR_MAP_LIST', '/api/screen/admin/map-points', 'GET', '大屏-点位列表', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器点位列表'
    UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE', '/api/screen/admin/map-points', 'PUT', '大屏-点位保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器点位保存'
    UNION ALL SELECT 'R_RPT_SCR_AR_LIST', '/api/screen/admin/screens/*/access-roles', 'GET', '大屏-查看角色列表', 0, 0, '0', NULL, 0, 'RPT', '查询屏级查看角色白名单'
    UNION ALL SELECT 'R_RPT_SCR_AR_SAVE', '/api/screen/admin/screens/*/access-roles', 'PUT', '大屏-查看角色覆盖保存', 0, 0, '0', NULL, 0, 'RPT', '高危屏级查看角色白名单保存'
    UNION ALL SELECT 'R_RPT_SCR_CV_GET', '/api/screen/admin/canvas/*', 'GET', '大屏-画布加载', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器画布加载'
    UNION ALL SELECT 'R_RPT_SCR_CV_SAVE', '/api/screen/admin/canvas/save', 'POST', '大屏-画布保存', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器草稿保存'
    UNION ALL SELECT 'R_RPT_SCR_CV_PUB', '/api/screen/admin/canvas/publish', 'POST', '大屏-画布发布', 0, 0, '0', NULL, 0, 'RPT', '高危大屏画布发布'
    UNION ALL SELECT 'R_RPT_SCR_CV_RB', '/api/screen/admin/canvas/rollback', 'POST', '大屏-画布回滚', 0, 0, '0', NULL, 0, 'RPT', '高危大屏画布回滚'
    UNION ALL SELECT 'R_RPT_SCR_CV_DISC', '/api/screen/admin/canvas/discard', 'POST', '大屏-放弃草稿', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器放弃草稿'
    UNION ALL SELECT 'R_RPT_SCR_CV_LOG', '/api/screen/admin/canvas/*/publish-logs', 'GET', '大屏-发布归档', 0, 0, '0', NULL, 0, 'RPT', '大屏设计器发布归档'
    UNION ALL SELECT 'A_ORG_PROF_LIST', '/api/admin/org-profiles', 'GET', '机构画像-列表', 0, 0, '0', NULL, 0, 'PLATFORM', '机构经营画像列表'
    UNION ALL SELECT 'A_ORG_PROF_EDIT', '/api/admin/org-profiles/*', 'PUT', '机构画像-保存', 0, 0, '0', NULL, 0, 'PLATFORM', '机构经营画像保存'
    UNION ALL SELECT 'A_ORG_GRP_LIST', '/api/admin/org-groups', 'GET', '机构组-列表', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组列表'
    UNION ALL SELECT 'A_ORG_GRP_CREATE', '/api/admin/org-groups', 'POST', '机构组-新建', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组新建'
    UNION ALL SELECT 'A_ORG_GRP_EDIT', '/api/admin/org-groups/*', 'PUT', '机构组-修改', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组基本信息修改'
    UNION ALL SELECT 'A_ORG_GRP_MEM', '/api/admin/org-groups/*/members', 'PUT', '机构组-成员覆盖', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组成员覆盖保存'
    UNION ALL SELECT 'A_ORG_GRP_ROLE', '/api/admin/org-groups/*/roles', 'PUT', '机构组-角色绑定', 0, 0, '0', NULL, 0, 'PLATFORM', '命名机构组角色绑定保存'
    UNION ALL SELECT 'M_RPT_SCR_DS', '/screen-admin/datasources', 'MENU', '大屏数据源', 7, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏数据源管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_DSN', '/screen-admin/designer', 'MENU', '大屏设计器', 8, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏设计器管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_PROF', '/screen-admin/org-profiles', 'MENU', '机构经营画像', 9, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏机构经营画像管理菜单'
    UNION ALL SELECT 'M_RPT_SCR_GRP', '/screen-admin/org-groups', 'MENU', '命名机构组', 10, 1, '1', 'M_GROUP_REPORT', 0, 'YITI', '大屏命名机构组管理菜单'
) expected_resource
WHERE NOT EXISTS (
    SELECT 1
    FROM PT_RESOURCE existing_resource
    WHERE existing_resource.RESOURCE_ID = expected_resource.resource_id
);

-- 常规大屏管理能力：继承现有屏保存资源的已启用角色集合。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), base_grant.ROLE_ID, target_resource.resource_id, 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE base_grant
JOIN PT_ROLE base_role
  ON base_role.ROLE_ID = base_grant.ROLE_ID
CROSS JOIN (
    SELECT 'R_RPT_SCR_DS_LIST' AS resource_id
    UNION ALL SELECT 'R_RPT_SCR_DS_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_DS_UPD'
    UNION ALL SELECT 'R_RPT_SCR_DS_DEL'
    UNION ALL SELECT 'R_RPT_SCR_KPI_SCH'
    UNION ALL SELECT 'R_RPT_SCR_CFG_LIST'
    UNION ALL SELECT 'R_RPT_SCR_CFG_GET'
    UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_CFG_DEL'
    UNION ALL SELECT 'R_RPT_SCR_META_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_MAP_LIST'
    UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_AR_LIST'
    UNION ALL SELECT 'R_RPT_SCR_CV_GET'
    UNION ALL SELECT 'R_RPT_SCR_CV_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_CV_PUB'
    UNION ALL SELECT 'R_RPT_SCR_CV_RB'
    UNION ALL SELECT 'R_RPT_SCR_CV_DISC'
    UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
    UNION ALL SELECT 'M_RPT_SCR_DS'
    UNION ALL SELECT 'M_RPT_SCR_DSN'
) target_resource
WHERE base_grant.RESOURCE_ID = 'R_RPT_SCR_CFG_SAVE'
  AND base_role.RECORD_STATUS = 0
  AND NOT EXISTS (
      SELECT 1
      FROM PT_ROLE_RESOURCE existing_grant
      WHERE existing_grant.ROLE_ID = base_grant.ROLE_ID
        AND existing_grant.RESOURCE_ID = target_resource.resource_id
  );

-- 高危数据源试跑：只继承既有 SQL 探查执行资源的已启用角色集合。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), base_grant.ROLE_ID, 'R_RPT_SCR_DS_TRY', 'PLATFORM', NOW()
FROM PT_ROLE_RESOURCE base_grant
JOIN PT_ROLE base_role
  ON base_role.ROLE_ID = base_grant.ROLE_ID
WHERE base_grant.RESOURCE_ID = 'R_RPT_SQL_EXEC'
  AND base_role.RECORD_STATUS = 0
  AND NOT EXISTS (
      SELECT 1
      FROM PT_ROLE_RESOURCE existing_grant
      WHERE existing_grant.ROLE_ID = base_grant.ROLE_ID
        AND existing_grant.RESOURCE_ID = 'R_RPT_SCR_DS_TRY'
  );

-- SYS_ADMIN 精确兜底：包含菜单、机构范围管理及屏级白名单保存；不含列探测。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), sys_admin.ROLE_ID, target_resource.resource_id, 'PLATFORM', NOW()
FROM PT_ROLE sys_admin
CROSS JOIN (
    SELECT 'R_RPT_SCR_DS_LIST' AS resource_id
    UNION ALL SELECT 'R_RPT_SCR_DS_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_DS_UPD'
    UNION ALL SELECT 'R_RPT_SCR_DS_DEL'
    UNION ALL SELECT 'R_RPT_SCR_DS_TRY'
    UNION ALL SELECT 'R_RPT_SCR_KPI_SCH'
    UNION ALL SELECT 'R_RPT_SCR_CFG_LIST'
    UNION ALL SELECT 'R_RPT_SCR_CFG_GET'
    UNION ALL SELECT 'R_RPT_SCR_CFG_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_CFG_DEL'
    UNION ALL SELECT 'R_RPT_SCR_META_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_MAP_LIST'
    UNION ALL SELECT 'R_RPT_SCR_MAP_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_AR_LIST'
    UNION ALL SELECT 'R_RPT_SCR_AR_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_CV_GET'
    UNION ALL SELECT 'R_RPT_SCR_CV_SAVE'
    UNION ALL SELECT 'R_RPT_SCR_CV_PUB'
    UNION ALL SELECT 'R_RPT_SCR_CV_RB'
    UNION ALL SELECT 'R_RPT_SCR_CV_DISC'
    UNION ALL SELECT 'R_RPT_SCR_CV_LOG'
    UNION ALL SELECT 'A_ORG_PROF_LIST'
    UNION ALL SELECT 'A_ORG_PROF_EDIT'
    UNION ALL SELECT 'A_ORG_GRP_LIST'
    UNION ALL SELECT 'A_ORG_GRP_CREATE'
    UNION ALL SELECT 'A_ORG_GRP_EDIT'
    UNION ALL SELECT 'A_ORG_GRP_MEM'
    UNION ALL SELECT 'A_ORG_GRP_ROLE'
    UNION ALL SELECT 'M_RPT_SCR_DS'
    UNION ALL SELECT 'M_RPT_SCR_DSN'
    UNION ALL SELECT 'M_RPT_SCR_PROF'
    UNION ALL SELECT 'M_RPT_SCR_GRP'
) target_resource
WHERE sys_admin.ROLE_CODE = 'SYS_ADMIN'
  AND sys_admin.RECORD_STATUS = 0
  AND NOT EXISTS (
      SELECT 1
      FROM PT_ROLE_RESOURCE existing_grant
      WHERE existing_grant.ROLE_ID = sys_admin.ROLE_ID
        AND existing_grant.RESOURCE_ID = target_resource.resource_id
  );

COMMIT;
