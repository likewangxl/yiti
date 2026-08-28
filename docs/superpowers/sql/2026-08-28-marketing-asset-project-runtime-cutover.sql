-- 资产立项运行资源切换脚本（候选稿，须经 DBA/权限管理员评审后执行）
-- 目标：注册客户营销模块资产立项菜单与接口资源，迁移旧 M_BIZ_LOAN/B_LOAN_* 授权并停用旧资源。
-- 边界：本脚本不创建业务表；业务表见 /home/djdev/lijh/2026-08-28-marketing-asset-project-schema-v2.mysql.sql。
-- 注意：仓库禁止可执行 SQL 使用 DELETE；旧授权关系保留作审计追溯，但其父资源会被 STATUS=1 停用。

START TRANSACTION;

INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL,
     MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS,
     SYS_CODE, CREATE_TIME, CREATE_USER, UPDATE_TIME, UPDATE_USER, REMARK)
VALUES
    ('M_MKT_ASSET_PROJECT', '/marketing/asset-projects', 'MENU', '资产立项', NULL,
     13, 1, '1', 'M_GROUP_MARKETING', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '客户营销资产立项工作台'),
    ('C_ASSET_LIST', '/api/marketing/asset-projects', 'GET', '资产立项工作台查询', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '我的申请、待办、已办'),
    ('C_ASSET_READ', '/api/marketing/asset-projects/*', 'GET', '资产立项详情', NULL,
     10, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '资产立项统一详情'),
    ('C_ASSET_CREATE', '/api/marketing/asset-projects', 'POST', '新建资产立项', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '创建资产立项草稿'),
    ('C_ASSET_UPDATE', '/api/marketing/asset-projects/*', 'PUT', '编辑资产立项', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '修改资产立项草稿'),
    ('C_ASSET_SUBMIT', '/api/marketing/asset-projects/*/submit', 'POST', '提交资产立项', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '提交资产立项审批'),
    ('C_ASSET_DELETE', '/api/marketing/asset-projects/*', 'DELETE', '删除资产立项草稿', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '删除草稿并保留审计原因'),
    ('C_ASSET_CANCEL', '/api/marketing/asset-projects/*/cancel', 'POST', '撤回资产立项', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '撤回审批中的资产立项'),
    ('C_ASSET_URG_CTX', '/api/marketing/asset-projects/*/urgent-context', 'GET', '查询资产立项加急上下文', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '加急前置校验'),
    ('C_ASSET_URG_NEW', '/api/marketing/asset-projects/*/urgent-applies', 'POST', '发起资产立项加急', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '发起独立加急审批'),
    ('C_ASSET_URG_LIST', '/api/marketing/asset-projects/*/urgent-applies', 'GET', '查询资产立项加急记录', NULL,
     0, 0, '0', 'M_MKT_ASSET_PROJECT', 0, 'CUSTOMER', NOW(), 'asset-project-0828', NOW(), 'asset-project-0828', '查询加急审批记录') AS new
ON DUPLICATE KEY UPDATE
    RESOURCE_URL=new.RESOURCE_URL, RESOURCE_METHOD=new.RESOURCE_METHOD,
    MENU_NAME=new.MENU_NAME, MENU_RANK_NO=new.MENU_RANK_NO,
    ISMENU=new.ISMENU, MENU_ENDFLAG=new.MENU_ENDFLAG,
    PARENT_RESOURCE_ID=new.PARENT_RESOURCE_ID, STATUS=new.STATUS,
    SYS_CODE=new.SYS_CODE, UPDATE_TIME=NOW(), UPDATE_USER='asset-project-0828',
    REMARK=new.REMARK;

-- 按旧资源粒度迁移角色权限；加急查询沿用详情权限，加急发起沿用提交权限。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT DISTINCT MD5(CONCAT(rr.ROLE_ID, '#', m.NEW_RESOURCE_ID)),
       rr.ROLE_ID, m.NEW_RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE_RESOURCE rr
JOIN (
    SELECT 'M_BIZ_LOAN' OLD_RESOURCE_ID, 'M_MKT_ASSET_PROJECT' NEW_RESOURCE_ID UNION ALL
    SELECT 'B_LOAN_LIST', 'M_MKT_ASSET_PROJECT' UNION ALL
    SELECT 'B_LOAN_LIST', 'C_ASSET_LIST' UNION ALL
    SELECT 'B_LOAN_READ', 'C_ASSET_READ' UNION ALL
    SELECT 'B_LOAN_READ', 'C_ASSET_URG_CTX' UNION ALL
    SELECT 'B_LOAN_READ', 'C_ASSET_URG_LIST' UNION ALL
    SELECT 'B_LOAN_CREATE', 'C_ASSET_CREATE' UNION ALL
    SELECT 'B_LOAN_UPDATE', 'C_ASSET_UPDATE' UNION ALL
    SELECT 'B_LOAN_SUBMIT', 'C_ASSET_SUBMIT' UNION ALL
    SELECT 'B_LOAN_SUBMIT', 'C_ASSET_URG_NEW' UNION ALL
    SELECT 'B_LOAN_DELETE', 'C_ASSET_DELETE' UNION ALL
    SELECT 'B_LOAN_CANCEL', 'C_ASSET_CANCEL'
) m ON m.OLD_RESOURCE_ID=rr.RESOURCE_ID
WHERE NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE target
    WHERE target.ROLE_ID=rr.ROLE_ID AND target.RESOURCE_ID=m.NEW_RESOURCE_ID
);

-- 系统管理员兜底获得新菜单与全部接口资源。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(r.ROLE_ID, '#', p.RESOURCE_ID)), r.ROLE_ID, p.RESOURCE_ID, 'CUSTOMER', NOW()
FROM PT_ROLE r
JOIN PT_RESOURCE p ON p.RESOURCE_ID IN (
    'M_MKT_ASSET_PROJECT','C_ASSET_LIST','C_ASSET_READ','C_ASSET_CREATE','C_ASSET_UPDATE',
    'C_ASSET_SUBMIT','C_ASSET_DELETE','C_ASSET_CANCEL','C_ASSET_URG_CTX','C_ASSET_URG_NEW','C_ASSET_URG_LIST')
WHERE r.ROLE_CODE='SYS_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_RESOURCE rr
      WHERE rr.ROLE_ID=r.ROLE_ID AND rr.RESOURCE_ID=p.RESOURCE_ID
  );

-- 将旧 LOAN 数据范围复制为正式 ASSET_PROJECT 数据范围。
INSERT INTO PT_ROLE_BIZ_SCOPE
    (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER, UPDATE_USER, REMARK)
SELECT MD5(CONCAT(ROLE_ID, '#ASSET_PROJECT')), ROLE_ID, 'ASSET_PROJECT', DATA_SCOPE,
       RECORD_STATUS, 'asset-project-0828', 'asset-project-0828', '由旧 LOAN 数据范围迁移'
FROM PT_ROLE_BIZ_SCOPE old_scope
WHERE old_scope.BIZ_TYPE='LOAN'
  AND COALESCE(old_scope.RECORD_STATUS, 0)=0
  AND NOT EXISTS (
      SELECT 1 FROM PT_ROLE_BIZ_SCOPE new_scope
      WHERE new_scope.ROLE_ID=old_scope.ROLE_ID AND new_scope.BIZ_TYPE='ASSET_PROJECT'
  );

-- 若 ASSET_PROJECT 范围已由人工预建，则以当前有效 LOAN 范围对齐并重新启用，保证脚本可幂等复跑。
UPDATE PT_ROLE_BIZ_SCOPE new_scope
JOIN PT_ROLE_BIZ_SCOPE old_scope
  ON old_scope.ROLE_ID=new_scope.ROLE_ID
 AND old_scope.BIZ_TYPE='LOAN'
 AND COALESCE(old_scope.RECORD_STATUS, 0)=0
SET new_scope.DATA_SCOPE=old_scope.DATA_SCOPE,
    new_scope.RECORD_STATUS=0,
    new_scope.UPDATE_USER='asset-project-0828',
    new_scope.REMARK='由旧 LOAN 数据范围迁移'
WHERE new_scope.BIZ_TYPE='ASSET_PROJECT';

-- 旧接口 /api/loans 已从代码移除。资源改为停用，旧角色授权关系保留作审计追溯。
UPDATE PT_RESOURCE
SET STATUS=1,
    UPDATE_TIME=NOW(),
    UPDATE_USER='asset-project-0828',
    REMARK='资产立项已迁移至 /marketing/asset-projects；旧资源停用'
WHERE RESOURCE_ID IN (
    'M_BIZ_LOAN','B_LOAN_LIST','B_LOAN_READ','B_LOAN_CREATE','B_LOAN_UPDATE',
    'B_LOAN_SUBMIT','B_LOAN_DELETE','B_LOAN_CANCEL','B_LOAN_EXPORT','B_LOAN_FORM'
);

UPDATE PT_ROLE_BIZ_SCOPE
SET RECORD_STATUS=1,
    UPDATE_USER='asset-project-0828',
    REMARK='资产立项已迁移至 ASSET_PROJECT 数据范围'
WHERE BIZ_TYPE='LOAN'
  AND COALESCE(RECORD_STATUS, 0)=0;

COMMIT;
