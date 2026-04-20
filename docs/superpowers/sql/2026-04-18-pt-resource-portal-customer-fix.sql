-- PT_RESOURCE 端点注册修复脚本 (精简版 - 只插入缺失的记录)
-- 目的: 将 PT_RESOURCE 补充为与 API 文档一致的 URL
-- 日期: 2026-04-18
-- 说明: 执行前已备份 PT_RESOURCE 表
-- 注意: RESOURCE_ID 最大长度 20 字符

USE onepl;

-- ==================== 插入缺失的 portal-content-center 端点 ====================

-- 快捷入口 PUT (ShortcutController 使用 PUT)
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_SHRTCUT_PUT', '/api/portal/shortcuts', 'PUT', '保存快捷入口', 0, 'PLATFORM');

-- 导航
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_NAV_LIST', '/api/nav', 'GET', '导航列表', 0, 'PLATFORM');

-- 管理导航
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_NAV_CREATE', '/api/admin/nav', 'POST', '新增导航', 0, 'PLATFORM'),
('RES_PTL_NAV_UPDATE', '/api/admin/nav/*', 'PUT', '编辑导航', 0, 'PLATFORM'),
('RES_PTL_NAV_DELETE', '/api/admin/nav/*', 'DELETE', '删除导航', 0, 'PLATFORM'),
('RES_PTL_NAV_SORT', '/api/admin/nav/sort', 'PUT', '批量排序', 0, 'PLATFORM');

-- 通讯录
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_ADDR_LIST', '/api/employees', 'GET', '通讯录列表', 0, 'PLATFORM'),
('RES_PTL_ADDR_READ', '/api/employees/*', 'GET', '员工详情', 0, 'PLATFORM'),
('RES_PTL_ADDR_UPDATE', '/api/employees/*', 'PUT', '编辑员工', 0, 'PLATFORM'),
('RES_PTL_ADDR_SRCH', '/api/employees/search', 'GET', '员工搜索', 0, 'PLATFORM');

-- 文档
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_DOC_LIST', '/api/documents', 'GET', '文档列表', 0, 'PLATFORM'),
('RES_PTL_DOC_DOWNLOAD', '/api/documents/*/download', 'GET', '文档下载', 0, 'PLATFORM');

-- 管理文档
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_DOC_CREATE', '/api/admin/documents', 'POST', '上传文档', 0, 'PLATFORM'),
('RES_PTL_DOC_UPDATE', '/api/admin/documents/*', 'PUT', '编辑文档', 0, 'PLATFORM'),
('RES_PTL_DOC_DEL', '/api/admin/documents/*', 'DELETE', '删除文档', 0, 'PLATFORM');

-- 工作台
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_WORKSPACE', '/api/portal/workspace', 'GET', '工作台', 0, 'PLATFORM');

-- 产品导出
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_PRD_EXPRT', '/api/products/export', 'GET', '导出产品', 0, 'PLATFORM');

-- 中场支持产品 (无权限要求)
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_PTL_PRD_SPT_AVL', '/api/products/support-available', 'GET', '中场支持产品', 0, 'PLATFORM');

-- ==================== 插入缺失的 customer-marketing-center 端点 ====================

-- 标签
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_TAG_READ', '/api/tags/*', 'GET', '标签详情', 0, 'PLATFORM'),
('RES_CUST_TAG_UPDATE', '/api/tags/*', 'PUT', '编辑标签', 0, 'PLATFORM'),
('RES_CUST_TAG_STATUS', '/api/tags/*/status', 'PUT', '启用/禁用标签', 0, 'PLATFORM'),
('RES_CUST_TAG_ENA', '/api/tags/enabled', 'GET', '启用标签列表', 0, 'PLATFORM'),
('RES_CUST_TAG_IMPORT', '/api/tags/*/customers/import', 'POST', '标签客户导入', 0, 'PLATFORM'),
('RES_CUST_TAG_EXPORT', '/api/tags/*/customers/export', 'GET', '标签客户导出', 0, 'PLATFORM');

-- 线索
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_LEAD_DETAIL', '/api/leads/*', 'GET', '线索详情', 0, 'PLATFORM'),
('RES_CUST_LEAD_UPDATE', '/api/leads/*', 'PUT', '编辑线索', 0, 'PLATFORM'),
('RES_CUST_LEAD_DELETE', '/api/leads/*', 'DELETE', '删除线索', 0, 'PLATFORM'),
('RES_CUST_LEAD_SUBMIT', '/api/leads/*/submit', 'POST', '提交审批', 0, 'PLATFORM'),
('RES_CUST_LEAD_EDIT', '/api/leads/*/edit', 'POST', '编辑已通过线索', 0, 'PLATFORM');

-- 线索导入
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_LD_IMP_PRE', '/api/leads/import/preview', 'POST', '导入预览', 0, 'PLATFORM'),
('RES_CUST_LD_IMP_BATCH', '/api/leads/import/batches', 'GET', '批次列表', 0, 'PLATFORM'),
('RES_CUST_LD_IMP_BTCHD', '/api/leads/import/batches/*', 'GET', '批次详情', 0, 'PLATFORM');

-- 客户详情
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_CUST_DETAIL', '/api/customers/*', 'GET', '客户详情', 0, 'PLATFORM'),
('RES_CUST_CUST_UPDATE', '/api/customers/*', 'PUT', '编辑客户', 0, 'PLATFORM'),
('RES_CUST_CUST_DELETE', '/api/customers/*/delete-apply', 'POST', '删除申请', 0, 'PLATFORM'),
('RES_CUST_CUST_HIST', '/api/customers/*/history', 'GET', '跨机构历史', 0, 'PLATFORM'),
('RES_CUST_CUST_TRANS', '/api/customers/*/transfer', 'POST', '转交', 0, 'PLATFORM'),
('RES_CUST_CUST_EXPORT', '/api/customers/export', 'GET', '导出客户', 0, 'PLATFORM');

-- 客户池
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_POOL_LIST', '/api/customer-pool', 'GET', '客户池列表', 0, 'PLATFORM'),
('RES_CUST_POOL_CLAIM', '/api/customer-pool/*/claim', 'POST', '认领客户', 0, 'PLATFORM');

-- 认领
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_CLAIM_LST', '/api/my-claims', 'GET', '已认领客户', 0, 'PLATFORM'),
('RES_CUST_CLAIM_CANCEL', '/api/claims/*/cancel', 'POST', '取消认领', 0, 'PLATFORM'),
('RES_CUST_CLAIM_RETOUCH', '/api/claims/*/re-touch', 'POST', '重新触达', 0, 'PLATFORM');

-- 触达任务详情
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_TSK_READ', '/api/touch-tasks/*', 'GET', '触达任务详情', 0, 'PLATFORM'),
('RES_CUST_TSK_SUCCESS', '/api/touch-tasks/*/success', 'POST', '触达成功', 0, 'PLATFORM'),
('RES_CUST_TSK_CANCEL', '/api/touch-tasks/*/cancel', 'POST', '触达取消', 0, 'PLATFORM'),
('RES_CUST_TSK_LOGS', '/api/touch-tasks/*/logs', 'POST', '触达日志', 0, 'PLATFORM');

-- 触达管理视图
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, SYS_CODE) VALUES
('RES_CUST_TRPT_SUM', '/api/touch-reports/summary', 'GET', '触达汇总', 0, 'PLATFORM'),
('RES_CUST_TRPT_LST', '/api/touch-reports', 'GET', '触达明细', 0, 'PLATFORM'),
('RES_CUST_TRPT_EXP', '/api/touch-reports/export', 'GET', '触达导出', 0, 'PLATFORM');

-- ==================== 验证 ====================
SELECT '端点总数' AS info, COUNT(*) AS count FROM PT_RESOURCE;
SELECT '新增快捷入口' AS info, COUNT(*) AS count FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_PTL_SHRTCUT%';
SELECT '新增导航' AS info, COUNT(*) AS count FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_PTL_NAV%';
SELECT '新增通讯录' AS info, COUNT(*) AS count FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_PTL_ADDR%';
SELECT '新增文档' AS info, COUNT(*) AS count FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_PTL_DOC%';
SELECT '新增客户营销' AS info, COUNT(*) AS count FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_CUST%';
