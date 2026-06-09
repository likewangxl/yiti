-- ============================================================================
-- Step 2a：用设计器重建对公/零售分配关系调整审批流程（设计源四表种子，幂等）
-- ----------------------------------------------------------------------------
-- 结构（spec 2026-06-08-flow-designer-rebuild-alloc-flows-design.md，v2 机构层级显式分流）：
--   开始 →〔网关 机构层级路由〕
--          ├ startOrgLevel==2「2级机构（本机构审批）」→ 机构负责人审批·2级(org-scope=SELF) ─┐
--          └ startOrgLevel==3「3级机构（上级分行审批）」→ 机构负责人审批·3级(org-scope=PARENT)┤
--        → 部门经办审批(corpRouteTo) →〔网关〕
--          ├ LEADER「部门负责人审批」→ 部门负责人审批
--          └ OWNER「原业绩所属人会签」→ 原业绩所属人审批(会签 VAR originalOwnerEmpIds) → 部门负责人审批
--        →〔汇合〕→ 资财部经办审批(finRouteTo) →〔网关〕
--          ├ LEADER「资财部负责人审批」→ 资财部负责人审批 → 结束
--          └ END「审批结束」→ 结束
--
-- 与 FlowDefService 持久化格式一致：edge.name=输出名称，condition_json=FlowConditionDTO，
-- 会签节点 approve_mode=ALL + VAR 审批人 originalOwnerEmpIds；机构负责人审批按层级分流后
-- 用 approve_org_scope SELF/PARENT 把审批人过滤到正确机构（2级本分行 / 3级上级分行）。
-- 驳回即终止 + 审批结果网关由发布时 FlowBpmnGenerator 自动补，设计图无需画。
--
-- 幂等：先按固定 id 前缀清理，再插入，可重复执行。库表大写。双库 dev=yiti / prod=onepl（本期 yiti）。
-- ============================================================================

-- ---------- 幂等清理 ----------
DELETE FROM WF_FLOW_NODE_APPROVER WHERE node_id LIKE 'NC\_%' OR node_id LIKE 'NR\_%';
DELETE FROM WF_FLOW_EDGE WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL');
DELETE FROM WF_FLOW_NODE WHERE flow_def_id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL');
DELETE FROM WF_FLOW_DEF  WHERE id IN ('FDEF_ALLOC_CORP', 'FDEF_ALLOC_RETAIL');

-- ============================================================================
-- 对公线 FDEF_ALLOC_CORP
-- ============================================================================
INSERT INTO WF_FLOW_DEF (id, flow_key, biz_type, name, status, version, is_readonly_import, created_by, updated_by)
VALUES ('FDEF_ALLOC_CORP', 'alloc_corp_designer', 'ALLOC_ADJUST', '对公分配关系调整审批（设计器）', 'DRAFT', 0, 0, 'system', 'system');

INSERT INTO WF_FLOW_NODE (id, flow_def_id, node_key, node_type, name, approve_mode, approve_org_scope, sort_no, pos_x, pos_y) VALUES
 ('NC_start',                    'FDEF_ALLOC_CORP', 'start',                    'START',    '开始',              NULL,  NULL,     1,  360, 20),
 ('NC_gw_level',                 'FDEF_ALLOC_CORP', 'gw_level',                 'GATEWAY',  '机构层级路由',      NULL,  NULL,     2,  390, 110),
 ('NC_branch_l2',                'FDEF_ALLOC_CORP', 'branch_approve_l2',        'APPROVAL', '机构负责人审批·2级','ANY', 'SELF',   3,  190, 210),
 ('NC_branch_l3',                'FDEF_ALLOC_CORP', 'branch_approve_l3',        'APPROVAL', '机构负责人审批·3级','ANY', 'PARENT', 4,  560, 210),
 ('NC_biz_dept_review',          'FDEF_ALLOC_CORP', 'biz_dept_review',          'APPROVAL', '公司部经办审批',    'ANY', NULL,     5,  360, 320),
 ('NC_gw1_route',                'FDEF_ALLOC_CORP', 'gw1_route',                'GATEWAY',  '公司部路由',        NULL,  NULL,     6,  390, 420),
 ('NC_biz_dept_leader_approve',  'FDEF_ALLOC_CORP', 'biz_dept_leader_approve',  'APPROVAL', '公司部负责人审批',  'ANY', NULL,     7,  360, 520),
 ('NC_original_owner_approve',   'FDEF_ALLOC_CORP', 'original_owner_approve',   'APPROVAL', '原业绩所属人审批',  'ALL', NULL,     8,  660, 420),
 ('NC_gw1_join',                 'FDEF_ALLOC_CORP', 'gw1_join',                 'GATEWAY',  '公司部汇合',        NULL,  NULL,     9,  390, 620),
 ('NC_finance_review',           'FDEF_ALLOC_CORP', 'finance_review',           'APPROVAL', '资财部经办审批',    'ANY', NULL,     10, 360, 710),
 ('NC_gw2_route',                'FDEF_ALLOC_CORP', 'gw2_route',                'GATEWAY',  '资财部路由',        NULL,  NULL,     11, 390, 810),
 ('NC_finance_leader_approve',   'FDEF_ALLOC_CORP', 'finance_leader_approve',   'APPROVAL', '资财部负责人审批',  'ANY', NULL,     12, 360, 910),
 ('NC_end',                      'FDEF_ALLOC_CORP', 'end',                      'END',      '结束',              NULL,  NULL,     13, 360, 1010);

INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no) VALUES
 ('AC_1',  'NC_branch_l2',               'ROLE', 'BRANCH_HEAD',          1),
 ('AC_2',  'NC_branch_l2',               'ROLE', 'BRANCH_PRE',           2),
 ('AC_3',  'NC_branch_l2',               'ROLE', 'R_5F1F1A19',           3),
 ('AC_4',  'NC_branch_l3',               'ROLE', 'BRANCH_HEAD',          1),
 ('AC_5',  'NC_branch_l3',               'ROLE', 'BRANCH_PRE',           2),
 ('AC_6',  'NC_branch_l3',               'ROLE', 'R_5F1F1A19',           3),
 ('AC_7',  'NC_biz_dept_review',         'ROLE', 'CORP_DEPT',            1),
 ('AC_8',  'NC_biz_dept_leader_approve', 'ROLE', 'CORP_DEPT_LEADER',     1),
 ('AC_9',  'NC_original_owner_approve',  'VAR',  'originalOwnerEmpIds',  1),
 ('AC_10', 'NC_finance_review',          'ROLE', 'BACK_FINANCE',         1),
 ('AC_11', 'NC_finance_leader_approve',  'ROLE', 'FINANCE_LEADER',       1);

INSERT INTO WF_FLOW_EDGE (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no) VALUES
 ('EC_1',  'FDEF_ALLOC_CORP', 'NC_start',                   'NC_gw_level',                NULL,                 0, NULL, 1),
 ('EC_2',  'FDEF_ALLOC_CORP', 'NC_gw_level',                'NC_branch_l2',               '2级机构（本机构审批）',   0, '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"2"}]}', 2),
 ('EC_3',  'FDEF_ALLOC_CORP', 'NC_gw_level',                'NC_branch_l3',               '3级机构（上级分行审批）', 0, '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"3"}]}', 3),
 ('EC_4',  'FDEF_ALLOC_CORP', 'NC_branch_l2',               'NC_biz_dept_review',         NULL,                 0, NULL, 4),
 ('EC_5',  'FDEF_ALLOC_CORP', 'NC_branch_l3',               'NC_biz_dept_review',         NULL,                 0, NULL, 5),
 ('EC_6',  'FDEF_ALLOC_CORP', 'NC_biz_dept_review',         'NC_gw1_route',               NULL,                 0, NULL, 6),
 ('EC_7',  'FDEF_ALLOC_CORP', 'NC_gw1_route',               'NC_biz_dept_leader_approve', '部门负责人审批',     0, '{"logic":"AND","conditions":[{"field":"corpRouteTo","op":"EQ","value":"LEADER"}]}', 7),
 ('EC_8',  'FDEF_ALLOC_CORP', 'NC_gw1_route',               'NC_original_owner_approve',  '原业绩所属人会签',   0, '{"logic":"AND","conditions":[{"field":"corpRouteTo","op":"EQ","value":"OWNER"}]}', 8),
 ('EC_9',  'FDEF_ALLOC_CORP', 'NC_original_owner_approve',  'NC_biz_dept_leader_approve', NULL,                 0, NULL, 9),
 ('EC_10', 'FDEF_ALLOC_CORP', 'NC_biz_dept_leader_approve', 'NC_gw1_join',                NULL,                 0, NULL, 10),
 ('EC_11', 'FDEF_ALLOC_CORP', 'NC_gw1_join',                'NC_finance_review',          NULL,                 0, NULL, 11),
 ('EC_12', 'FDEF_ALLOC_CORP', 'NC_finance_review',          'NC_gw2_route',               NULL,                 0, NULL, 12),
 ('EC_13', 'FDEF_ALLOC_CORP', 'NC_gw2_route',               'NC_finance_leader_approve',  '资财部负责人审批',   0, '{"logic":"AND","conditions":[{"field":"finRouteTo","op":"EQ","value":"LEADER"}]}', 13),
 ('EC_14', 'FDEF_ALLOC_CORP', 'NC_gw2_route',               'NC_end',                     '审批结束',           0, '{"logic":"AND","conditions":[{"field":"finRouteTo","op":"EQ","value":"END"}]}', 14),
 ('EC_15', 'FDEF_ALLOC_CORP', 'NC_finance_leader_approve',  'NC_end',                     NULL,                 0, NULL, 15);

-- ============================================================================
-- 零售线 FDEF_ALLOC_RETAIL（结构同对公，部门名/角色换零售）
-- ============================================================================
INSERT INTO WF_FLOW_DEF (id, flow_key, biz_type, name, status, version, is_readonly_import, created_by, updated_by)
VALUES ('FDEF_ALLOC_RETAIL', 'alloc_retail_designer', 'ALLOC_ADJUST', '零售分配关系调整审批（设计器）', 'DRAFT', 0, 0, 'system', 'system');

INSERT INTO WF_FLOW_NODE (id, flow_def_id, node_key, node_type, name, approve_mode, approve_org_scope, sort_no, pos_x, pos_y) VALUES
 ('NR_start',                    'FDEF_ALLOC_RETAIL', 'start',                    'START',    '开始',              NULL,  NULL,     1,  360, 20),
 ('NR_gw_level',                 'FDEF_ALLOC_RETAIL', 'gw_level',                 'GATEWAY',  '机构层级路由',      NULL,  NULL,     2,  390, 110),
 ('NR_branch_l2',                'FDEF_ALLOC_RETAIL', 'branch_approve_l2',        'APPROVAL', '机构负责人审批·2级','ANY', 'SELF',   3,  190, 210),
 ('NR_branch_l3',                'FDEF_ALLOC_RETAIL', 'branch_approve_l3',        'APPROVAL', '机构负责人审批·3级','ANY', 'PARENT', 4,  560, 210),
 ('NR_biz_dept_review',          'FDEF_ALLOC_RETAIL', 'biz_dept_review',          'APPROVAL', '零售部经办审批',    'ANY', NULL,     5,  360, 320),
 ('NR_gw1_route',                'FDEF_ALLOC_RETAIL', 'gw1_route',                'GATEWAY',  '零售部路由',        NULL,  NULL,     6,  390, 420),
 ('NR_biz_dept_leader_approve',  'FDEF_ALLOC_RETAIL', 'biz_dept_leader_approve',  'APPROVAL', '零售部负责人审批',  'ANY', NULL,     7,  360, 520),
 ('NR_original_owner_approve',   'FDEF_ALLOC_RETAIL', 'original_owner_approve',   'APPROVAL', '原业绩所属人审批',  'ALL', NULL,     8,  660, 420),
 ('NR_gw1_join',                 'FDEF_ALLOC_RETAIL', 'gw1_join',                 'GATEWAY',  '零售部汇合',        NULL,  NULL,     9,  390, 620),
 ('NR_finance_review',           'FDEF_ALLOC_RETAIL', 'finance_review',           'APPROVAL', '资财部经办审批',    'ANY', NULL,     10, 360, 710),
 ('NR_gw2_route',                'FDEF_ALLOC_RETAIL', 'gw2_route',                'GATEWAY',  '资财部路由',        NULL,  NULL,     11, 390, 810),
 ('NR_finance_leader_approve',   'FDEF_ALLOC_RETAIL', 'finance_leader_approve',   'APPROVAL', '资财部负责人审批',  'ANY', NULL,     12, 360, 910),
 ('NR_end',                      'FDEF_ALLOC_RETAIL', 'end',                      'END',      '结束',              NULL,  NULL,     13, 360, 1010);

INSERT INTO WF_FLOW_NODE_APPROVER (id, node_id, approver_type, approver_value, sort_no) VALUES
 ('AR_1',  'NR_branch_l2',               'ROLE', 'BRANCH_HEAD',          1),
 ('AR_2',  'NR_branch_l2',               'ROLE', 'BRANCH_PRE',           2),
 ('AR_3',  'NR_branch_l2',               'ROLE', 'R_5F1F1A19',           3),
 ('AR_4',  'NR_branch_l3',               'ROLE', 'BRANCH_HEAD',          1),
 ('AR_5',  'NR_branch_l3',               'ROLE', 'BRANCH_PRE',           2),
 ('AR_6',  'NR_branch_l3',               'ROLE', 'R_5F1F1A19',           3),
 ('AR_7',  'NR_biz_dept_review',         'ROLE', 'RETAIL_DEPT',          1),
 ('AR_8',  'NR_biz_dept_leader_approve', 'ROLE', 'RETAIL_DEPT_LEADER',   1),
 ('AR_9',  'NR_original_owner_approve',  'VAR',  'originalOwnerEmpIds',  1),
 ('AR_10', 'NR_finance_review',          'ROLE', 'BACK_FINANCE',         1),
 ('AR_11', 'NR_finance_leader_approve',  'ROLE', 'FINANCE_LEADER',       1);

INSERT INTO WF_FLOW_EDGE (id, flow_def_id, from_node_id, to_node_id, name, is_default, condition_json, sort_no) VALUES
 ('ER_1',  'FDEF_ALLOC_RETAIL', 'NR_start',                   'NR_gw_level',                NULL,                 0, NULL, 1),
 ('ER_2',  'FDEF_ALLOC_RETAIL', 'NR_gw_level',                'NR_branch_l2',               '2级机构（本机构审批）',   0, '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"2"}]}', 2),
 ('ER_3',  'FDEF_ALLOC_RETAIL', 'NR_gw_level',                'NR_branch_l3',               '3级机构（上级分行审批）', 0, '{"logic":"AND","conditions":[{"field":"startOrgLevel","op":"EQ","value":"3"}]}', 3),
 ('ER_4',  'FDEF_ALLOC_RETAIL', 'NR_branch_l2',               'NR_biz_dept_review',         NULL,                 0, NULL, 4),
 ('ER_5',  'FDEF_ALLOC_RETAIL', 'NR_branch_l3',               'NR_biz_dept_review',         NULL,                 0, NULL, 5),
 ('ER_6',  'FDEF_ALLOC_RETAIL', 'NR_biz_dept_review',         'NR_gw1_route',               NULL,                 0, NULL, 6),
 ('ER_7',  'FDEF_ALLOC_RETAIL', 'NR_gw1_route',               'NR_biz_dept_leader_approve', '部门负责人审批',     0, '{"logic":"AND","conditions":[{"field":"corpRouteTo","op":"EQ","value":"LEADER"}]}', 7),
 ('ER_8',  'FDEF_ALLOC_RETAIL', 'NR_gw1_route',               'NR_original_owner_approve',  '原业绩所属人会签',   0, '{"logic":"AND","conditions":[{"field":"corpRouteTo","op":"EQ","value":"OWNER"}]}', 8),
 ('ER_9',  'FDEF_ALLOC_RETAIL', 'NR_original_owner_approve',  'NR_biz_dept_leader_approve', NULL,                 0, NULL, 9),
 ('ER_10', 'FDEF_ALLOC_RETAIL', 'NR_biz_dept_leader_approve', 'NR_gw1_join',                NULL,                 0, NULL, 10),
 ('ER_11', 'FDEF_ALLOC_RETAIL', 'NR_gw1_join',                'NR_finance_review',          NULL,                 0, NULL, 11),
 ('ER_12', 'FDEF_ALLOC_RETAIL', 'NR_finance_review',          'NR_gw2_route',               NULL,                 0, NULL, 12),
 ('ER_13', 'FDEF_ALLOC_RETAIL', 'NR_gw2_route',               'NR_finance_leader_approve',  '资财部负责人审批',   0, '{"logic":"AND","conditions":[{"field":"finRouteTo","op":"EQ","value":"LEADER"}]}', 13),
 ('ER_14', 'FDEF_ALLOC_RETAIL', 'NR_gw2_route',               'NR_end',                     '审批结束',           0, '{"logic":"AND","conditions":[{"field":"finRouteTo","op":"EQ","value":"END"}]}', 14),
 ('ER_15', 'FDEF_ALLOC_RETAIL', 'NR_finance_leader_approve',  'NR_end',                     NULL,                 0, NULL, 15);
