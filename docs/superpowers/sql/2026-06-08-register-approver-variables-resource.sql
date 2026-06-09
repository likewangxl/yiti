-- ============================================================================
-- 注册新端点 GET /api/admin/workflow/flows/meta/approver-variables 到 PT_RESOURCE
-- ----------------------------------------------------------------------------
-- 背景：VAR 审批人下拉源端点是新增的，鉴权拦截器（ResourceMatcher）对未注册 URL
--      返回 403（AUTH-40302），前端 GET 兜底成空数组 → 审批人「流程变量」下拉无数据。
--      现有 W_FLOW_VARS(/meta/variables) 是精确 URL 注册，/flows/* 单段通配不覆盖
--      两段的 /meta/approver-variables，故必须单独登记。
--
-- 处理：新增资源 W_FLOW_AVARS，并复制 W_FLOW_VARS 的全部角色绑定（同 21 个角色）。
-- 幂等：先删后插，可重复执行。仅 yiti（onepl 暂无流程设计器 W_FLOW_* 资源）。
-- 生效：auth 资源缓存（auth:resource:all，5min TTL）需刷新——重启应用即可。
-- ============================================================================

DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'W_FLOW_AVARS';
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID = 'W_FLOW_AVARS';

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME)
VALUES
  ('W_FLOW_AVARS', '/api/admin/workflow/flows/meta/approver-variables', 'GET', '查询审批人变量目录', 0, 0, 0, 0, 'WF', NOW(), NOW());

-- 复制 W_FLOW_VARS 的角色绑定（同样的角色集合 + 同 SYS_CODE）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT UPPER(REPLACE(UUID(), '-', '')), ROLE_ID, 'W_FLOW_AVARS', SYS_CODE, NOW()
FROM PT_ROLE_RESOURCE
WHERE RESOURCE_ID = 'W_FLOW_VARS';
