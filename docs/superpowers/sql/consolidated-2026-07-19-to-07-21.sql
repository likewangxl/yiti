-- =====================================================================
-- 合并脚本：docs/superpowers/sql 中 2026-07-19 ~ 2026-07-21 的迁移脚本汇总
--
-- 收录范围与生成规则：
--   1) 覆盖 8 个脚本。该区间内无 07-19 日期的 SQL，实际最早为 07-20。
--      （master 上 07-19 唯一提交 82539f52 为 chore(claude)，不含 SQL。）
--   2) 【未收录】2026-07-18-redengine-*.sql / 2026-07-18-screen-expansion-*.sql：
--      日期在区间外；且 redengine 三件套自身标注「仅测试库/yiti_test 演示数据」，
--      本合并脚本面向 yiti(dev)/生产同名库，不应带入。如需部署红色引擎请单独执行原脚本。
--   3) 按文件时间先后拼接，各段内容与原脚本逐字一致（含原有的「部署后自检 / 验证」
--      SELECT，执行时仅打印结果集，不影响迁移本身）。
--   4) 原脚本均未使用 yiti./onepl. 库名前缀，无需去前缀，落当前连接库即可。
--
-- ⚠️ 顺序依赖（不可调换）：
--   [07-20] 人员标签种子 会 `DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%'`
--   并按 M_SYS_DICT / G_DICT_CREATE 的角色集重建绑定；
--   [07-21] 授予角色 238 只读 必须在其之后执行，否则授权会被前者清空。
--
-- 幂等性：全部 8 段均可重复执行
--   （建表 IF NOT EXISTS；改列走 information_schema 判断；种子先 DELETE 同键再 INSERT；
--     ACT_* 回填带旧值+时间条件，重复执行影响 0 行）。
--
-- ⚠️ 环境相关段：[07-21] ACT_* 节点显示名回填 属存量数据订正，只影响 2026-07-16 之后
--   产生的流程快照行（之前的是真实历史，脚本刻意不动）。全新库执行影响 0 行，属正常。
--
-- 【执行前务必备份】PT_RESOURCE、PT_ROLE_RESOURCE、PERF_KPI_SCHEME、WF_TASK_TRANSFER，
--   以及 ACT_RU_TASK / ACT_HI_TASKINST / ACT_HI_ACTINST。备份放 docs/superpowers/sql/backup/。
-- =====================================================================



-- ########## [07-20] 人员标签 建表(PERSON_TAG/PERSON_TAG_REL) + 菜单 + G_PTAG_* 资源/角色绑定 ##########
-- 源文件: 2026-07-20-person-tag-tables-and-menu.sql

-- =====================================================================
-- 人员标签（全平台通用）—— 建表 + 菜单/资源/角色绑定 种子
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 功能: 系统设置 > 人员标签
--   标签 CRUD（删除级联删关联）、标签成员管理（新增/修改/删除）、
--   全局导入（标签名称+工号，缺标签自动新建，同步原子）、
--   详情导入（工号+姓名，整标签全量覆盖，同步原子）。
--
-- 【幂等】建表用 IF NOT EXISTS；种子先 DELETE 同键再 INSERT，可重复执行。
-- 【执行前务必备份】PT_RESOURCE / PT_ROLE_RESOURCE。
-- =====================================================================

-- ① 建表
CREATE TABLE IF NOT EXISTS `PERSON_TAG` (
  `TAG_ID`      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_NAME`    varchar(100) NOT NULL COMMENT '标签名称',
  `REMARK`      varchar(500) DEFAULT NULL COMMENT '备注',
  `CREATE_BY`   varchar(50)  DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_BY`   varchar(50)  DEFAULT NULL COMMENT '更新人工号',
  `UPDATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`TAG_ID`),
  UNIQUE KEY `UK_PERSON_TAG_NAME` (`TAG_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员标签（全平台通用）';

CREATE TABLE IF NOT EXISTS `PERSON_TAG_REL` (
  `ID`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_ID`      bigint       NOT NULL COMMENT '标签ID（PERSON_TAG.TAG_ID）',
  `USERNAME`    varchar(200) NOT NULL COMMENT '员工工号（PT_USER.USERNAME，注意不是 USER_ID 代理键）',
  `CREATE_BY`   varchar(50)  DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK_PTR_TAG_USER` (`TAG_ID`, `USERNAME`),
  KEY `IDX_PTR_USERNAME` (`USERNAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人员标签-人员关联（一人可多标签）';

-- ② 菜单（系统设置组下，排在审批流监控之后）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('M_SYS_PERSON_TAGS', '/system/person-tags', 'GET', '人员标签', NULL, 15,
   1, '1', 'M_GROUP_SYSTEM', 0, 'PLATFORM', NOW(), 'seed', '人员标签管理页');

-- ③ API 资源（AuthorizationInterceptor 对未登记 URL 一律 403，逐端点登记；Ant 通配 * 匹配单段路径变量）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID LIKE 'G_PTAG_%';
INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('G_PTAG_LIST',    '/api/admin/sys/person-tags',                        'GET',    '人员标签-列表',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '标签分页列表(含关联人数)'),
  ('G_PTAG_CREATE',  '/api/admin/sys/person-tags',                        'POST',   '人员标签-新建',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_UPDATE',  '/api/admin/sys/person-tags/*',                      'PUT',    '人员标签-编辑',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_DELETE',  '/api/admin/sys/person-tags/*',                      'DELETE', '人员标签-删除',     NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '级联删除关联人员'),
  ('G_PTAG_MEMBERS', '/api/admin/sys/person-tags/*/members',              'GET',    '人员标签-成员列表', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '工号/姓名/机构'),
  ('G_PTAG_MEM_ADD', '/api/admin/sys/person-tags/*/members',              'POST',   '人员标签-新增成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_UPD', '/api/admin/sys/person-tags/*/members/*',            'PUT',    '人员标签-修改成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_DEL', '/api/admin/sys/person-tags/*/members/*',            'DELETE', '人员标签-删除成员', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_IMPORT',  '/api/admin/sys/person-tags/import',                 'POST',   '人员标签-全局导入', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '标签+工号,缺标签自建'),
  ('G_PTAG_TPL',     '/api/admin/sys/person-tags/import-template',        'GET',    '人员标签-全局导入模板', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL),
  ('G_PTAG_MEM_IMP', '/api/admin/sys/person-tags/*/import',               'POST',   '人员标签-成员导入', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', '整标签全量覆盖'),
  ('G_PTAG_MEM_TPL', '/api/admin/sys/person-tags/member-import-template', 'GET',    '人员标签-成员导入模板', NULL, 0, 0, '0', 'M_SYS_PERSON_TAGS', 0, 'PLATFORM', NOW(), 'seed', NULL);

-- ④ 角色绑定：菜单复用 M_SYS_DICT 的角色集；API 资源复用 G_DICT_CREATE 的角色集（系统管理员）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPTAGM_', ROLE_ID), ROLE_ID, 'M_SYS_PERSON_TAGS', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_DICT';
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RR', r.RESOURCE_ID, '_', rr.ROLE_ID), rr.ROLE_ID, r.RESOURCE_ID, 'PLATFORM', NOW()
    FROM PT_RESOURCE r
    JOIN PT_ROLE_RESOURCE rr ON rr.RESOURCE_ID = 'G_DICT_CREATE'
   WHERE r.RESOURCE_ID LIKE 'G_PTAG_%';

-- =====================================================================
-- 部署后自检：
--   SHOW TABLES LIKE 'PERSON_TAG%';                                                   -- 期望 2 张
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';               -- 期望 12
--   SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%';          -- 期望 12×角色数
--   SELECT COUNT(*) FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'M_SYS_PERSON_TAGS';    -- 期望 >=1
-- =====================================================================


-- ########## [07-20] KPI 方案员工范围 emp_role_scope→emp_tag_scope 改列 + 下线 P_PERF_KPI_ROLES ##########
-- 源文件: 2026-07-20-kpi-scheme-emp-tag-scope.sql

-- =====================================================================
-- KPI 方案「员工范围」由角色改为人员标签
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 变更：PERF_KPI_SCHEME.emp_role_scope(角色编码CSV) → emp_tag_scope(标签ID CSV)
--   标签 ID 指向 PERSON_TAG.TAG_ID（人员标签页面维护，见
--   docs/superpowers/sql/2026-07-20-person-tag-tables-and-menu.sql）。
--   方案生效员工范围 = 所选标签关联员工的并集（多选，空=不限定全员）。
--
-- 【数据处理】旧角色范围无法自动映射为标签，改列后置空（本库仅 2 条测试数据
--   KPI_0610 / KPI_TEST_1 配过角色范围），上线后由业务在页面重新选标签。
-- 【幂等】用 information_schema 判断，可重复执行。
-- 【执行前务必备份】PERF_KPI_SCHEME、PT_RESOURCE。
-- =====================================================================

-- ① 改列名并置空（旧值为角色编码，与标签 ID 语义不兼容，不做迁移）
SET @has_old := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'PERF_KPI_SCHEME'
                   AND COLUMN_NAME = 'emp_role_scope');
SET @sql := IF(@has_old > 0,
  'ALTER TABLE PERF_KPI_SCHEME CHANGE COLUMN `emp_role_scope` `emp_tag_scope` varchar(500) DEFAULT NULL COMMENT ''员工标签范围(PERSON_TAG.TAG_ID CSV，空=不限定全员)''',
  'SELECT ''emp_role_scope 不存在，跳过改列'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 若为全新库（既无旧列也无新列）则补建新列
SET @has_new := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'PERF_KPI_SCHEME'
                   AND COLUMN_NAME = 'emp_tag_scope');
SET @sql := IF(@has_new = 0,
  'ALTER TABLE PERF_KPI_SCHEME ADD COLUMN `emp_tag_scope` varchar(500) DEFAULT NULL COMMENT ''员工标签范围(PERSON_TAG.TAG_ID CSV，空=不限定全员)''',
  'SELECT ''emp_tag_scope 已存在，跳过建列'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧角色编码值清空（改列会保留原值，须清掉否则被当成标签 ID 解析）
UPDATE PERF_KPI_SCHEME
   SET emp_tag_scope = NULL
 WHERE emp_tag_scope IS NOT NULL
   AND emp_tag_scope REGEXP '[^0-9,[:space:]]';

-- ② 下线角色下拉端点资源（改用人员标签下拉 G_PTAG_LIST）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_KPI_ROLES';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'P_PERF_KPI_ROLES';

-- =====================================================================
-- 部署后自检：
--   SHOW COLUMNS FROM PERF_KPI_SCHEME LIKE 'emp_%_scope';                       -- 期望只有 emp_tag_scope
--   SELECT COUNT(*) FROM PERF_KPI_SCHEME WHERE emp_tag_scope REGEXP '[^0-9,]';  -- 期望 0
--   SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID='P_PERF_KPI_ROLES';      -- 期望 0
-- =====================================================================


-- ########## [07-20] WF_TASK_TRANSFER.from_emp_id 改可空（NULL=候选池指派） ##########
-- 源文件: 2026-07-20-wf-transfer-allow-unclaimed.sql

-- =====================================================================
-- 审批流监控：放开「未签收候选组任务」的转交（指派语义）
-- 日期: 2026-07-20   目标库: yiti（生产按同名库执行）
--
-- 背景：候选组任务（如 branch_approve_l2 机构负责人会签）在无人签收时 assignee 为 NULL。
--   正常审批链路对此无要求（PC 端 approve 前自动 claim、手机端 approveTaskByEmp 不校验 assignee），
--   但转交链路此前硬性要求 assignee 非空（WF-40917 任务尚未签收），导致秘书岗无法把
--   「还没人认领的任务」指派给指定负责人——两条链路口径不一致。
--
-- 变更：WF_TASK_TRANSFER.from_emp_id 由 NOT NULL 改为可空。
--   from_emp_id 非空 = 转交（从原办理人 A 手上转给 B）
--   from_emp_id 为空 = 指派（任务尚在候选池无人签收，由发起人直接指派给 B）
--   两者均仍走两阶段（接收人认领后才真正 setAssignee）与发起即锁定（待认领期间原任务只读）。
--
-- 【幂等】用 information_schema 判断，可重复执行。
-- 【执行前务必备份】WF_TASK_TRANSFER。
-- =====================================================================

SET @is_notnull := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'WF_TASK_TRANSFER'
                      AND COLUMN_NAME = 'from_emp_id'
                      AND IS_NULLABLE = 'NO');
SET @sql := IF(@is_notnull > 0,
  'ALTER TABLE WF_TASK_TRANSFER MODIFY COLUMN `from_emp_id` varchar(32) COLLATE utf8mb4_general_ci NULL COMMENT ''原办理人工号；NULL=发起时任务尚未签收(候选池指派)''',
  'SELECT ''from_emp_id 已可空，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 部署后自检：
--   SHOW COLUMNS FROM WF_TASK_TRANSFER LIKE 'from_emp_id';   -- 期望 Null=YES
-- 说明：存量记录的 from_emp_id 均有值（旧逻辑强制非空），无需回填。
-- =====================================================================


-- ########## [07-21] 转交接收人候选端点 RES_WF_TRF_CAND 资源+角色 ##########
-- 源文件: 2026-07-21-workflow-transfer-candidates-resource.sql

-- =====================================================================
-- 转交接收人候选查询端点 PT_RESOURCE + 角色绑定
--   GET /api/workflow/monitor/tasks/{taskId}/transfer-candidates  RES_WF_TRF_CAND
--
-- 背景：转交弹窗此前列的是「本机构全部人员」（OrgController /api/orgs/{orgCode}/users），
--   节点办理资格不在前端校验、只在提交时由 initiate 抛 WF-40912 兜底，导致用户能选中
--   一个注定失败的接收人（典型：流程发起后才被授予 BRANCH_HEAD 的人不在任务身份链接
--   快照内，选了必被打回）。新端点直接返回与 initiate 同源的可选集。
--
-- 角色绑定口径：与发起端点 RES_WF_TRF_INIT 完全一致（秘书岗=231、分行行长=2）。
--   候选人名单等价于「谁能办理这个节点」，属与发起同级的敏感信息，鉴权不得比发起更松；
--   Controller 侧同样声明 @BizAuth(WORKFLOW_MONITOR, TRANSFER)。
--
-- URL 匹配说明：'*' 为单段通配，'/api/workflow/monitor/tasks/*/transfer' 不会误匹配
--   本端点的 '.../transfer-candidates'（且二者 METHOD 不同：POST vs GET），无需担心串权。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅涉及本脚本自身新增的 RESOURCE_ID，不触碰既有资源行，故未单独 mysqldump。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_TRF_CAND', '/api/workflow/monitor/tasks/*/transfer-candidates', 'GET',
   '转交接收人候选', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-21',
   'v2 转交弹窗只列有资格接收人');

-- 与发起端点同口径：秘书岗(231)/分行行长(2)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_231_CAND','231','RES_WF_TRF_CAND','PLATFORM',NOW()),
  ('WFTRF_2_CAND',  '2',  'RES_WF_TRF_CAND','PLATFORM',NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_CAND';
-- 预期 role_count = 2，且与 RES_WF_TRF_INIT 的角色集合一致
SELECT RESOURCE_ID, COUNT(*) AS role_count
  FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('RES_WF_TRF_CAND','RES_WF_TRF_INIT')
 GROUP BY RESOURCE_ID ORDER BY RESOURCE_ID;


-- ########## [07-21] 流程转交历史端点 RES_WF_TRF_HIST 资源+角色 ##########
-- 源文件: 2026-07-21-workflow-transfer-history-resource.sql

-- =====================================================================
-- 流程转交历史查询端点 PT_RESOURCE + 角色绑定
--   GET /api/workflow/monitor/processes/{processInstanceId}/transfers  RES_WF_TRF_HIST
--
-- 背景：审批流监控详情抽屉去掉「流程进度图」后，底部新增「转交历史」区块，展示该流程实例被
--   转交/指派的全过程——谁转给谁、谁认领了、谁拒绝了、拒绝原因、发起与处理时间。
--   数据全部来自既有 WF_TASK_TRANSFER 表，无 schema 变更。
--
-- 角色绑定口径：与监控列表 RES_WF_MONITOR_LIST 一致（分行行长=2、秘书岗=231，实测确认）。
--   转交历史是监控详情的一部分，能看列表的人即可看；Controller 侧用
--   @BizAuth(WORKFLOW_MONITOR, READ)，而非 TRANSFER——后者是「能发起转交」的权限，
--   看历史不必要求这么高。
--
-- URL 匹配说明：'*' 为单段通配，与既有 '/api/workflow/monitor/processes'（GET，精确）
--   路径深度不同，不会互相误匹配。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅涉及本脚本自身新增的 RESOURCE_ID，不触碰既有资源行，故未单独 mysqldump。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('RES_WF_TRF_HIST', '/api/workflow/monitor/processes/*/transfers', 'GET',
   '流程转交历史', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'wf-transfer-2026-07-21',
   'v2 监控详情转交历史');

-- 与监控列表同口径：分行行长(2)/秘书岗(231)
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('WFTRF_2_HIST',  '2',  'RES_WF_TRF_HIST','PLATFORM',NOW()),
  ('WFTRF_231_HIST','231','RES_WF_TRF_HIST','PLATFORM',NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'RES_WF_TRF_HIST';
-- 预期两者 role_count 均为 2（与监控列表口径一致）
SELECT RESOURCE_ID, COUNT(*) AS role_count
  FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('RES_WF_TRF_HIST','RES_WF_MONITOR_LIST')
 GROUP BY RESOURCE_ID ORDER BY RESOURCE_ID;


-- ########## [07-21] original_owner_approve 节点显示名回填（ACT_* 三张快照表，存量数据订正） ##########
-- 源文件: 2026-07-21-alloc-original-owner-node-name-backfill.sql

-- =====================================================================
-- original_owner_approve 节点显示名回填：Flowable 三张快照表
--
-- 背景：2026-07-16 的 ...-alloc-original-owner-org-leader-switch.sql 把该节点从
--   「原业绩分配人本人」切到「原业绩所属 2 级机构负责人(BRANCH_HEAD)会签」，
--   同时改了设计器源表 WF_FLOW_NODE.name / WF_FLOW_EDGE.name。但节点显示名在 Flowable 侧
--   是<多处快照>：部署期写进 BPMN、运行期写进任务行、历史期写进活动/任务历史行。
--   该脚本执行时既没有重新发布流程（BPMN 未刷新），也没有回填这些快照，于是：
--     - 工作台待办任务名   ← ACT_RU_TASK.NAME_        （任务创建时快照）
--     - 业绩调整审批历史   ← ACT_HI_ACTINST.ACT_NAME_ （活动开始时快照，ProcessQueryService
--                                                      经 HistoricActivityInstance 取用）
--     - 已办任务列表       ← ACT_HI_TASKINST.NAME_
--   三处都仍显示旧名「原业绩所属人审批」。
--
-- 治本已完成：设计器两条流程已重新发布（DSN_alloc_corp_designer v7 / retail v4，
--   BPMN 内已确认为 name="原业绩所属机构负责人审批"），此后<新发起>的流程显示正确。
--   本脚本只处理<已存在>的快照行。
--
-- ⚠️ 时间边界是本脚本的关键：仅回填 2026-07-16（切换日）之后的记录。
--   之前的 22 条是<真实历史>——当时审批人确实是原业绩所属人本人，
--   改名等于伪造审计痕迹，绝不能动。
--
-- ⚠️ 同样不改静态 BPMN（perf_alloc_adjust_{corp,retail}_v1.bpmn20.xml）的节点名：
--   其 assignee=${ownerEmpId} 仍是原业绩所属人本人语义，旧名与其行为相符
--   （详见 AllocAdjustService#resolveProcessKey 的回退补注）。
--
-- 幂等：带 ACT_NAME_/NAME_ 旧值条件，重复执行影响 0 行。仅执行库 yiti（dev）。
-- 执行记录：2026-07-21 已在 yiti 执行，ACT_HI_TASKINST 1 行、ACT_HI_ACTINST 1 行，
--   ACT_RU_TASK 当时已无在途行（该实例已流转完）。
-- =====================================================================

-- 1) 运行时任务（在途待办的显示名）
UPDATE ACT_RU_TASK SET NAME_ = '原业绩所属机构负责人审批'
 WHERE TASK_DEF_KEY_ = 'original_owner_approve'
   AND NAME_ = '原业绩所属人审批'
   AND CREATE_TIME_ >= '2026-07-16';

-- 2) 历史任务实例（已办列表）
UPDATE ACT_HI_TASKINST SET NAME_ = '原业绩所属机构负责人审批'
 WHERE TASK_DEF_KEY_ = 'original_owner_approve'
   AND NAME_ = '原业绩所属人审批'
   AND START_TIME_ >= '2026-07-16';

-- 3) 历史活动实例（业绩调整「审批历史」时间线取此表）
UPDATE ACT_HI_ACTINST SET ACT_NAME_ = '原业绩所属机构负责人审批'
 WHERE ACT_ID_ = 'original_owner_approve'
   AND ACT_NAME_ = '原业绩所属人审批'
   AND START_TIME_ >= '2026-07-16';

-- ── 验证 ────────────────────────────────────────────────────
-- 预期：切换前一律旧名（真实历史，不得被改动）；切换后一律新名
SELECT 'ACT_HI_ACTINST' AS tbl,
       CASE WHEN START_TIME_ >= '2026-07-16' THEN '切换后' ELSE '切换前(历史)' END AS seg,
       ACT_NAME_, COUNT(*) AS cnt
  FROM ACT_HI_ACTINST WHERE ACT_ID_ = 'original_owner_approve' GROUP BY seg, ACT_NAME_
UNION ALL
SELECT 'ACT_HI_TASKINST',
       CASE WHEN START_TIME_ >= '2026-07-16' THEN '切换后' ELSE '切换前(历史)' END,
       NAME_, COUNT(*)
  FROM ACT_HI_TASKINST WHERE TASK_DEF_KEY_ = 'original_owner_approve' GROUP BY 2, NAME_
UNION ALL
SELECT 'ACT_RU_TASK', '在途', NAME_, COUNT(*)
  FROM ACT_RU_TASK WHERE TASK_DEF_KEY_ = 'original_owner_approve' GROUP BY NAME_;


-- ########## [07-21] 授予资财部经办人(238) 人员标签 页面+查询（只读）—— 依赖上面 07-20 人员标签种子 ##########
-- 源文件: 2026-07-21-grant-person-tags-readonly-to-back-finance.sql

-- =====================================================================
-- 授予「资财部经办人」(ROLE_ID=238, BACK_FINANCE) 人员标签页面 + 查询权限（只读）
--
-- 目标功能：**系统管理域**人员标签（菜单 /system/person-tags，API /api/admin/sys/person-tags）。
--   ⚠️ 平台内存在两套同名「人员标签」，勿混：
--     - 本脚本目标：M_SYS_PERSON_TAGS + G_PTAG_*   （系统设置 → 人员标签，标签定义与成员管理）
--     - 另一套：    M_EVAL_USER_TAGS  + PERF_EVAL_* （评价域 /eval/user-tags，绑定评价角色）
--   角色 238 早已持有评价域那套（PERF_EVAL_21/22/23/24/25），本次要加的是系统管理域这套。
--
-- 授权范围＝页面 + 查询，**不含任何写操作**：
--   ✅ M_SYS_PERSON_TAGS  菜单/页面路由
--   ✅ G_PTAG_LIST        GET  /api/admin/sys/person-tags            标签列表（页面 reload 调用）
--   ✅ G_PTAG_MEMBERS     GET  /api/admin/sys/person-tags/*/members  成员列表（详情抽屉 reloadMembers 调用）
--   ❌ 不授：CREATE/UPDATE/DELETE、MEM_ADD/MEM_UPD/MEM_DEL、IMPORT/MEM_IMP、TPL/MEM_TPL
--   （查询链路已实测对齐前端 PersonTags.vue 的两个加载函数，不多授一个端点）
--
-- 父菜单无需授权：AuthService#pruneMenuTree 的剪枝规则是「节点本身被授权 或 有后代被授权」即保留，
--   故 M_GROUP_SYSTEM（系统设置，实际无任何角色持有）会因本次子菜单授权而自动可见，
--   与角色 238 既有的 M_SYS_NOTIFICATIONS / M_SYS_CALENDAR 等同理。
--
-- ⚠️ 已知 UX 副作用（非本脚本可解）：主平台前端不按权限隐藏按钮（见 xanzc_frontend/CLAUDE.md：
--   「主平台不做前端菜单过滤，权限最终一律靠后端 403 兜底」），故该角色进入页面后仍会看到
--   新建/编辑/删除/导入按钮，点击将被后端 403。如需隐藏须改前端 PersonTags.vue 按权限渲染。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅新增 3 条 PT_ROLE_RESOURCE 授权行，不改任何资源定义、不动其他角色。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE
 WHERE ROLE_ID = '238'
   AND RESOURCE_ID IN ('M_SYS_PERSON_TAGS', 'G_PTAG_LIST', 'G_PTAG_MEMBERS');

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('PTAG238_MENU',    '238', 'M_SYS_PERSON_TAGS', 'PLATFORM', NOW()),
  ('PTAG238_LIST',    '238', 'G_PTAG_LIST',       'PLATFORM', NOW()),
  ('PTAG238_MEMBERS', '238', 'G_PTAG_MEMBERS',    'PLATFORM', NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 3 行：菜单 + 列表 + 成员列表
SELECT rr.RESOURCE_ID, r.RESOURCE_URL, r.RESOURCE_METHOD, r.MENU_NAME, r.ISMENU
  FROM PT_ROLE_RESOURCE rr JOIN PT_RESOURCE r ON r.RESOURCE_ID = rr.RESOURCE_ID
 WHERE rr.ROLE_ID = '238'
   AND rr.RESOURCE_ID IN ('M_SYS_PERSON_TAGS', 'G_PTAG_LIST', 'G_PTAG_MEMBERS')
 ORDER BY r.ISMENU DESC, rr.RESOURCE_ID;

-- 预期为空：确认没有把任何写操作端点授给该角色
SELECT rr.RESOURCE_ID AS 误授的写操作
  FROM PT_ROLE_RESOURCE rr
 WHERE rr.ROLE_ID = '238'
   AND rr.RESOURCE_ID LIKE 'G_PTAG%'
   AND rr.RESOURCE_ID NOT IN ('G_PTAG_LIST', 'G_PTAG_MEMBERS');


-- ########## [07-21] 绩效员工搜索端点 P_PERF_EMP_SRCH 资源+角色 ##########
-- 源文件: 2026-07-21-perf-employee-search-resource.sql

-- =====================================================================
-- 绩效域员工搜索端点 PT_RESOURCE + 角色绑定
--   GET /api/perf/employees/search  P_PERF_EMP_SRCH
--
-- 背景：目标值管理页「按工号选人」的输入建议原先走管理员接口 /api/admin/users，
--   该接口资源 A_USER_LIST 仅授予角色 1/3/4/131/169，资财部经办人(238)等业务角色
--   进页面即 403「没有权限」。现由绩效模块自开轻量端点替代。
--
-- 为什么不复用现成端点（三者各差一项，详见 PerfEmployeeQueryService 类注释）：
--   /api/admin/users                → 字段对，但管理员接口，业务角色无权且不宜授权
--   /api/employees（通讯录）         → 权限够，但数据源 ADDRBOOK_EMPLOYEE 覆盖不足（实测 12 vs 82）
--   /api/reports/employees/search   → 权限与覆盖面都够，但返回 USER_ID 而非工号，
--                                     与目标值 subject_id(=PT_USER.USERNAME) 对不上
--
-- 角色绑定口径：与**目标值列表** /api/perf/target-values (GET) 完全一致
--   （1 系统管理员、17 零售部负责人、128 公司部负责人、129 资财部负责人、
--     130 公司主管行领导、229 中后台员工(科技)、238 资财部经办人，实测确认）。
--   ⚠️ 刻意不按「持有 PERF_CONFIG 数据范围的全部 37 个角色」授权——那批角色里多数
--   根本进不了目标值页面，本端点是为该页面选人服务的，受众应与页面一致，避免过度授权。
--   Controller 侧 @BizAuth(PERF_CONFIG, LIST) 与目标值列表同一权限位。
--
-- 幂等：先删同名行再插入，可重复执行。仅执行库 yiti（dev）。
-- 破坏性：仅新增本脚本自身的资源与授权行，不触碰既有资源。
-- =====================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('P_PERF_EMP_SRCH', '/api/perf/employees/search', 'GET',
   '绩效员工搜索', NULL, 0, 0, '0', NULL, 0, 'PLATFORM', NOW(), 'perf-emp-2026-07-21',
   '目标值选人输入建议，返回工号');

-- 与 /api/perf/target-values (GET) 同一批角色
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME) VALUES
  ('PEMPS_1',   '1',   'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_17',  '17',  'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_128', '128', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_129', '129', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_130', '130', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_229', '229', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW()),
  ('PEMPS_238', '238', 'P_PERF_EMP_SRCH', 'PLATFORM', NOW());

-- ── 验证 ────────────────────────────────────────────────────
-- 预期 1 行资源
SELECT RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, ISMENU
  FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_EMP_SRCH';

-- 预期两行角色集合完全相同（新端点 与 目标值列表）
SELECT '新端点' AS src, GROUP_CONCAT(rr.ROLE_ID ORDER BY CAST(rr.ROLE_ID AS UNSIGNED)) AS roles
  FROM PT_ROLE_RESOURCE rr WHERE rr.RESOURCE_ID = 'P_PERF_EMP_SRCH'
UNION ALL
SELECT '目标值列表', GROUP_CONCAT(rr.ROLE_ID ORDER BY CAST(rr.ROLE_ID AS UNSIGNED))
  FROM PT_ROLE_RESOURCE rr JOIN PT_RESOURCE r ON r.RESOURCE_ID = rr.RESOURCE_ID
 WHERE r.RESOURCE_URL = '/api/perf/target-values' AND r.RESOURCE_METHOD = 'GET';


-- =====================================================================
-- 合并后统一自检（全部执行完毕后跑一次）
-- =====================================================================
-- 人员标签：2 张表 + 12 个 G_PTAG_* 资源
SELECT 'PERSON_TAG 表数(期望2)' AS chk, COUNT(*) AS val
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('PERSON_TAG','PERSON_TAG_REL')
UNION ALL
SELECT 'G_PTAG_* 资源数(期望12)', COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'G_PTAG_%'
UNION ALL
-- KPI 员工范围改列：只应存在 emp_tag_scope
SELECT 'emp_role_scope 残留(期望0)', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='PERF_KPI_SCHEME' AND COLUMN_NAME='emp_role_scope'
UNION ALL
SELECT 'emp_tag_scope 存在(期望1)', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='PERF_KPI_SCHEME' AND COLUMN_NAME='emp_tag_scope'
UNION ALL
SELECT 'P_PERF_KPI_ROLES 已下线(期望0)', COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID='P_PERF_KPI_ROLES'
UNION ALL
-- 转交：from_emp_id 可空
SELECT 'from_emp_id 可空(期望1)', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='WF_TASK_TRANSFER'
   AND COLUMN_NAME='from_emp_id' AND IS_NULLABLE='YES'
UNION ALL
-- 三个新端点资源
SELECT '新端点资源数(期望3)', COUNT(*) FROM PT_RESOURCE
 WHERE RESOURCE_ID IN ('RES_WF_TRF_CAND','RES_WF_TRF_HIST','P_PERF_EMP_SRCH')
UNION ALL
-- 角色 238 的人员标签只读授权（务必在人员标签种子之后执行才会有值）
SELECT '角色238 人员标签授权(期望3)', COUNT(*) FROM PT_ROLE_RESOURCE
 WHERE ROLE_ID='238' AND RESOURCE_ID IN ('M_SYS_PERSON_TAGS','G_PTAG_LIST','G_PTAG_MEMBERS')
UNION ALL
SELECT '角色238 误授写操作(期望0)', COUNT(*) FROM PT_ROLE_RESOURCE
 WHERE ROLE_ID='238' AND RESOURCE_ID LIKE 'G_PTAG%'
   AND RESOURCE_ID NOT IN ('G_PTAG_LIST','G_PTAG_MEMBERS');
