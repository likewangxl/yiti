-- ============================================================================
-- portal-content-center V1 切片 PT_RESOURCE 对齐脚本
-- 创建日期：2026-04-11
-- 关联 spec：docs/superpowers/specs/2026-04-11-portal-content-center-v1-slice-design.md
-- 执行前：备份 PT_RESOURCE 表
-- 幂等：使用 ON DUPLICATE KEY UPDATE
-- ============================================================================

-- (a) 新增 3 条 V1 切片需要的资源
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME, IS_MENU, SYS_CODE, IS_DELETED, OWNER_BY, CREATE_TIME, CREATE_BY, UPDATE_TIME, UPDATE_BY, REMARK)
VALUES
  ('RES_PORTAL_WORKSPACE',          '/api/portal/workspace',           'GET', '工作台聚合',         0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice'),
  ('RES_PRODUCT_SUPPORT_AVAILABLE', '/api/products/support-available', 'GET', '中场支持产品查询',   0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice'),
  ('RES_SHORTCUT_REPLACE_PUT',      '/api/portal/shortcuts',           'PUT', '快捷入口全量替换',   0, '1', 0, 'PLATFORM', NOW(), 'align-2026-04-11', NOW(), 'align-2026-04-11', 'V1 portal slice')
ON DUPLICATE KEY UPDATE
  RESOURCE_NAME=VALUES(RESOURCE_NAME),
  UPDATE_TIME=NOW(),
  UPDATE_BY='align-2026-04-11';

-- (b) 标记 4 条已废弃的旧 dashboard 资源为 deleted=1
UPDATE PT_RESOURCE
SET IS_DELETED = 1, UPDATE_TIME = NOW(), UPDATE_BY = 'align-2026-04-11',
    REMARK = CONCAT(IFNULL(REMARK, ''), ' [deprecated by 2026-04-11 portal slice]')
WHERE RESOURCE_ID IN (
  'RES_PORTAL_TODOS',
  'RES_PORTAL_NOTIFY',
  'RES_PORTAL_NOTIFY_READ',
  'RES_PORTAL_CARDS'
);

-- 验证 SQL（执行后核对）
-- 1. 新增的 3 条应该存在
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, IS_DELETED FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('RES_PORTAL_WORKSPACE','RES_PRODUCT_SUPPORT_AVAILABLE','RES_SHORTCUT_REPLACE_PUT');
-- 期望：3 行，IS_DELETED=0

-- 2. 废弃的 4 条应该 IS_DELETED=1
SELECT RESOURCE_ID, IS_DELETED FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('RES_PORTAL_TODOS','RES_PORTAL_NOTIFY','RES_PORTAL_NOTIFY_READ','RES_PORTAL_CARDS');
-- 期望：4 行，IS_DELETED=1
