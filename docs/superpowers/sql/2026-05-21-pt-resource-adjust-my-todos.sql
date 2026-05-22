-- 业绩调整 - 我的待审批 list 接口登记
-- 配合 GET /api/perf/adjusts/my-todos，鉴权沿用 PERF_CONFIG/LIST（与 AllocAdjustController 一致）
-- 部署时机：lf yiti 后端部署前手工 mysql 跑（否则鉴权拦截器找不到资源会 403）

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME,
                         BIZ_TYPE, BIZ_ACTION, IS_MENU, RECORD_STATUS, SYS_CODE,
                         CREATE_TIME, UPDATE_TIME)
VALUES ('R_PERF_ADJ_TODO_LIST', '/api/perf/adjusts/my-todos', 'GET', '业绩调整-我的待审批',
        'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE UPDATE_TIME = NOW();

-- 绑给现有审批角色（按实际角色码调，sys_admin 通常自动）
INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_TODO_LIST'
  FROM PT_ROLE r
 WHERE r.ROLE_CODE IN ('SYS_ADMIN', 'ADJUST_APPROVER', 'BIZ_DEPT_MANAGER')
   AND r.RECORD_STATUS = 0;
