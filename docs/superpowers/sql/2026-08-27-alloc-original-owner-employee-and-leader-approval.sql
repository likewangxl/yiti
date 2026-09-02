-- ============================================================================
-- 业绩调整设计器流程：原业绩归属员工 + 原业绩所属机构负责人审批
--
-- 目标流程：FDEF_ALLOC_CORP / FDEF_ALLOC_RETAIL
--   公司部/零售部经办人选择 OWNER 后：
--     gw1_route → original_owner_employee_approve（员工会签，ALL）
--                    → original_owner_approve（按机构顺序会签，GROUP_ALL；机构内或签）
--                    → biz_dept_leader_approve
--
-- original_owner_approve 的审批人变量切换为 originalOwnerOrgApprovalGroups；旧的
-- originalOwnerOrgLeaderEmpIds 仍由业务启动变量保留，用于兼容历史流程/审计读取。
-- 虚拟员工的默认同意行为由现有运行时监听器负责，本脚本只维护设计器源模型。
--
-- 幂等约束：新节点不存在时才平移 sort_no；节点、审批人和连线均使用
-- INSERT ... SELECT + NOT EXISTS，重复执行不会重复插入或再次平移节点顺序。
-- 本脚本只包含事务控制、INSERT 和 UPDATE，不执行发布动作。
-- 执行后必须在设计器中分别重新发布对公、零售两条流程，使影子流程定义同步。
-- ============================================================================

START TRANSACTION;

-- 首次新增前，按原 OWNER 入边的 sort_no 统一顺延同流程后续连线；员工节点存在时不再平移。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_DEF d
  ON d.id = e.flow_def_id
JOIN WF_FLOW_NODE route
  ON route.flow_def_id = e.flow_def_id
 AND route.node_key = 'gw1_route'
JOIN WF_FLOW_NODE owner
  ON owner.flow_def_id = e.flow_def_id
 AND owner.node_key = 'original_owner_approve'
JOIN WF_FLOW_EDGE owner_entry
  ON owner_entry.flow_def_id = e.flow_def_id
 AND owner_entry.from_node_id = route.id
 AND owner_entry.to_node_id = owner.id
 AND owner_entry.condition_json LIKE '%OWNER%'
LEFT JOIN WF_FLOW_NODE emp
  ON emp.flow_def_id = e.flow_def_id
 AND emp.node_key = 'original_owner_employee_approve'
SET e.sort_no = e.sort_no + 1
WHERE d.id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND emp.id IS NULL
  AND e.sort_no > owner_entry.sort_no;

-- 仅在对应流程尚未新增员工审批节点时，把原负责人节点及其后续节点顺延一位。
UPDATE WF_FLOW_NODE n
JOIN WF_FLOW_DEF d
  ON d.id = n.flow_def_id
JOIN WF_FLOW_NODE owner
  ON owner.flow_def_id = n.flow_def_id
 AND owner.node_key = 'original_owner_approve'
LEFT JOIN WF_FLOW_NODE emp
  ON emp.flow_def_id = n.flow_def_id
 AND emp.node_key = 'original_owner_employee_approve'
SET n.sort_no = n.sort_no + 1
WHERE d.id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND emp.id IS NULL
  AND n.sort_no >= owner.sort_no;

-- 新增对公原业绩归属员工会签节点，插在原机构负责人节点之前。
INSERT INTO WF_FLOW_NODE
    (id, flow_def_id, node_key, node_type, name, approve_mode, sort_no, pos_x, pos_y)
SELECT 'NC_ORIG_OWNER_EMP', d.id, 'original_owner_employee_approve',
       'APPROVAL', '原业绩归属员工审批', 'ALL', owner.sort_no - 1,
       GREATEST(COALESCE(owner.pos_x, 180) - 180, 0), owner.pos_y
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE owner
    ON owner.flow_def_id = d.id
   AND owner.node_key = 'original_owner_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_NODE existing
        WHERE existing.flow_def_id = d.id
          AND existing.node_key = 'original_owner_employee_approve'
   );

-- 新增零售原业绩归属员工会签节点，插在原机构负责人节点之前。
INSERT INTO WF_FLOW_NODE
    (id, flow_def_id, node_key, node_type, name, approve_mode, sort_no, pos_x, pos_y)
SELECT 'NR_ORIG_OWNER_EMP', d.id, 'original_owner_employee_approve',
       'APPROVAL', '原业绩归属员工审批', 'ALL', owner.sort_no - 1,
       GREATEST(COALESCE(owner.pos_x, 180) - 180, 0), owner.pos_y
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE owner
    ON owner.flow_def_id = d.id
   AND owner.node_key = 'original_owner_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_NODE existing
        WHERE existing.flow_def_id = d.id
          AND existing.node_key = 'original_owner_employee_approve'
   );

-- 新员工节点审批人：原业绩归属员工，会签。
INSERT INTO WF_FLOW_NODE_APPROVER
    (id, node_id, approver_type, approver_value, sort_no)
SELECT 'AC_ORIG_OWNER_EMP', n.id, 'VAR', 'originalOwnerEmpIds', 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE n
    ON n.flow_def_id = d.id
   AND n.node_key = 'original_owner_employee_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_NODE_APPROVER a
        WHERE a.node_id = n.id
          AND a.approver_type = 'VAR'
          AND a.approver_value = 'originalOwnerEmpIds'
   );

INSERT INTO WF_FLOW_NODE_APPROVER
    (id, node_id, approver_type, approver_value, sort_no)
SELECT 'AR_ORIG_OWNER_EMP', n.id, 'VAR', 'originalOwnerEmpIds', 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE n
    ON n.flow_def_id = d.id
   AND n.node_key = 'original_owner_employee_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_NODE_APPROVER a
        WHERE a.node_id = n.id
          AND a.approver_type = 'VAR'
          AND a.approver_value = 'originalOwnerEmpIds'
   );

-- 原业绩所属机构负责人改为按机构顺序会签、组内或签；候选变量切换为机构分组变量。
UPDATE WF_FLOW_NODE n
JOIN WF_FLOW_DEF d
  ON d.id = n.flow_def_id
SET n.approve_mode = 'GROUP_ALL'
WHERE d.id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND n.node_key = 'original_owner_approve';

UPDATE WF_FLOW_NODE_APPROVER a
JOIN WF_FLOW_NODE n
  ON n.id = a.node_id
JOIN WF_FLOW_DEF d
  ON d.id = n.flow_def_id
SET a.approver_value = 'originalOwnerOrgApprovalGroups'
WHERE d.id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL')
  AND n.node_key = 'original_owner_approve'
  AND a.approver_type = 'VAR';

-- 原业绩归属员工节点 → 原业绩所属机构负责人节点：无条件直连，使用原 OWNER 入边的后继位置。
-- 此处先插入新边，再重连网关边，才能读取原 gw1_route → owner 入边的 sort_no。
INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'EC_OWNER_EMP_LEADER', d.id, emp.id, owner.id,
       NULL, 0, NULL, owner_entry.sort_no + 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE route
    ON route.flow_def_id = d.id
   AND route.node_key = 'gw1_route'
  JOIN WF_FLOW_NODE owner
    ON owner.flow_def_id = d.id
   AND owner.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE owner_entry
    ON owner_entry.flow_def_id = d.id
   AND owner_entry.from_node_id = route.id
   AND owner_entry.to_node_id = owner.id
   AND owner_entry.condition_json LIKE '%OWNER%'
  JOIN WF_FLOW_NODE emp
    ON emp.flow_def_id = d.id
   AND emp.node_key = 'original_owner_employee_approve'
 WHERE d.id = 'FDEF_ALLOC_CORP'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_EDGE e
        WHERE e.flow_def_id = d.id
          AND e.from_node_id = emp.id
          AND e.to_node_id = owner.id
   );

INSERT INTO WF_FLOW_EDGE
    (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no)
SELECT 'ER_OWNER_EMP_LEADER', d.id, emp.id, owner.id,
       NULL, 0, NULL, owner_entry.sort_no + 1
  FROM WF_FLOW_DEF d
  JOIN WF_FLOW_NODE route
    ON route.flow_def_id = d.id
   AND route.node_key = 'gw1_route'
  JOIN WF_FLOW_NODE owner
    ON owner.flow_def_id = d.id
   AND owner.node_key = 'original_owner_approve'
  JOIN WF_FLOW_EDGE owner_entry
    ON owner_entry.flow_def_id = d.id
   AND owner_entry.from_node_id = route.id
   AND owner_entry.to_node_id = owner.id
   AND owner_entry.condition_json LIKE '%OWNER%'
  JOIN WF_FLOW_NODE emp
    ON emp.flow_def_id = d.id
   AND emp.node_key = 'original_owner_employee_approve'
 WHERE d.id = 'FDEF_ALLOC_RETAIL'
   AND NOT EXISTS (
       SELECT 1
         FROM WF_FLOW_EDGE e
        WHERE e.flow_def_id = d.id
          AND e.from_node_id = emp.id
          AND e.to_node_id = owner.id
   );

-- OWNER 条件边改到员工节点；精确按两条目标流程的节点关系定位，重复执行无变化。
UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_DEF d
  ON d.id = e.flow_def_id
JOIN WF_FLOW_NODE route
  ON route.id = e.from_node_id
 AND route.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE owner
  ON owner.id = e.to_node_id
 AND owner.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE emp
  ON emp.flow_def_id = e.flow_def_id
 AND emp.node_key = 'original_owner_employee_approve'
SET e.to_node_id = emp.id,
    e.name = '原业绩归属员工审批'
WHERE d.id = 'FDEF_ALLOC_CORP'
  AND route.node_key = 'gw1_route'
  AND owner.node_key = 'original_owner_approve'
  AND e.condition_json LIKE '%OWNER%';

UPDATE WF_FLOW_EDGE e
JOIN WF_FLOW_DEF d
  ON d.id = e.flow_def_id
JOIN WF_FLOW_NODE route
  ON route.id = e.from_node_id
 AND route.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE owner
  ON owner.id = e.to_node_id
 AND owner.flow_def_id = e.flow_def_id
JOIN WF_FLOW_NODE emp
  ON emp.flow_def_id = e.flow_def_id
 AND emp.node_key = 'original_owner_employee_approve'
SET e.to_node_id = emp.id,
    e.name = '原业绩归属员工审批'
WHERE d.id = 'FDEF_ALLOC_RETAIL'
  AND route.node_key = 'gw1_route'
  AND owner.node_key = 'original_owner_approve'
  AND e.condition_json LIKE '%OWNER%';

-- 现有 original_owner_approve → biz_dept_leader_approve 边不修改，保持原流程汇合关系。

-- 网关 OWNER 边和员工 → 负责人边完成后，需通过设计器重新发布两条流程。
COMMIT;
