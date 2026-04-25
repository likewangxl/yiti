-- ============================================================================
-- 2026-04-25 V1.1 整改：PT_RESOURCE 端点 vs 资源对账清零
-- 创建日期：2026-04-25
-- 来源：docs/superpowers/reports/2026-04-25-endpoint-resource-audit.md
--   - 类型 A 26 项：代码端点未注册 → INSERT IGNORE 26 条
--   - 类型 B 11 项：PT_RESOURCE 孤儿 → UPDATE STATUS=1（保留审计记录，禁用启用态）
-- 幂等：INSERT IGNORE / UPDATE WHERE 双向保护
-- ============================================================================

USE onepl;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 阶段 A：补 perf 模块 20 条 PT_RESOURCE（写操作高危必须有授权链）
-- SYS_CODE='PERF' 与现有 38 条 perf 资源对齐
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  -- DataTaskController（任务状态查询）
  ('P_PERF_DATA_TASK_ST', '/api/data-task/status',                'POST',   '任务状态查询',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- AllocAdjustController（分配调整 4 条）
  ('P_PERF_ALLOC_AD_CRE', '/api/perf/alloc-adjust/create',        'POST',   '创建分配调整申请',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_ALLOC_AD_LST', '/api/perf/alloc-adjust/list',          'GET',    '分配调整申请列表',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_ALLOC_AD_GET', '/api/perf/alloc-adjust/*',             'GET',    '分配调整申请详情',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_ALLOC_AD_WD',  '/api/perf/alloc-adjust/*/withdraw',    'POST',   '撤回分配调整申请',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- TargetAdjustController（目标调整 4 条）
  ('P_PERF_TGT_AD_CRE',   '/api/perf/target-adjust/create',       'POST',   '创建目标调整申请',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_TGT_AD_LST',   '/api/perf/target-adjust/list',         'GET',    '目标调整申请列表',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_TGT_AD_GET',   '/api/perf/target-adjust/*',            'GET',    '目标调整申请详情',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_TGT_AD_WD',    '/api/perf/target-adjust/*/withdraw',   'POST',   '撤回目标调整申请',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- PerfImportController（导入 5 条）
  ('P_PERF_IMP_UPLOAD',   '/api/perf/import/upload',              'POST',   '导入文件上传',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_IMP_BTC_GET',  '/api/perf/import/batches/*',           'GET',    '导入批次详情',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_IMP_BTC_DEL',  '/api/perf/import/batches/*',           'DELETE', '删除导入批次',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_IMP_BTC_ERR',  '/api/perf/import/batches/*/errors',    'GET',    '导入批次错误清单',      0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_IMP_BTC_RTY',  '/api/perf/import/batches/*/retry',     'POST',   '重试导入批次',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- PerfExportController（导出 2 条）
  ('P_PERF_EXP_ALLOC',    '/api/perf/export/alloc',               'POST',   '导出绩效分配',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_EXP_KPI',      '/api/perf/export/kpi',                 'POST',   '导出 KPI 结果',         0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- MetricDefController（指标手动执行 2 条）
  ('P_PERF_MTR_EXEC',     '/api/perf/metrics/*/execute',          'POST',   '指标立即执行',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_MTR_TRIAL',    '/api/perf/metrics/*/trial-run',        'POST',   '指标试运行',            0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  -- PerfCalcController + SysControlController
  ('P_PERF_RECALC',       '/api/perf/recalc',                     'POST',   '历史重算',              0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('P_PERF_SYS_RB',       '/api/perf/sys-control/rollback',       'POST',   '系统控制回滚',          0, 0, 'PERF', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐');

-- ----------------------------------------------------------------------------
-- 阶段 B：补 portal 模块 6 条 PT_RESOURCE
-- SYS_CODE='PLATFORM' 与现有 60 条 RES_* 资源对齐
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE, CREATE_USER, UPDATE_USER, REMARK)
VALUES
  ('RES_SHORTCUT_LIST',   '/api/portal/shortcuts',                'GET',    '快捷入口列表',          0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('RES_PRODUCT_LIST',    '/api/products',                        'GET',    '产品列表',              0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('RES_PRODUCT_CREATE',  '/api/products',                        'POST',   '产品新增',              0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('RES_PRODUCT_DETAIL',  '/api/products/*',                      'GET',    '产品详情',              0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('RES_PRODUCT_UPDATE',  '/api/products/*',                      'PUT',    '产品编辑',              0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐'),
  ('RES_PRODUCT_DELETE',  '/api/products/*',                      'DELETE', '产品删除',              0, 0, 'PLATFORM', 'align-2026-04-25', 'align-2026-04-25', 'V1.1 PT 资源对账补齐');

-- ----------------------------------------------------------------------------
-- 阶段 C：标记 11 条 customer 孤儿 STATUS=1（已下线，保留审计记录但禁用启用态）
-- 这些 RESOURCE_ID 在 PT_RESOURCE 表里存在但代码已无对应 controller，按
-- reviewer 建议保留行 + STATUS=1 而非 DELETE（保留 audit 历史）
-- ----------------------------------------------------------------------------
UPDATE PT_RESOURCE
SET STATUS = 1,
    UPDATE_USER = 'align-2026-04-25',
    UPDATE_TIME = NOW(),
    REMARK = CONCAT(IFNULL(REMARK, ''), ' [V1.1 已下线: 代码无对应 controller, 2026-04-25 audit 标记孤儿]')
WHERE RESOURCE_ID IN (
  'RES_CUST_CLAIM_RETOU',     -- /api/claims/*/re-touch POST
  'RES_CUST_POOL_CLAIM',      -- /api/customer-pool/*/claim POST
  'RES_CUST_CUST_UPDATE',     -- /api/customers/* PUT
  'RES_CUSTOMER_EDIT',        -- /api/customers/*/edit POST
  'RES_CUST_CUST_TRANS',      -- /api/customers/*/transfer POST
  'RES_CUST_LD_IMP_BATC',     -- /api/leads/import/batches GET
  'RES_CUST_LD_IMP_BTCH',     -- /api/leads/import/batches/* GET
  'RES_CUST_LEAD_EDIT',       -- /api/leads/*/edit POST
  'RES_CUST_CLAIM_LST',       -- /api/my-claims GET
  'RES_CUST_TAG_READ',        -- /api/tags/* GET
  'RES_CUST_TRPT_SUM'         -- /api/touch-reports/summary GET
);

-- ----------------------------------------------------------------------------
-- 验证 SQL（脚本结尾用，对账）
-- ----------------------------------------------------------------------------
SELECT 'perf_added' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE
  WHERE RESOURCE_ID LIKE 'P_PERF_DATA%' OR RESOURCE_ID LIKE 'P_PERF_ALLOC_AD%'
     OR RESOURCE_ID LIKE 'P_PERF_TGT_AD%' OR RESOURCE_ID LIKE 'P_PERF_IMP_%'
     OR RESOURCE_ID LIKE 'P_PERF_EXP_%' OR RESOURCE_ID LIKE 'P_PERF_MTR_%'
     OR RESOURCE_ID = 'P_PERF_RECALC' OR RESOURCE_ID = 'P_PERF_SYS_RB';
-- 期望 20

SELECT 'portal_added' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE
  WHERE RESOURCE_ID IN ('RES_SHORTCUT_LIST','RES_PRODUCT_LIST','RES_PRODUCT_CREATE','RES_PRODUCT_DETAIL','RES_PRODUCT_UPDATE','RES_PRODUCT_DELETE');
-- 期望 6

SELECT 'cust_disabled' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE
  WHERE STATUS = 1 AND RESOURCE_ID IN (
    'RES_CUST_CLAIM_RETOU','RES_CUST_POOL_CLAIM','RES_CUST_CUST_UPDATE','RES_CUSTOMER_EDIT','RES_CUST_CUST_TRANS',
    'RES_CUST_LD_IMP_BATC','RES_CUST_LD_IMP_BTCH','RES_CUST_LEAD_EDIT','RES_CUST_CLAIM_LST','RES_CUST_TAG_READ','RES_CUST_TRPT_SUM'
  );
-- 期望 11

SELECT 'TOTAL' AS scope, COUNT(*) AS cnt FROM PT_RESOURCE;
-- 期望 272 + 26 = 298
