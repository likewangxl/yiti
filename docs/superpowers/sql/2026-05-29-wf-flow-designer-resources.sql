-- ============================================================
-- 审批流程设计器 API 资源登记 + 角色绑定
-- Task 10：FlowDesignController 7 端点
-- 执行库：yiti（开发库）+ onepl（生产库）
-- 幂等：INSERT IGNORE（按主键 RESOURCE_ID）
-- 角色绑定：与现有 /api/admin/workflow/* 资源保持一致
--           绑定 PT_ROLE_RESOURCE 中已有 W_NC_LIST 的全部 20 个角色
-- ============================================================

-- ── 1. 资源登记 ─────────────────────────────────────────────

INSERT IGNORE INTO PT_RESOURCE
    (RESOURCE_ID, MENU_NAME, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE, STATUS)
VALUES
    -- GET /api/admin/workflow/flows  查询流程定义列表
    ('W_FLOW_LIST',  '查询流程定义列表',   '/api/admin/workflow/flows',               'GET',    'WF', 0),
    -- GET /api/admin/workflow/flows/{id}  获取流程图
    ('W_FLOW_GET',   '获取流程图',         '/api/admin/workflow/flows/*',             'GET',    'WF', 0),
    -- POST /api/admin/workflow/flows  新建流程定义草稿
    ('W_FLOW_ADD',   '新建流程定义草稿',   '/api/admin/workflow/flows',               'POST',   'WF', 0),
    -- PUT /api/admin/workflow/flows/{id}  整图替换保存
    ('W_FLOW_UPD',   '保存流程图',         '/api/admin/workflow/flows/*',             'PUT',    'WF', 0),
    -- POST /api/admin/workflow/flows/{id}/publish  发布流程定义
    ('W_FLOW_PUB',   '发布流程定义',       '/api/admin/workflow/flows/*/publish',     'POST',   'WF', 0),
    -- DELETE /api/admin/workflow/flows/{id}  删除草稿
    ('W_FLOW_DEL',   '删除草稿流程定义',   '/api/admin/workflow/flows/*',             'DELETE', 'WF', 0),
    -- GET /api/admin/workflow/flows/meta/variables  查询流程变量目录
    ('W_FLOW_VARS',  '查询流程变量目录',   '/api/admin/workflow/flows/meta/variables','GET',    'WF', 0);

-- ── 2. 角色绑定 ─────────────────────────────────────────────
-- 绑定与现有 W_NC_LIST 相同的 20 个角色，保持权限对齐

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, res.RESOURCE_ID, 'WF'
FROM
    (SELECT 'R_10552DF9' AS ROLE_ID UNION ALL
     SELECT 'R_129EE164'            UNION ALL
     SELECT 'R_ADMIN'               UNION ALL
     SELECT 'R_BACK_FINANCE'        UNION ALL
     SELECT 'R_BACK_TECH'           UNION ALL
     SELECT 'R_BRANCH_EMP'          UNION ALL
     SELECT 'R_BRANCH_MGR'          UNION ALL
     SELECT 'R_CORP_DEPT'           UNION ALL
     SELECT 'R_CORP_LEAD'           UNION ALL
     SELECT 'R_CREDIT_APPROVER'     UNION ALL
     SELECT 'R_CREDIT_REVIEWER'     UNION ALL
     SELECT 'R_DATA_REPORT'         UNION ALL
     SELECT 'R_FIN_LEAD'            UNION ALL
     SELECT 'R_PRESIDENT'           UNION ALL
     SELECT 'R_RETAIL_DEPT'         UNION ALL
     SELECT 'R_RETAIL_LEAD'         UNION ALL
     SELECT 'R_RM'                  UNION ALL
     SELECT 'R_SUB_EMP'             UNION ALL
     SELECT 'R_SUPPORT_SEC'         UNION ALL
     SELECT 'R_SUPPORT_STAFF') r
CROSS JOIN
    (SELECT 'W_FLOW_LIST' AS RESOURCE_ID UNION ALL
     SELECT 'W_FLOW_GET'                 UNION ALL
     SELECT 'W_FLOW_ADD'                 UNION ALL
     SELECT 'W_FLOW_UPD'                 UNION ALL
     SELECT 'W_FLOW_PUB'                 UNION ALL
     SELECT 'W_FLOW_DEL'                 UNION ALL
     SELECT 'W_FLOW_VARS') res
-- 幂等：跳过已存在的 ROLE_ID + RESOURCE_ID 组合
WHERE NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE x
    WHERE x.ROLE_ID = r.ROLE_ID AND x.RESOURCE_ID = res.RESOURCE_ID
);

-- ── 验证 ────────────────────────────────────────────────────
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
FROM PT_RESOURCE
WHERE RESOURCE_ID IN ('W_FLOW_LIST','W_FLOW_GET','W_FLOW_ADD',
                      'W_FLOW_UPD','W_FLOW_PUB','W_FLOW_DEL','W_FLOW_VARS')
ORDER BY RESOURCE_ID;

SELECT COUNT(*) AS bound_rows
FROM PT_ROLE_RESOURCE
WHERE RESOURCE_ID IN ('W_FLOW_LIST','W_FLOW_GET','W_FLOW_ADD',
                      'W_FLOW_UPD','W_FLOW_PUB','W_FLOW_DEL','W_FLOW_VARS');

-- ============================================================
-- P3 Task 2：导入现有流程端点资源登记
-- 端点：POST /api/admin/workflow/flows/import-existing
-- 执行库：yiti（开发库）+ onepl（生产库）
-- 幂等：PT_RESOURCE 用 INSERT IGNORE；角色绑定用 NOT EXISTS
-- 角色：与 W_FLOW_LIST 完全对齐（同一批 20 个角色）
-- ============================================================

-- ── 3. 资源登记（1 条）───────────────────────────────────────

INSERT IGNORE INTO PT_RESOURCE
    (RESOURCE_ID, MENU_NAME, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE, STATUS)
VALUES
    ('W_FLOW_IMP', '审批流程-导入现有', '/api/admin/workflow/flows/import-existing', 'POST', 'WF', 0);

-- ── 4. 角色绑定：复制 W_FLOW_LIST 的全部角色绑定到 W_FLOW_IMP（幂等）───

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, 'W_FLOW_IMP', 'WF'
FROM PT_ROLE_RESOURCE rr
WHERE rr.RESOURCE_ID = 'W_FLOW_LIST'
  AND NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE x
    WHERE x.ROLE_ID = rr.ROLE_ID AND x.RESOURCE_ID = 'W_FLOW_IMP'
  );

-- ── 验证 P3 Task 2 ───────────────────────────────────────────
-- 预期：W_FLOW_IMP 资源 1 条
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
FROM PT_RESOURCE
WHERE RESOURCE_ID = 'W_FLOW_IMP';

-- 预期：绑定角色数 = W_FLOW_LIST 的绑定数
SELECT
    (SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'W_FLOW_IMP')  AS imp_bindings,
    (SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'W_FLOW_LIST') AS list_bindings;
