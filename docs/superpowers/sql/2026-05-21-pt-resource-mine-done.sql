-- 业绩调整 - 我的申请 / 已审批 端点登记
-- 配合 lf yiti GET /api/perf/alloc-adjust/my-applies + /my-done
-- 部署前手工 mysql 跑（否则鉴权拦截器 403）

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME,
                         BIZ_TYPE, BIZ_ACTION, IS_MENU, RECORD_STATUS, SYS_CODE,
                         CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_PERF_ADJ_MINE_LIST', '/api/perf/alloc-adjust/my-applies', 'GET', '业绩调整-我的申请',
   'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW()),
  ('R_PERF_ADJ_DONE_LIST', '/api/perf/alloc-adjust/my-done', 'GET', '业绩调整-已审批',
   'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW())
ON DUPLICATE KEY UPDATE UPDATE_TIME = NOW();

-- 绑给角色：
-- mine 任何登录用户都该能看自己（绑全部启用角色）
INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_MINE_LIST'
  FROM PT_ROLE r WHERE r.RECORD_STATUS = 0;

-- done 限审批角色（同 V1 todo）
INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_DONE_LIST'
  FROM PT_ROLE r
 WHERE r.ROLE_CODE IN ('SYS_ADMIN', 'ADJUST_APPROVER', 'BIZ_DEPT_MANAGER')
   AND r.RECORD_STATUS = 0;
