-- portal-content-center 最小可用测试夹具
-- 说明：
-- 1. 当前默认不自动加载，供需要稳定基线数据的测试通过 @Sql 显式引入
-- 2. 所有数据使用 FIXTURE_ 前缀，避免与手工数据和 TEST_ 临时数据冲突

INSERT IGNORE INTO portal_nav (
  id, nav_name, nav_url, nav_icon, nav_category, sort_order, status,
  created_by, created_time, updated_by, updated_time
) VALUES (
  'FIXTURE_NAV_001', 'FIXTURE_核心系统', 'https://fixture.bank.local/core', 'icon-core', '业务系统', 1, 'ACTIVE',
  'SYSTEM', NOW(), 'SYSTEM', NOW()
);

INSERT IGNORE INTO portal_shortcut (
  id, shortcut_name, shortcut_url, shortcut_icon, shortcut_type, target_type, emp_id,
  sort_order, status, created_by, created_time, updated_by, updated_time
) VALUES (
  'FIXTURE_SC_001', 'FIXTURE_工作台', '/portal/workspace', 'icon-workspace', 'SYSTEM', 'INTERNAL', NULL,
  1, 'ACTIVE', 'SYSTEM', NOW(), 'SYSTEM', NOW()
);

INSERT IGNORE INTO product_info (
  id, product_code, product_name, product_category, description, support_for_support_request,
  owner_org_id, product_dept_org_code, file_object_id, status,
  created_by, updated_by, created_time, updated_time, deleted
) VALUES (
  'FIXTURE_PROD_001', 'FIXTURE_PROD_001', '夹具产品', 'CAT_FIXTURE', 'fixture product', 1,
  'ORG_FIXTURE_001', 'ORG_FIXTURE_001', 'FILE_FIXTURE_001', 'ACTIVE',
  'SYSTEM', 'SYSTEM', NOW(), NOW(), 0
);

INSERT IGNORE INTO PORTAL_USER_PRODUCT_REL (
  user_id, product_id, assigned_time, updated_time, updated_by
) VALUES (
  'FIXTURE_USER_001', 'FIXTURE_PROD_001', NOW(), NOW(), 'SYSTEM'
);

INSERT IGNORE INTO doc_info (
  id, doc_title, doc_category, file_object_id, status,
  created_by, created_time, updated_by, updated_time
) VALUES (
  'FIXTURE_DOC_001', 'FIXTURE_制度文档', 'POLICY', 'FILE_FIXTURE_001', 'ACTIVE',
  'SYSTEM', NOW(), 'SYSTEM', NOW()
);
