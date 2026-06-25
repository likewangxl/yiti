-- ============================================================
-- 2026-06-25 注册待处理任务「批量提交打分」接口到 PT_RESOURCE
-- 接口：POST /api/eval/pending-tasks/submit-batch
--   Controller: EvalPendingController.submitBatch
--   @BizAuth(bizType = EVAL, action = WRITE)（与单条 submit 同口径）
-- 资源 ID：PERF_EVAL_38（紧接现网最大 PERF_EVAL_37）
-- 授权：复制单条提交 PERF_EVAL_27 的全部角色绑定（能逐条提交者即可批量提交）
-- 幂等：INSERT IGNORE + 角色绑定 NOT EXISTS 防重，可重复执行
-- ============================================================

-- ---------- 1. PT_RESOURCE 资源登记 ----------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS) VALUES
('PERF_EVAL_38', '/api/eval/pending-tasks/submit-batch', 'POST', '批量提交待处理任务打分', 0, 0);

-- ---------- 2. 角色绑定：与单条提交 PERF_EVAL_27 保持一致 ----------
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT REPLACE(UUID(), '-', ''), t.ROLE_ID, 'PERF_EVAL_38', t.SYS_CODE
FROM (
    SELECT DISTINCT ROLE_ID, SYS_CODE
    FROM PT_ROLE_RESOURCE
    WHERE RESOURCE_ID = 'PERF_EVAL_27'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE x
    WHERE x.ROLE_ID = t.ROLE_ID AND x.RESOURCE_ID = 'PERF_EVAL_38'
);
