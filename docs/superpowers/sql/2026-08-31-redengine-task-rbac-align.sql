-- 红色引擎任务域 RBAC 与角色合并对齐
-- 执行边界：仅在已获授权的 yit_test 上执行；yiti、yiti_test 不得执行。
-- 本文件只包含事务控制、INSERT 和 UPDATE；所有写入均按资源/角色编码幂等。
-- 资源匹配依赖 MENU_RANK_NO：字面量及较具体路径排在通配路径之前。

START TRANSACTION;

-- 支部审核员角色停用；历史用户角色关系保留，随后为原用户补齐支部书记角色。
UPDATE PT_ROLE
SET RECORD_STATUS = 1,
    UPDATE_USER = 'redengine-rbac-20260831',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE ROLE_CODE = 'R_RE_BRREV'
  AND SYS_CODE = 'RE';

UPDATE PT_ROLE
SET RECORD_STATUS = 0,
    ROLE_CHNAME = '党建支部书记',
    UPDATE_USER = 'redengine-rbac-20260831',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE ROLE_CODE = 'R_RE_SECR'
  AND SYS_CODE = 'RE';

-- 党内描述角色同步为支部书记；不删除历史映射。
UPDATE RE_USER_PARTY_MAP
SET PARTY_ROLE = 'SECRETARY',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE PARTY_ROLE = 'BRANCH_REVIEWER';

-- 原支部审核员用户继承支部书记平台角色；已有关系不重复写入。
INSERT INTO PT_USER_ROLE
    (USER_ID, ROLE_ID, DEFAULT_ASSIGN, INHERIT_ASSIGN, GROUP_ASSING, CREATE_TIME)
SELECT old_role.USER_ID,
       secretary_role.ROLE_ID,
       old_role.DEFAULT_ASSIGN,
       old_role.INHERIT_ASSIGN,
       old_role.GROUP_ASSING,
       COALESCE(old_role.CREATE_TIME, CURRENT_TIMESTAMP)
FROM PT_USER_ROLE old_role
JOIN PT_ROLE branch_role
  ON branch_role.ROLE_ID = old_role.ROLE_ID
 AND branch_role.ROLE_CODE = 'R_RE_BRREV'
JOIN PT_ROLE secretary_role
  ON secretary_role.ROLE_CODE = 'R_RE_SECR'
 AND secretary_role.RECORD_STATUS = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM PT_USER_ROLE existing_role
    WHERE existing_role.USER_ID = old_role.USER_ID
      AND existing_role.ROLE_ID = secretary_role.ROLE_ID
);

-- 归档、旧同步导出以及旧版逾期执行入口由任务/首页新接口替代；只停用资源，保留关联记录。
UPDATE PT_RESOURCE
SET STATUS = 1,
    UPDATE_USER = 'redengine-rbac-20260831',
    UPDATE_TIME = CURRENT_TIMESTAMP
WHERE RESOURCE_ID IN ('P_RE_CKPT_ANNUAL', 'P_RE_EXPORT', 'P_RE_CKPT_EXEC')
  AND SYS_CODE = 'RE';

-- 支部书记不再继承报送、驾驶舱、组织管理及旧审核资源；旧关系改挂到已停用角色，物理关系不删除。
UPDATE PT_ROLE_RESOURCE relation_row
JOIN PT_ROLE from_role
  ON from_role.ROLE_ID = relation_row.ROLE_ID
 AND from_role.ROLE_CODE = 'R_RE_SECR'
JOIN PT_ROLE retired_role
  ON retired_role.ROLE_CODE = 'R_RE_BRREV'
SET relation_row.ROLE_ID = retired_role.ROLE_ID
WHERE relation_row.RESOURCE_ID IN (
    'G_FILE_UPLOAD',
    'P_RE_CKPT_VIEW',
    'P_RE_EXPORT',
    'P_RE_ORG_GET',
    'P_RE_ORG_TREE',
    'P_RE_SUBMIT_ADD',
    'P_RE_SUBMIT_GET',
    'P_RE_SUBMIT_MY',
    'P_RE_REVIEW_Q',
    'P_RE_REVIEW_APPR',
    'P_RE_REVIEW_REJ'
);

-- 报送员仅保留报送、首页、预警和四维材料入口；历史组织树只读关系改挂停用角色。
UPDATE PT_ROLE_RESOURCE relation_row
JOIN PT_ROLE from_role
  ON from_role.ROLE_ID = relation_row.ROLE_ID
 AND from_role.ROLE_CODE = 'R_RE_REPORT'
JOIN PT_ROLE retired_role
  ON retired_role.ROLE_CODE = 'R_RE_BRREV'
SET relation_row.ROLE_ID = retired_role.ROLE_ID
WHERE relation_row.RESOURCE_ID IN ('P_RE_ORG_GET', 'P_RE_ORG_TREE');

-- 新增平台菜单资源；菜单页面沿用现有红色引擎布局。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
     MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT desired.resource_id,
       desired.resource_url,
       desired.resource_method,
       desired.menu_name,
       desired.menu_rank_no,
       desired.is_menu,
       desired.menu_end_flag,
       desired.parent_resource_id,
       desired.status,
       desired.sys_code,
       'redengine-rbac-20260831',
       '任务域菜单与角色合并对齐'
FROM (
    SELECT 'M_RE_REPORT' resource_id, '/redengine/report' resource_url, 'MENU' resource_method,
           '四大维度材料上报' menu_name, 10 menu_rank_no, 1 is_menu, '1' menu_end_flag,
           'M_RE_ENGINE' parent_resource_id, 0 status, 'RE' sys_code
    UNION ALL SELECT 'M_RE_RECORDS', '/redengine/records', 'MENU', '上报信息', 20, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_BRANCH_WORK', '/redengine/branch-review', 'MENU', '支部审核工作台', 30, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_COCKPIT', '/redengine/cockpit', 'MENU', '全局数据驾驶舱', 40, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_WARNING', '/redengine/warning', 'MENU', '红黄牌预警池', 50, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_REVIEW_WORK', '/redengine/review', 'MENU', '工作台', 60, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_TASK_MGMT', '/redengine/task-management', 'MENU', '任务管理', 70, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_ORG_MANAGE', '/redengine/org-manage', 'MENU', '党组织管理', 80, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_USER_MAP', '/redengine/user-map', 'MENU', '用户党组织映射', 90, 1, '1', 'M_RE_ENGINE', 0, 'RE'
) desired
WHERE NOT EXISTS (
    SELECT 1 FROM PT_RESOURCE existing_resource
    WHERE existing_resource.RESOURCE_ID = desired.resource_id
);

-- 新增任务、首页、审核、附件及异步导出 REST 资源。
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU,
     MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
SELECT desired.resource_id,
       desired.resource_url,
       desired.resource_method,
       desired.menu_name,
       desired.menu_rank_no,
       desired.is_menu,
       desired.menu_end_flag,
       desired.parent_resource_id,
       desired.status,
       desired.sys_code,
       'redengine-rbac-20260831',
       '任务域 REST 资源与角色合并对齐'
FROM (
    SELECT 'P_RE_HOME_SUM' resource_id, '/api/re/home/summary' resource_url, 'GET' resource_method,
           '红色引擎-首页汇总' menu_name, 0 menu_rank_no, 0 is_menu, '0' menu_end_flag,
           'M_RE_ENGINE' parent_resource_id, 0 status, 'RE' sys_code
    UNION ALL SELECT 'P_RE_HOME_RANK', '/api/re/home/ranking', 'GET', '红色引擎-支部排名', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_WARN', '/api/re/home/warning-pool', 'GET', '红色引擎-红黄牌预警池', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_OVER', '/api/re/home/overdue', 'GET', '红色引擎-逾期待执行扣分', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_EXEC', '/api/re/home/overdue/execute', 'POST', '红色引擎-执行逾期扣分', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_LIST', '/api/re/tasks', 'GET', '红色引擎-任务列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_CREATE', '/api/re/tasks', 'POST', '红色引擎-新增发布任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_GET', '/api/re/tasks/*', 'GET', '红色引擎-任务详情', 10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_ASG_LIST', '/api/re/tasks/*/assignments', 'GET', '红色引擎-任务支部进度', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_MY', '/api/re/tasks/my-assignments', 'GET', '红色引擎-我的任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_ASG_GET', '/api/re/tasks/assignments/*', 'GET', '红色引擎-任务分配详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_SUBMIT', '/api/re/tasks/submissions', 'POST', '红色引擎-提交任务填报', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_F_LIST', '/api/re/tasks/assignments/*/attachments', 'GET', '红色引擎-任务附件列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_F_DOWN', '/api/re/tasks/*/assignments/*/attachments/*/download', 'GET', '红色引擎-下载任务附件', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BQ', '/api/re/tasks/branch-review', 'GET', '红色引擎-支部审核列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BG', '/api/re/tasks/branch-review/*', 'GET', '红色引擎-支部审核详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BA', '/api/re/tasks/branch-review/*/approve', 'POST', '红色引擎-支部审核通过', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BR', '/api/re/tasks/branch-review/*/reject', 'POST', '红色引擎-支部驳回任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BS', '/api/re/tasks/branch-review/*/submit-to-org', 'POST', '红色引擎-提交组织审核', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BQC', '/api/re/reviews/tasks/branch/queue', 'GET', '红色引擎-支部审核列表(标准)', -20, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BGC', '/api/re/reviews/tasks/branch/*', 'GET', '红色引擎-支部审核详情(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BAC', '/api/re/reviews/tasks/branch/*/approve', 'POST', '红色引擎-支部审核通过(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BRC', '/api/re/reviews/tasks/branch/*/reject', 'POST', '红色引擎-支部驳回任务(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BSC', '/api/re/reviews/tasks/branch/*/submit-to-org', 'POST', '红色引擎-提交组织审核(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OQ', '/api/re/tasks/org-review', 'GET', '红色引擎-组织审核列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OG', '/api/re/tasks/org-review/*', 'GET', '红色引擎-组织审核详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OA', '/api/re/tasks/org-review/*/approve', 'POST', '红色引擎-组织审核通过', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OR', '/api/re/tasks/org-review/*/reject', 'POST', '红色引擎-组织驳回任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OQC', '/api/re/reviews/tasks/org/queue', 'GET', '红色引擎-组织审核列表(标准)', -20, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OGC', '/api/re/reviews/tasks/org/*', 'GET', '红色引擎-组织审核详情(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OAC', '/api/re/reviews/tasks/org/*/approve', 'POST', '红色引擎-组织审核通过(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_ORC', '/api/re/reviews/tasks/org/*/reject', 'POST', '红色引擎-组织驳回任务(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_NEW', '/api/re/tasks/*/exports', 'POST', '红色引擎-创建异步导出', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_GET', '/api/re/task-exports/*', 'GET', '红色引擎-导出状态', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_DL', '/api/re/task-exports/*/download', 'GET', '红色引擎-下载导出文件', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
) desired
WHERE NOT EXISTS (
    SELECT 1 FROM PT_RESOURCE existing_resource
    WHERE existing_resource.RESOURCE_ID = desired.resource_id
);

-- 对已存在的新增资源补齐路径、父菜单和排序，保证重放后仍按当前契约匹配。
UPDATE PT_RESOURCE resource_row
JOIN (
    SELECT 'M_RE_REPORT' resource_id, '/redengine/report' resource_url, 'MENU' resource_method, '四大维度材料上报' menu_name, 10 menu_rank_no, 1 is_menu, '1' menu_end_flag, 'M_RE_ENGINE' parent_resource_id, 0 status, 'RE' sys_code
    UNION ALL SELECT 'M_RE_RECORDS', '/redengine/records', 'MENU', '上报信息', 20, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_BRANCH_WORK', '/redengine/branch-review', 'MENU', '支部审核工作台', 30, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_COCKPIT', '/redengine/cockpit', 'MENU', '全局数据驾驶舱', 40, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_WARNING', '/redengine/warning', 'MENU', '红黄牌预警池', 50, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_REVIEW_WORK', '/redengine/review', 'MENU', '工作台', 60, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_TASK_MGMT', '/redengine/task-management', 'MENU', '任务管理', 70, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_ORG_MANAGE', '/redengine/org-manage', 'MENU', '党组织管理', 80, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'M_RE_USER_MAP', '/redengine/user-map', 'MENU', '用户党组织映射', 90, 1, '1', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_SUM', '/api/re/home/summary', 'GET', '红色引擎-首页汇总', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_RANK', '/api/re/home/ranking', 'GET', '红色引擎-支部排名', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_WARN', '/api/re/home/warning-pool', 'GET', '红色引擎-红黄牌预警池', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_OVER', '/api/re/home/overdue', 'GET', '红色引擎-逾期待执行扣分', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_HOME_EXEC', '/api/re/home/overdue/execute', 'POST', '红色引擎-执行逾期扣分', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_LIST', '/api/re/tasks', 'GET', '红色引擎-任务列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_CREATE', '/api/re/tasks', 'POST', '红色引擎-新增发布任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_GET', '/api/re/tasks/*', 'GET', '红色引擎-任务详情', 10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_ASG_LIST', '/api/re/tasks/*/assignments', 'GET', '红色引擎-任务支部进度', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_MY', '/api/re/tasks/my-assignments', 'GET', '红色引擎-我的任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_ASG_GET', '/api/re/tasks/assignments/*', 'GET', '红色引擎-任务分配详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_SUBMIT', '/api/re/tasks/submissions', 'POST', '红色引擎-提交任务填报', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_F_LIST', '/api/re/tasks/assignments/*/attachments', 'GET', '红色引擎-任务附件列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_F_DOWN', '/api/re/tasks/*/assignments/*/attachments/*/download', 'GET', '红色引擎-下载任务附件', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BQ', '/api/re/tasks/branch-review', 'GET', '红色引擎-支部审核列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BG', '/api/re/tasks/branch-review/*', 'GET', '红色引擎-支部审核详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BA', '/api/re/tasks/branch-review/*/approve', 'POST', '红色引擎-支部审核通过', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BR', '/api/re/tasks/branch-review/*/reject', 'POST', '红色引擎-支部驳回任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BS', '/api/re/tasks/branch-review/*/submit-to-org', 'POST', '红色引擎-提交组织审核', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BQC', '/api/re/reviews/tasks/branch/queue', 'GET', '红色引擎-支部审核列表(标准)', -20, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BGC', '/api/re/reviews/tasks/branch/*', 'GET', '红色引擎-支部审核详情(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BAC', '/api/re/reviews/tasks/branch/*/approve', 'POST', '红色引擎-支部审核通过(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BRC', '/api/re/reviews/tasks/branch/*/reject', 'POST', '红色引擎-支部驳回任务(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_BSC', '/api/re/reviews/tasks/branch/*/submit-to-org', 'POST', '红色引擎-提交组织审核(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OQ', '/api/re/tasks/org-review', 'GET', '红色引擎-组织审核列表', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OG', '/api/re/tasks/org-review/*', 'GET', '红色引擎-组织审核详情', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OA', '/api/re/tasks/org-review/*/approve', 'POST', '红色引擎-组织审核通过', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OR', '/api/re/tasks/org-review/*/reject', 'POST', '红色引擎-组织驳回任务', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OQC', '/api/re/reviews/tasks/org/queue', 'GET', '红色引擎-组织审核列表(标准)', -20, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OGC', '/api/re/reviews/tasks/org/*', 'GET', '红色引擎-组织审核详情(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_OAC', '/api/re/reviews/tasks/org/*/approve', 'POST', '红色引擎-组织审核通过(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_WF_ORC', '/api/re/reviews/tasks/org/*/reject', 'POST', '红色引擎-组织驳回任务(标准)', -10, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_NEW', '/api/re/tasks/*/exports', 'POST', '红色引擎-创建异步导出', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_GET', '/api/re/task-exports/*', 'GET', '红色引擎-导出状态', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
    UNION ALL SELECT 'P_RE_TASK_EXP_DL', '/api/re/task-exports/*/download', 'GET', '红色引擎-下载导出文件', 0, 0, '0', 'M_RE_ENGINE', 0, 'RE'
) desired
ON desired.resource_id = resource_row.RESOURCE_ID
SET resource_row.RESOURCE_URL = desired.resource_url,
    resource_row.RESOURCE_METHOD = desired.resource_method,
    resource_row.MENU_NAME = desired.menu_name,
    resource_row.MENU_RANK_NO = desired.menu_rank_no,
    resource_row.ISMENU = desired.is_menu,
    resource_row.MENU_ENDFLAG = desired.menu_end_flag,
    resource_row.PARENT_RESOURCE_ID = desired.parent_resource_id,
    resource_row.STATUS = desired.status,
    resource_row.SYS_CODE = desired.sys_code,
    resource_row.UPDATE_USER = 'redengine-rbac-20260831',
    resource_row.UPDATE_TIME = CURRENT_TIMESTAMP;

-- 角色菜单权限：组织审核员与系统管理员拥有组织管理/任务管理；书记只保留支部工作台。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(role_row.ROLE_ID, '#', grant_row.resource_id)),
       role_row.ROLE_ID,
       grant_row.resource_id,
       resource_row.SYS_CODE,
       CURRENT_TIMESTAMP
FROM PT_ROLE role_row
JOIN (
    SELECT 'R_RE_REPORT' role_code, 'M_RE_ENGINE' resource_id
    UNION ALL SELECT 'R_RE_REPORT', 'M_RE_REPORT'
    UNION ALL SELECT 'R_RE_REPORT', 'M_RE_RECORDS'
    UNION ALL SELECT 'R_RE_REPORT', 'M_RE_WARNING'
    UNION ALL SELECT 'R_RE_SECR', 'M_RE_ENGINE'
    UNION ALL SELECT 'R_RE_SECR', 'M_RE_BRANCH_WORK'
    UNION ALL SELECT 'R_RE_SECR', 'M_RE_WARNING'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_ENGINE'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_COCKPIT'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_WARNING'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_REVIEW_WORK'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_TASK_MGMT'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_ORG_MANAGE'
    UNION ALL SELECT 'R_RE_ORGREV', 'M_RE_USER_MAP'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_ENGINE'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_REPORT'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_RECORDS'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_BRANCH_WORK'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_COCKPIT'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_WARNING'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_REVIEW_WORK'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_TASK_MGMT'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_ORG_MANAGE'
    UNION ALL SELECT 'SYS_ADMIN', 'M_RE_USER_MAP'
) grant_row
  ON grant_row.role_code = role_row.ROLE_CODE
JOIN PT_RESOURCE resource_row
  ON resource_row.RESOURCE_ID = grant_row.resource_id
 AND resource_row.STATUS = 0
WHERE role_row.RECORD_STATUS = 0
  AND NOT EXISTS (
      SELECT 1
      FROM PT_ROLE_RESOURCE existing_relation
      WHERE existing_relation.ROLE_ID = role_row.ROLE_ID
        AND existing_relation.RESOURCE_ID = grant_row.resource_id
  );

-- 角色 REST 权限。支部书记只获得支部审核流程；逾期扣分仅系统管理员。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT MD5(CONCAT(role_row.ROLE_ID, '#', grant_row.resource_id)),
       role_row.ROLE_ID,
       grant_row.resource_id,
       resource_row.SYS_CODE,
       CURRENT_TIMESTAMP
FROM PT_ROLE role_row
JOIN (
    SELECT 'R_RE_REPORT' role_code, 'P_RE_HOME_SUM' resource_id
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_HOME_RANK'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_HOME_WARN'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_TASK_MY'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_TASK_ASG_GET'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_TASK_SUBMIT'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_TASK_F_LIST'
    UNION ALL SELECT 'R_RE_REPORT', 'P_RE_TASK_F_DOWN'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_HOME_SUM'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_HOME_RANK'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_HOME_WARN'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_TASK_ASG_GET'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_TASK_F_LIST'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_TASK_F_DOWN'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BQ'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BG'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BA'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BR'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BS'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BQC'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BGC'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BAC'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BRC'
    UNION ALL SELECT 'R_RE_SECR', 'P_RE_WF_BSC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_HOME_SUM'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_HOME_RANK'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_HOME_WARN'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_ORG_ADD'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_ORG_UPD'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_ORG_DEL'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_MAP_LIST'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_MAP_BIND'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_LIST'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_CREATE'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_GET'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_ASG_LIST'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_ASG_GET'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_F_LIST'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_F_DOWN'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OQ'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OG'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OA'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OR'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OQC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OGC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_OAC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_WF_ORC'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_EXP_NEW'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_EXP_GET'
    UNION ALL SELECT 'R_RE_ORGREV', 'P_RE_TASK_EXP_DL'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_HOME_SUM'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_HOME_RANK'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_HOME_WARN'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_HOME_OVER'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_HOME_EXEC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_LIST'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_CREATE'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_GET'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_ASG_LIST'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_MY'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_ASG_GET'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_SUBMIT'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_F_LIST'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_F_DOWN'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BQ'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BG'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BA'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BR'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BS'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BQC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BGC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BAC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BRC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_BSC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OQ'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OG'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OA'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OR'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OQC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OGC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_OAC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_WF_ORC'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_EXP_NEW'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_EXP_GET'
    UNION ALL SELECT 'SYS_ADMIN', 'P_RE_TASK_EXP_DL'
) grant_row
  ON grant_row.role_code = role_row.ROLE_CODE
JOIN PT_RESOURCE resource_row
  ON resource_row.RESOURCE_ID = grant_row.resource_id
 AND resource_row.STATUS = 0
WHERE role_row.RECORD_STATUS = 0
  AND NOT EXISTS (
      SELECT 1
      FROM PT_ROLE_RESOURCE existing_relation
      WHERE existing_relation.ROLE_ID = role_row.ROLE_ID
        AND existing_relation.RESOURCE_ID = grant_row.resource_id
  );

-- 组织审核员保留材料审核兼容资源；组织审核新接口及任务导出在上段已登记。
COMMIT;
