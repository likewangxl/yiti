-- ============================================================
-- 业绩调整申请演示用户三角色种子（2026-05-20）
-- ============================================================
-- 目标：为 perf_alloc_adjust_{corp,retail}_v1 BPMN 提供完整三角色演示链路：
--   申请人(支行 R_RM) → 一级审批(支行 R_BRANCH_MGR) → 二级审批(分行 R_PRESIDENT)
--
-- 现状（yiti 库）：
--   - 全部 15 个用户挂在 10002741 西安分行-营业部（level 3 支行）
--   - WF_NODE_CANDIDATE_CONF 两个 BPMN 的两步候选人都是 admin
--   - 无法演示"客户经理→支行长→分行长"链路
--
-- 本脚本：
--   1) 新增 3 个专用演示用户（密码统一 123456）
--   2) 绑定 3 个用户到不同机构（支行 vs 分行）
--   3) 绑定角色（R_RM / R_BRANCH_MGR / R_PRESIDENT）
--   4) 更新 WF_NODE_CANDIDATE_CONF：corp/retail 两个 BPMN
--      - branch_mgr_review (一级)  → PERF_L1_BRANCH（支行长）
--      - hq_mgr_review    (二级)  → PERF_L2_HQ（分行长）
--
-- 适用：yiti（dev 运行库）。onepl 库缺 02900000/02901000 两个机构，
--   需先完成 fresh-deploy 机构种子才能套用（本脚本暂不动 onepl）。
-- ============================================================

-- 1) 新增 3 个演示用户（密码 hash 来自 docs/.../2026-04-10-pt-align-and-test-seed.sql 同款 123456）
INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, ISENABLED, ISLOCKED, PASS_WRONG_COUNT, CREATE_AUTHOR, REMARK)
VALUES
  ('PERF_APPLY',     'perf_apply',     '业绩调整-申请人',     '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed-2026-05-20', '业绩调整 demo 申请人(支行客户经理)'),
  ('PERF_L1_BRANCH', 'perf_l1_branch', '业绩调整-一级审批',   '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed-2026-05-20', '业绩调整 demo 一级审批(支行长)'),
  ('PERF_L2_HQ',     'perf_l2_hq',     '业绩调整-二级审批',   '$2a$10$nURd20BPbYGR7t1zaKF4We6yuGFQn6Ck3jW4IcgEU2HHCSd1NO/Iy', 0, 0, 0, 'seed-2026-05-20', '业绩调整 demo 二级审批(分行长)')
ON DUPLICATE KEY UPDATE
  USERCHNNAME=VALUES(USERCHNNAME),
  PWD=VALUES(PWD),
  ISENABLED=0,
  ISLOCKED=0,
  PASS_WRONG_COUNT=0,
  UPDATE_AUTHOR='seed-2026-05-20';

-- 2) 绑定机构（支行/分行 立体）
--    PERF_APPLY     → 02901000 西安西稍门支行（level 3 支行）
--    PERF_L1_BRANCH → 02901000 同上支行
--    PERF_L2_HQ     → 02900000 西安分行（level 2 分行）
INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
  ('PERF_APPLY',     '02901000'),
  ('PERF_L1_BRANCH', '02901000'),
  ('PERF_L2_HQ',     '02900000')
ON DUPLICATE KEY UPDATE ORG_CODE=VALUES(ORG_CODE);

-- 3) 绑定角色
INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID) VALUES
  ('PERF_APPLY',     'R_RM'),          -- 客户经理(申请人)
  ('PERF_L1_BRANCH', 'R_BRANCH_MGR'),  -- 经营机构负责人(支行长 = 一级审批)
  ('PERF_L2_HQ',     'R_PRESIDENT')    -- 分行行长(二级审批)
ON DUPLICATE KEY UPDATE ROLE_ID=VALUES(ROLE_ID);

-- 4) 更新 BPMN 候选人（对公 + 零售各两个节点）
--    candidate_type=USER, candidate_value=JSON 数组
UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["PERF_L1_BRANCH"]'
 WHERE process_definition_key='perf_alloc_adjust_corp_v1' AND node_key='branch_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["PERF_L2_HQ"]'
 WHERE process_definition_key='perf_alloc_adjust_corp_v1' AND node_key='hq_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["PERF_L1_BRANCH"]'
 WHERE process_definition_key='perf_alloc_adjust_retail_v1' AND node_key='branch_mgr_review';

UPDATE WF_NODE_CANDIDATE_CONF
   SET candidate_value='["PERF_L2_HQ"]'
 WHERE process_definition_key='perf_alloc_adjust_retail_v1' AND node_key='hq_mgr_review';

-- 5) 验证
SELECT '== users ==' AS section;
SELECT u.USER_ID, u.USERNAME, u.USERCHNNAME, uo.ORG_CODE, o.ORG_NAME, GROUP_CONCAT(r.ROLE_CODE) AS roles
FROM PT_USER u
LEFT JOIN EXT_USER_ORG uo ON uo.USER_ID=u.USER_ID
LEFT JOIN EXT_ORG_INFO o  ON o.ORG_CODE =uo.ORG_CODE
LEFT JOIN PT_USER_ROLE ur ON ur.USER_ID =u.USER_ID
LEFT JOIN PT_ROLE r       ON r.ROLE_ID  =ur.ROLE_ID
WHERE u.USER_ID IN ('PERF_APPLY','PERF_L1_BRANCH','PERF_L2_HQ')
GROUP BY u.USER_ID;

SELECT '== candidate config ==' AS section;
SELECT process_definition_key, node_key, candidate_type, candidate_value
FROM WF_NODE_CANDIDATE_CONF
WHERE process_definition_key LIKE 'perf_alloc_adjust%'
ORDER BY process_definition_key, node_key;
