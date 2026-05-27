-- =========================================================
-- 2026-05-20 角色补齐 + ROLE_CODE 对齐脚本
-- =========================================================
-- 背景：
--   alloc_adjust_approve_v1 流程的 6 个节点（branch_approve / biz_dept_review /
--   original_owner_approve / biz_dept_leader_approve / finance_review /
--   finance_leader_approve）的 WF_NODE_CANDIDATE_CONF.candidate_value 引用了
--   PT_ROLE.ROLE_CODE，但存在两类不一致：
--   (1) 业务部门"经办人"与"负责人"用同一角色（CORP_DEPT / RETAIL_DEPT / BACK_FINANCE），
--       导致 BPMN 分级审批失效；
--   (2) PT_ROLE 现有 3 行 ROLE_CODE 历史上被截到 10 字符（BRANCH_HEA / RETAIL_DEP /
--       BACK_FINAN），但 candidate_value 写的是完整名（BRANCH_HEAD / RETAIL_DEPT /
--       BACK_FINANCE），candidate 解析永远拿不到角色；
--   (3) original_owner_approve / touch_execute 引用了 CUST_MANAGER，但 PT_ROLE 中
--       客户经理角色的 ROLE_CODE 实际是 R_RM。
--
-- 处置（对齐基线 seed-v1.sql + workflow-seed-v1.sql 2026-05-20 改动）：
--   A. 新增 3 个业务部门负责人角色：R_CORP_LEAD / R_FIN_LEAD / R_RETAIL_LEAD
--   B. 修正 3 行历史短编码：BRANCH_HEA→BRANCH_HEAD / RETAIL_DEP→RETAIL_DEPT /
--      BACK_FINAN→BACK_FINANCE
--   C. 修正 5 处 candidate_value：
--      - touch_process_v1.touch_execute              CUST_MANAGER → R_RM
--      - alloc_adjust_approve_v1.original_owner_approve CUST_MANAGER → R_RM
--      - alloc_adjust_approve_v1.biz_dept_leader_approve
--          [CORP_DEPT,RETAIL_DEPT] → [CORP_DEPT_LEADER,RETAIL_DEPT_LEADER]
--      - alloc_adjust_approve_v1.finance_leader_approve BACK_FINANCE → FINANCE_LEADER
--      - target_adjust_approve_v1.finance_leader_approve BACK_FINANCE → FINANCE_LEADER
--
-- 部署范围（双库执行）：
--   - onepl       生产/文档基线库
--   - yiti        本地 dev profile 实际连接的库（见用户记忆 reference_mysql_credentials）
--   两库都要跑，否则 dev 和文档基线不一致。
--
-- 安全：
--   - 所有 INSERT 使用 INSERT IGNORE 幂等
--   - 所有 UPDATE 带精确 WHERE，可重复执行（短编码已对齐到长编码后再次执行 WHERE 命中 0 行）
--   - 不动 ROLE_ID（主键），不动 PT_USER_ROLE / PT_ROLE_RESOURCE / PT_ROLE_BIZ_SCOPE
--     等外键引用（这些表都按 ROLE_ID 关联，不依赖 ROLE_CODE）
--
-- 执行前必跑备份：
--   mysqldump -uroot -p<password> onepl PT_ROLE WF_NODE_CANDIDATE_CONF \
--     > docs/superpowers/sql/backup/2026-05-20-pt_role-wf_node-pre-align.sql
--   mysqldump -uroot -p<password> yiti  PT_ROLE WF_NODE_CANDIDATE_CONF \
--     >> docs/superpowers/sql/backup/2026-05-20-pt_role-wf_node-pre-align.sql
-- =========================================================

-- ---------------------------------------------------------
-- A. 新增 3 个业务部门负责人角色
-- ---------------------------------------------------------
INSERT IGNORE INTO PT_ROLE (`ROLE_ID`, `ROLE_CODE`, `ROLE_CHNAME`, `RECORD_STATUS`, `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`)
VALUES ('R_CORP_LEAD', 'CORP_DEPT_LEADER', '公司部负责人', 0, 'PLATFORM',
        '2026-05-20 00:00:00', 'seed', '2026-05-20 00:00:00', 'seed',
        'V1 seed - 公司部负责人，用于 alloc_adjust_approve_v1 biz_dept_leader_approve 节点');

INSERT IGNORE INTO PT_ROLE (`ROLE_ID`, `ROLE_CODE`, `ROLE_CHNAME`, `RECORD_STATUS`, `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`)
VALUES ('R_FIN_LEAD', 'FINANCE_LEADER', '资财部负责人', 0, 'PLATFORM',
        '2026-05-20 00:00:00', 'seed', '2026-05-20 00:00:00', 'seed',
        'V1 seed - 资财部负责人，用于 alloc_adjust_approve_v1 finance_leader_approve 节点');

INSERT IGNORE INTO PT_ROLE (`ROLE_ID`, `ROLE_CODE`, `ROLE_CHNAME`, `RECORD_STATUS`, `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`)
VALUES ('R_RETAIL_LEAD', 'RETAIL_DEPT_LEADER', '零售部负责人', 0, 'PLATFORM',
        '2026-05-20 00:00:00', 'seed', '2026-05-20 00:00:00', 'seed',
        'V1 seed - 零售部负责人，用于 alloc_adjust_approve_v1 biz_dept_leader_approve 节点');

-- ---------------------------------------------------------
-- B. 3 行 ROLE_CODE 历史截短修正
-- ---------------------------------------------------------
UPDATE PT_ROLE
SET ROLE_CODE = 'BRANCH_HEAD',
    UPDATE_TIME = '2026-05-20 00:00:00',
    UPDATE_USER = 'seed',
    REMARK = CONCAT(IFNULL(REMARK, ''), '（2026-05-20 ROLE_CODE BRANCH_HEA→BRANCH_HEAD 对齐 candidateValue）')
WHERE ROLE_ID = 'R_BRANCH_MGR' AND ROLE_CODE = 'BRANCH_HEA';

UPDATE PT_ROLE
SET ROLE_CODE = 'RETAIL_DEPT',
    UPDATE_TIME = '2026-05-20 00:00:00',
    UPDATE_USER = 'seed',
    REMARK = CONCAT(IFNULL(REMARK, ''), '（2026-05-20 ROLE_CODE RETAIL_DEP→RETAIL_DEPT 对齐 candidateValue）')
WHERE ROLE_ID = 'R_RETAIL_DEPT' AND ROLE_CODE = 'RETAIL_DEP';

UPDATE PT_ROLE
SET ROLE_CODE = 'BACK_FINANCE',
    UPDATE_TIME = '2026-05-20 00:00:00',
    UPDATE_USER = 'seed',
    REMARK = CONCAT(IFNULL(REMARK, ''), '（2026-05-20 ROLE_CODE BACK_FINAN→BACK_FINANCE 对齐 candidateValue）')
WHERE ROLE_ID = 'R_BACK_FINANCE' AND ROLE_CODE = 'BACK_FINAN';

-- ---------------------------------------------------------
-- C. WF_NODE_CANDIDATE_CONF 候选编码对齐（5 处）
-- ---------------------------------------------------------
-- C.1 touch_process_v1.touch_execute  CUST_MANAGER → R_RM
UPDATE WF_NODE_CANDIDATE_CONF
SET candidate_value = '["R_RM"]',
    updated_time = CURRENT_TIMESTAMP
WHERE id = 'WNC_TOUCH_V1_RM_ROLE';

-- C.2 alloc_adjust_approve_v1.original_owner_approve  CUST_MANAGER → R_RM
UPDATE WF_NODE_CANDIDATE_CONF
SET candidate_value = '["R_RM"]',
    updated_time = CURRENT_TIMESTAMP
WHERE id = 'WNC_ALLOC_ORIG';

-- C.3 alloc_adjust_approve_v1.biz_dept_leader_approve
--     [CORP_DEPT, RETAIL_DEPT] → [CORP_DEPT_LEADER, RETAIL_DEPT_LEADER]
UPDATE WF_NODE_CANDIDATE_CONF
SET candidate_value = '["CORP_DEPT_LEADER","RETAIL_DEPT_LEADER"]',
    updated_time = CURRENT_TIMESTAMP
WHERE id = 'WNC_ALLOC_BIZ_LDR';

-- C.4 alloc_adjust_approve_v1.finance_leader_approve  BACK_FINANCE → FINANCE_LEADER
UPDATE WF_NODE_CANDIDATE_CONF
SET candidate_value = '["FINANCE_LEADER"]',
    updated_time = CURRENT_TIMESTAMP
WHERE id = 'WNC_ALLOC_FIN_LDR';

-- C.5 target_adjust_approve_v1.finance_leader_approve  BACK_FINANCE → FINANCE_LEADER
UPDATE WF_NODE_CANDIDATE_CONF
SET candidate_value = '["FINANCE_LEADER"]',
    updated_time = CURRENT_TIMESTAMP
WHERE id = 'WNC_TGT_ADJ_FL';

-- =========================================================
-- 执行后验证 SQL（应与下方期望结果一致）
-- =========================================================
-- 1) PT_ROLE 行数应为 16（原 13 + 新增 3）
--    SELECT COUNT(*) FROM PT_ROLE;  -- 期望 16
--
-- 2) 3 个新角色都在
--    SELECT ROLE_ID, ROLE_CODE, ROLE_CHNAME FROM PT_ROLE
--    WHERE ROLE_ID IN ('R_CORP_LEAD','R_FIN_LEAD','R_RETAIL_LEAD');
--    -- 期望 3 行
--
-- 3) 短编码全部修正
--    SELECT ROLE_ID, ROLE_CODE FROM PT_ROLE
--    WHERE ROLE_CODE IN ('BRANCH_HEA','RETAIL_DEP','BACK_FINAN');
--    -- 期望 0 行
--
-- 4) candidate_value 已清零所有 CUST_MANAGER
--    SELECT id, candidate_value FROM WF_NODE_CANDIDATE_CONF
--    WHERE candidate_value LIKE '%CUST_MANAGER%';
--    -- 期望 0 行
--
-- 5) 5 个目标节点候选已对齐
--    SELECT id, candidate_value FROM WF_NODE_CANDIDATE_CONF
--    WHERE id IN ('WNC_TOUCH_V1_RM_ROLE','WNC_ALLOC_ORIG','WNC_ALLOC_BIZ_LDR',
--                 'WNC_ALLOC_FIN_LDR','WNC_TGT_ADJ_FL');
--    -- 期望 5 行，candidate_value 与 workflow-seed-v1.sql 2026-05-20 版本一致
-- =========================================================
