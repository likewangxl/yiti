-- ============================================================================
-- 业绩调整：三级机构负责人 -> 二级机构负责人逐级审批
--
-- 目标流程：FDEF_ALLOC_CORP / FDEF_ALLOC_RETAIL。
-- 前置：已执行 2026-08-27-alloc-original-owner-employee-and-leader-approval.sql，
--       两条设计器流程均已存在 original_owner_employee_approve 和
--       original_owner_approve 节点。
--       WF_FLOW_NODE.approve_mode 必须能完整容纳 GROUP_ALL，建议由 DBA 预先确认为
--       VARCHAR(16)；若仍为 VARCHAR(8)，本脚本会在插入新3级 GROUP_ALL 节点时失败。
--
-- 申请人链路：
--   2级员工：branch_approve_l2（L2） -> 公司部/零售部经办；
--   3级员工：branch_approve_l3（SELF） -> branch_approve_l2（L2）
--              -> 公司部/零售部经办。
--   NEW 维度仍保留 branch_approve_l2 之后的现有路由，本脚本不改其业务语义。
--
-- 原业绩人链路：
--   原业绩员工会签 -> 若 originalOwnerLevel3ApprovalRequired=YES，
--   先 original_owner_level3_approve（GROUP_ALL），再 original_owner_approve（GROUP_ALL，2级）；
--   若为 NO，直接进入 original_owner_approve。之后仍进入公司部/零售部负责人。
--
-- 本脚本仅修改设计器源模型，不执行发布。执行后必须分别重新发布
-- 对公、零售流程。在途实例继续按原已部署版本流转，不会被追溯改写。
-- ============================================================================

START TRANSACTION;

-- 机构最多3级：网关只精确接受 startOrgLevel=3，不再用 GE 3 覆盖更深层级。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_NODE gateway
  ON gateway.id = e.from_node_id
 AND gateway.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE level3_node
  ON level3_node.id = e.to_node_id
 AND level3_node.flow_def_id = e.flow_def_id
SET e.name = '3级机构（3级机构负责人审批）',
    e.condition_json = '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"3"}]}'
WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND gateway.node_key = 'gw_level'
  AND level3_node.node_key = 'branch_approve_l3';

-- 3级节点审本机构，2级节点统一解析到所属2级机构；2级发起时 L2 即本机构。
UPDATE WF_FLOW_NODE_APPROVER a
JOIN WF_FLOW_NODE n
  ON n.id = a.node_id
SET a.org_scope = 'SELF'
WHERE n.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND n.node_key = 'branch_approve_l3'
  AND a.approver_type = 'LEVEL_ROLE';

UPDATE WF_FLOW_NODE_APPROVER a
JOIN WF_FLOW_NODE n
  ON n.id = a.node_id
SET a.org_scope = 'L2'
WHERE n.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND n.node_key = 'branch_approve_l2'
  AND a.approver_type = 'LEVEL_ROLE';

UPDATE WF_FLOW_NODE
SET name = '3级机构负责人审批'
WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND node_key = 'branch_approve_l3';

UPDATE WF_FLOW_NODE
SET name = '2级机构负责人审批'
WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND node_key = 'branch_approve_l2';

-- 将原 branch_approve_l3 -> 条线经办主边改为先进 branch_approve_l2。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_NODE level3_node
  ON level3_node.id = e.from_node_id
 AND level3_node.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE biz_node
  ON biz_node.id = e.to_node_id
 AND biz_node.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE level2_node
  ON level2_node.flow_def_id = e.flow_def_id
 AND level2_node.node_key = 'branch_approve_l2'
SET e.to_node_id = level2_node.id,
    e.name = '2级机构负责人审批',
    e.is_default = 0,
    e.condition_json = NULL
WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND level3_node.node_key = 'branch_approve_l3'
  AND biz_node.node_key = 'biz_dept_review';

-- 历史对公模型曾有 branch_approve_l3 -> finance_review 的 NEW 直达边。
-- 由于交付 SQL 禁止 DELETE，将其改为在合法发起级别下永不命中的条件，
-- 确保3级发起人不能绕过2级机构负责人。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_NODE level3_node
  ON level3_node.id = e.from_node_id
 AND level3_node.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE finance_node
  ON finance_node.id = e.to_node_id
 AND finance_node.flow_def_id = e.flow_def_id
SET e.name = '历史直达边已停用',
    e.is_default = 0,
    e.condition_json = '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"0"}]}'
WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND level3_node.node_key = 'branch_approve_l3'
  AND finance_node.node_key = 'finance_review';

-- 首次新增3级原业绩机构节点前，为新增的两条边预留 sort_no。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_NODE employee_node
  ON employee_node.flow_def_id = e.flow_def_id
 AND employee_node.node_key = 'original_owner_employee_approve'
JOIN WF_FLOW_NODE level2_owner_node
  ON level2_owner_node.flow_def_id = e.flow_def_id
 AND level2_owner_node.node_key = 'original_owner_approve'
JOIN WF_FLOW_EDGE employee_to_level2
  ON employee_to_level2.flow_def_id = e.flow_def_id
 AND employee_to_level2.from_node_id = employee_node.id
 AND employee_to_level2.to_node_id = level2_owner_node.id
LEFT JOIN WF_FLOW_NODE level3_owner_node
  ON level3_owner_node.flow_def_id = e.flow_def_id
 AND level3_owner_node.node_key = 'original_owner_level3_approve'
SET e.sort_no = e.sort_no + 2
WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND level3_owner_node.id IS NULL
  AND e.sort_no > employee_to_level2.sort_no;

-- 将现有2级原业绩机构节点及后续节点顺延一位。
UPDATE WF_FLOW_NODE n
JOIN WF_FLOW_NODE level2_owner_node
  ON level2_owner_node.flow_def_id = n.flow_def_id
 AND level2_owner_node.node_key = 'original_owner_approve'
LEFT JOIN WF_FLOW_NODE level3_owner_node
  ON level3_owner_node.flow_def_id = n.flow_def_id
 AND level3_owner_node.node_key = 'original_owner_level3_approve'
SET n.sort_no = n.sort_no + 1
WHERE n.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND level3_owner_node.id IS NULL
  AND n.sort_no >= level2_owner_node.sort_no;

-- 对公：新增原业绩所属3级机构负责人分组审批节点。
INSERT INTO WF_FLOW_NODE
    (id, flow_def_id, node_key, node_type, name, approve_mode, sort_no, pos_x, pos_y)
SELECT 'NC_ORIG_OWNER_L3', d.id, 'original_owner_level3_approve', 'APPROVAL',
       '原业绩所属3级机构负责人审批', 'GROUP_ALL', level2_owner_node.sort_no - 1,
       GREATEST(COALESCE(level2_owner_node.pos_x, 180) - 180, 0), level2_owner_node.pos_y
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_NODE existing
        WHERE existing.flow_def_id = d.id
          AND existing.node_key = 'original_owner_level3_approve'
   );

-- 零售：新增原业绩所属3级机构负责人分组审批节点。
INSERT INTO WF_FLOW_NODE
    (id, flow_def_id, node_key, node_type, name, approve_mode, sort_no, pos_x, pos_y)
SELECT 'NR_ORIG_OWNER_L3', d.id, 'original_owner_level3_approve', 'APPROVAL',
       '原业绩所属3级机构负责人审批', 'GROUP_ALL', level2_owner_node.sort_no - 1,
       GREATEST(COALESCE(level2_owner_node.pos_x, 180) - 180, 0), level2_owner_node.pos_y
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_NODE existing
        WHERE existing.flow_def_id = d.id
          AND existing.node_key = 'original_owner_level3_approve'
   );

-- 新节点审批人使用3级机构分组快照。
INSERT INTO WF_FLOW_NODE_APPROVER
    (id, node_id, approver_type, approver_value, sort_no)
SELECT 'AC_ORIG_OWNER_L3', n.id, 'VAR', 'originalOwnerLevel3OrgApprovalGroups', 1
  FROM WF_FLOW_NODE n
 WHERE n.flow_def_id = 'FDEF_ALLOC_CORP'
   AND n.node_key = 'original_owner_level3_approve'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_NODE_APPROVER existing
        WHERE existing.node_id = n.id
          AND existing.approver_type = 'VAR'
          AND existing.approver_value = 'originalOwnerLevel3OrgApprovalGroups'
   );

INSERT INTO WF_FLOW_NODE_APPROVER
    (id, node_id, approver_type, approver_value, sort_no)
SELECT 'AR_ORIG_OWNER_L3', n.id, 'VAR', 'originalOwnerLevel3OrgApprovalGroups', 1
  FROM WF_FLOW_NODE n
 WHERE n.flow_def_id = 'FDEF_ALLOC_RETAIL'
   AND n.node_key = 'original_owner_level3_approve'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_NODE_APPROVER existing
        WHERE existing.node_id = n.id
          AND existing.approver_type = 'VAR'
          AND existing.approver_value = 'originalOwnerLevel3OrgApprovalGroups'
   );

-- 现有原业绩所属机构节点明确为2级机构。
UPDATE WF_FLOW_NODE
SET name = '原业绩所属2级机构负责人审批'
WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND node_key = 'original_owner_approve';

-- 原业绩员工会签 -> 2级机构原边：仅3级审批不需要时命中。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_NODE employee_node
  ON employee_node.id = e.from_node_id
 AND employee_node.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE level2_owner_node
  ON level2_owner_node.id = e.to_node_id
 AND level2_owner_node.flow_def_id = e.flow_def_id
SET e.name = '直接进入2级机构负责人审批',
    e.is_default = 0,
    e.condition_json = '{"logic":"AND","conditions":[{"field":"originalOwnerLevel3ApprovalRequired","op":"EQ","value":"NO"}]}'
WHERE e.flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND employee_node.node_key = 'original_owner_employee_approve'
  AND level2_owner_node.node_key = 'original_owner_approve';

-- 对公：原业绩员工会签 -> 3级机构负责人（YES）。
INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'EC_OWNER_EMP_L3', d.id, employee_node.id, level3_owner_node.id,
       '进入3级机构负责人审批', 0,
       '{"logic":"AND","conditions":[{"field":"originalOwnerLevel3ApprovalRequired","op":"EQ","value":"YES"}]}',
       employee_to_level2.sort_no + 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE employee_node
    ON employee_node.flow_def_id = d.id
   AND employee_node.node_key = 'original_owner_employee_approve'
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE employee_to_level2
    ON employee_to_level2.flow_def_id = d.id
   AND employee_to_level2.from_node_id = employee_node.id
   AND employee_to_level2.to_node_id = level2_owner_node.id
  JOIN WF_FLOW_NODE level3_owner_node
    ON level3_owner_node.flow_def_id = d.id
   AND level3_owner_node.node_key = 'original_owner_level3_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_EDGE existing
        WHERE existing.flow_def_id = d.id
          AND existing.from_node_id = employee_node.id
          AND existing.to_node_id = level3_owner_node.id
   );

-- 零售：原业绩员工会签 -> 3级机构负责人（YES）。
INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'ER_OWNER_EMP_L3', d.id, employee_node.id, level3_owner_node.id,
       '进入3级机构负责人审批', 0,
       '{"logic":"AND","conditions":[{"field":"originalOwnerLevel3ApprovalRequired","op":"EQ","value":"YES"}]}',
       employee_to_level2.sort_no + 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE employee_node
    ON employee_node.flow_def_id = d.id
   AND employee_node.node_key = 'original_owner_employee_approve'
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE employee_to_level2
    ON employee_to_level2.flow_def_id = d.id
   AND employee_to_level2.from_node_id = employee_node.id
   AND employee_to_level2.to_node_id = level2_owner_node.id
  JOIN WF_FLOW_NODE level3_owner_node
    ON level3_owner_node.flow_def_id = d.id
   AND level3_owner_node.node_key = 'original_owner_level3_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_EDGE existing
        WHERE existing.flow_def_id = d.id
          AND existing.from_node_id = employee_node.id
          AND existing.to_node_id = level3_owner_node.id
   );

-- 3级机构负责人分组全部完成后，无条件进入2级机构负责人分组。
INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'EC_OWNER_L3_L2', d.id, level3_owner_node.id, level2_owner_node.id,
       '进入2级机构负责人审批', 0, NULL, employee_to_level2.sort_no + 2
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE employee_node
    ON employee_node.flow_def_id = d.id
   AND employee_node.node_key = 'original_owner_employee_approve'
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE employee_to_level2
    ON employee_to_level2.flow_def_id = d.id
   AND employee_to_level2.from_node_id = employee_node.id
   AND employee_to_level2.to_node_id = level2_owner_node.id
  JOIN WF_FLOW_NODE level3_owner_node
    ON level3_owner_node.flow_def_id = d.id
   AND level3_owner_node.node_key = 'original_owner_level3_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_EDGE existing
        WHERE existing.flow_def_id = d.id
          AND existing.from_node_id = level3_owner_node.id
          AND existing.to_node_id = level2_owner_node.id
   );

INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'ER_OWNER_L3_L2', d.id, level3_owner_node.id, level2_owner_node.id,
       '进入2级机构负责人审批', 0, NULL, employee_to_level2.sort_no + 2
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE employee_node
    ON employee_node.flow_def_id = d.id
   AND employee_node.node_key = 'original_owner_employee_approve'
  JOIN WF_FLOW_NODE level2_owner_node
    ON level2_owner_node.flow_def_id = d.id
   AND level2_owner_node.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE employee_to_level2
    ON employee_to_level2.flow_def_id = d.id
   AND employee_to_level2.from_node_id = employee_node.id
   AND employee_to_level2.to_node_id = level2_owner_node.id
  JOIN WF_FLOW_NODE level3_owner_node
    ON level3_owner_node.flow_def_id = d.id
   AND level3_owner_node.node_key = 'original_owner_level3_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1 FROM WF_FLOW_EDGE existing
        WHERE existing.flow_def_id = d.id
          AND existing.from_node_id = level3_owner_node.id
          AND existing.to_node_id = level2_owner_node.id
   );

COMMIT;
