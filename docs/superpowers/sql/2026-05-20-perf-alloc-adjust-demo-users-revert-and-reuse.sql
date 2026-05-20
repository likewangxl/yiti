-- ============================================================
-- 业绩调整演示用户改用 PT_USER 现有账号（2026-05-20 二次调整）
-- ============================================================
-- 替代 2026-05-20-perf-alloc-adjust-demo-users.sql：
--   1) 删除上一脚本新增的 3 个专用账号（PERF_APPLY / PERF_L1_BRANCH / PERF_L2_HQ）
--   2) WF_NODE_CANDIDATE_CONF 候选人改回 PT_USER 已有账号：
--      - 一级审批(branch_mgr_review) → E20001 (branch_wang, BRANCH_HEA 经营机构负责人)
--      - 二级审批(hq_mgr_review)     → user002 (李四2,    BRANCH_PRE 分行行长)
--   3) 申请人沿用 E10001 (rm_zhang, R_RM 客户经理) — 仅作"建议登录账号"，无需 DB 配置
--
-- 现有用户均在 10002741 西安分行-营业部；本脚本不再立体化机构。
-- 适用：yiti（dev 运行库）。
-- ============================================================

-- 1) 删除上次新增账号（按依赖反向）
DELETE FROM PT_USER_ROLE WHERE USER_ID IN ('PERF_APPLY','PERF_L1_BRANCH','PERF_L2_HQ');
DELETE FROM EXT_USER_ORG WHERE USER_ID IN ('PERF_APPLY','PERF_L1_BRANCH','PERF_L2_HQ');
DELETE FROM PT_USER      WHERE USER_ID IN ('PERF_APPLY','PERF_L1_BRANCH','PERF_L2_HQ');

-- 2) 候选人改回 PT_USER 现有账号
UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["E20001"]'
 WHERE process_definition_key='perf_alloc_adjust_corp_v1' AND node_key='branch_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["user002"]'
 WHERE process_definition_key='perf_alloc_adjust_corp_v1' AND node_key='hq_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["E20001"]'
 WHERE process_definition_key='perf_alloc_adjust_retail_v1' AND node_key='branch_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["user002"]'
 WHERE process_definition_key='perf_alloc_adjust_retail_v1' AND node_key='hq_mgr_review';
