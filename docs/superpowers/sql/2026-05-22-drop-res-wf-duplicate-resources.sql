-- =========================================================
-- 2026-05-22 清理 PT_RESOURCE 中 workflow tasks 接口的重复登记
-- =========================================================
-- 背景：
--   /api/workflow/tasks(*) 7 个端点在 PT_RESOURCE 同时存在两套：
--     - W_TASK_*   (SYS_CODE='WF',       2026-04-10 align 脚本登记)
--     - RES_WF_*   (SYS_CODE='PLATFORM', 2026-04-25 重复登记)
--   两套 URL/METHOD 完全相同，仅 SYS_CODE 不同，因 PT_RESOURCE 唯一键
--   uk_pt_resource_url_method_sys 含 SYS_CODE 而双双幸存。
--
--   ResourceMatcher.match() 走 cacheService.getAllResources().stream().findFirst()，
--   而 PtResourceMapper.selectAll ORDER BY MENU_RANK_NO ASC，两套 MENU_RANK_NO 均为 0，
--   命中谁纯靠 MySQL 默认聚簇序（按 PK 字典序）：RES_WF_* < W_TASK_*。
--
--   后果：RES_WF_* 这套**未授权给 R_BACK_FINANCE / R_BACK_TECH / R_RETAIL_DEPT /
--   R_SUPPORT_SEC / R_SUPPORT_STAFF** 5 个角色，导致这些角色的用户访问 workflow
--   接口被拦截返回 AUTH-40301 无接口访问权限。
--
--   典型现象：finance_zhou（R_BACK_FINANCE）作为 finance_review 节点候选人，
--   corp_leader01 审批通过后流程已正确流转，但 finance_zhou 待办列表为空 —— 不是
--   工作流问题，是 RBAC 拦截器误命中 RES_WF_TODO 把请求挡掉了。
--
--   清理思路：保留 W_TASK_*（SYS_CODE='WF'，授权面广且语义贴合 workflow-center
--   模块），删除 RES_WF_* 这一套连同 PT_ROLE_RESOURCE 关联授权。
--
-- 部署范围：yiti + onepl 双库
--
-- 执行前备份：
--   docs/superpowers/sql/backup/2026-05-22-pre-res-wf-cleanup-yiti.sql
--   docs/superpowers/sql/backup/2026-05-22-pre-res-wf-cleanup-onepl.sql
--
-- 幂等：DELETE 带 WHERE 限定，重复执行无副作用
-- =========================================================

-- A) 先清 PT_ROLE_RESOURCE 关联（外键不存在，但保证语义对齐）
DELETE FROM PT_ROLE_RESOURCE
 WHERE RESOURCE_ID IN (
   'RES_WF_TODO', 'RES_WF_DETAIL', 'RES_WF_APPROVE',
   'RES_WF_CLAIM', 'RES_WF_REJECT', 'RES_WF_TRANSFER', 'RES_WF_DONE'
 );

-- B) 再删 PT_RESOURCE 主记录
DELETE FROM PT_RESOURCE
 WHERE RESOURCE_ID IN (
   'RES_WF_TODO', 'RES_WF_DETAIL', 'RES_WF_APPROVE',
   'RES_WF_CLAIM', 'RES_WF_REJECT', 'RES_WF_TRANSFER', 'RES_WF_DONE'
 );

-- =========================================================
-- 验证
-- =========================================================
-- 1) RES_WF_* 应全部消失
--    SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'RES_WF_%';
--    -- 期望 0
--
-- 2) /api/workflow/tasks(*) 应只剩 W_TASK_* 7 行
--    SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, SYS_CODE
--      FROM PT_RESOURCE WHERE RESOURCE_URL LIKE '/api/workflow/tasks%'
--      ORDER BY RESOURCE_ID;
--    -- 期望 7 行 W_TASK_*
--
-- 3) PT_ROLE_RESOURCE 不应再含 RES_WF_* 记录
--    SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'RES_WF_%';
--    -- 期望 0
--
-- 4) finance_zhou (R_BACK_FINANCE) 仍持有 W_TASK_TODO 等 workflow 资源授权
--    SELECT res.RESOURCE_ID, res.RESOURCE_URL
--      FROM PT_ROLE_RESOURCE rr
--      JOIN PT_RESOURCE res ON rr.RESOURCE_ID=res.RESOURCE_ID
--     WHERE rr.ROLE_ID='R_BACK_FINANCE'
--       AND res.RESOURCE_URL LIKE '/api/workflow/tasks%';
--    -- 期望 7 行 W_TASK_*
-- =========================================================
