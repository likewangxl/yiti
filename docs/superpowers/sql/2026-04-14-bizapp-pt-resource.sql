-- ============================================================================
-- 模块：业务申请中心 (business-application-center)
-- 用途：PT_RESOURCE 资源注册 (21 条)
-- 日期：2026-04-14
-- ============================================================================

-- 资产投放申请 (LOAN) — 9 条
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_NAME, RESOURCE_URL, RESOURCE_METHOD, IS_MENU, SYS_CODE, REMARK) VALUES
('B_LOAN_LIST',   '资产投放列表',       '/api/loans',                  'GET',    0, 'BRANCH', '资产投放分页列表'),
('B_LOAN_READ',   '资产投放详情',       '/api/loans/*',                'GET',    0, 'BRANCH', '资产投放申请详情'),
('B_LOAN_CREATE', '创建资产投放',       '/api/loans',                  'POST',   0, 'BRANCH', '创建资产投放草稿'),
('B_LOAN_UPDATE', '更新资产投放',       '/api/loans/*',                'PUT',    0, 'BRANCH', '更新资产投放草稿'),
('B_LOAN_SUBMIT', '提交资产投放审批',   '/api/loans/*/submit',         'POST',   0, 'BRANCH', '提交资产投放审批'),
('B_LOAN_DELETE', '删除资产投放',       '/api/loans/*',                'DELETE',  0, 'BRANCH', '删除资产投放草稿'),
('B_LOAN_CANCEL', '撤回资产投放',       '/api/loans/*/cancel',         'POST',   0, 'BRANCH', '撤回资产投放申请'),
('B_LOAN_EXPORT', '导出资产投放',       '/api/loans/export',           'GET',    0, 'BRANCH', '导出资产投放申请(高危)'),
('B_LOAN_FORM',   '节点表单配置',       '/api/loans/*/node-form/*',    'GET',    0, 'BRANCH', '获取节点表单配置');

-- 中场支持申请-发起侧 (SUPPORT) — 8 条
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_NAME, RESOURCE_URL, RESOURCE_METHOD, IS_MENU, SYS_CODE, REMARK) VALUES
('B_SUP_LIST',    '中场支持列表',       '/api/support-requests',                 'GET',    0, 'BRANCH', '中场支持发起侧列表'),
('B_SUP_READ',    '中场支持详情',       '/api/support-requests/*',               'GET',    0, 'BRANCH', '中场支持申请详情'),
('B_SUP_CREATE',  '创建中场支持',       '/api/support-requests',                 'POST',   0, 'BRANCH', '创建中场支持申请'),
('B_SUP_SUBMIT',  '提交中场支持',       '/api/support-requests/*/submit',        'POST',   0, 'BRANCH', '提交中场支持审批'),
('B_SUP_DELETE',  '删除中场支持',       '/api/support-requests/*',               'DELETE',  0, 'BRANCH', '删除中场支持草稿'),
('B_SUP_CANCEL',  '撤回中场支持',       '/api/support-requests/*/cancel',        'POST',   0, 'BRANCH', '撤回中场支持申请'),
('B_SUP_EXPORT',  '导出中场支持',       '/api/support-requests/export',          'GET',    0, 'BRANCH', '导出中场支持申请(高危)'),
('B_SUP_PROD',    '可用产品列表',       '/api/support-requests/available-products', 'GET', 0, 'BRANCH', '中场支持可用产品');

-- 中场支持申请-承接侧 (SUPPORT_DEPT) — 4 条
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_NAME, RESOURCE_URL, RESOURCE_METHOD, IS_MENU, SYS_CODE, REMARK) VALUES
('B_SUPD_LIST',   '承接侧列表',         '/api/support-dept/requests',              'GET',    0, 'BRANCH', '承接侧申请列表'),
('B_SUPD_DISP',   '秘书派单',           '/api/support-dept/requests/*/dispatch',    'POST',   0, 'BRANCH', '秘书派单'),
('B_SUPD_XFER',   '秘书转交',           '/api/support-dept/requests/*/transfer',    'POST',   0, 'BRANCH', '秘书转交(高危)'),
('B_SUPD_DONE',   '办理完成',           '/api/support-dept/requests/*/complete',    'POST',   0, 'BRANCH', '支持人员办理完成');
