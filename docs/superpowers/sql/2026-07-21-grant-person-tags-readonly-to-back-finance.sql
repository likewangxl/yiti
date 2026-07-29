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
