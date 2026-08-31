// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';
import { menuItems, RED_ENGINE_RESOURCE_URLS } from '../layout/canSee.js';

describe('红色引擎路由入口', () => {
  it('注册独立公开登录路由，业务路由继续使用红色引擎布局', async () => {
    const { default: router } = await import('@/router');

    const login = router.getRoutes().find((route) => route.name === 'RedEngineLogin');
    expect(login).toBeTruthy();
    expect(login.path).toBe('/redengine/login');
    expect(login.meta.public).toBe(true);
    expect((await login.components.default()).default).toBeTruthy();

    const dashboard = router.getRoutes().find((route) => route.name === 'RedEngineDashboard');
    expect(dashboard).toBeTruthy();
    expect(dashboard.path).toBe('/redengine/dashboard');
    expect(dashboard.meta.public).not.toBe(true);
  });

  it('注册 no-access，并为红色引擎敏感路由声明资源要求', async () => {
    const { default: router } = await import('@/router');
    const noAccess = router.getRoutes().find((route) => route.name === 'NoAccess');
    expect(noAccess).toBeTruthy();
    expect(noAccess.path).toBe('/no-access');

    const expected = {
      RedEngineReport: [RED_ENGINE_RESOURCE_URLS.REPORT, ['R_RE_REPORT', 'SYS_ADMIN']],
      RedEngineRecords: [RED_ENGINE_RESOURCE_URLS.RECORDS, ['R_RE_REPORT', 'SYS_ADMIN']],
      RedEngineBranchReview: [RED_ENGINE_RESOURCE_URLS.REVIEW, ['R_RE_SECR']],
      RedEngineCockpit: [RED_ENGINE_RESOURCE_URLS.COCKPIT, ['R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineWarning: [RED_ENGINE_RESOURCE_URLS.HOME, ['R_RE_REPORT', 'R_RE_SECR', 'R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineReview: [RED_ENGINE_RESOURCE_URLS.REVIEW, ['R_RE_ORGREV']],
      RedEngineTaskManagement: [RED_ENGINE_RESOURCE_URLS.TASK_COLLECTION, ['R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineTaskNew: [RED_ENGINE_RESOURCE_URLS.TASK_COLLECTION, ['R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineTaskDetail: [RED_ENGINE_RESOURCE_URLS.TASK_DETAIL, ['R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineTaskEntry: [RED_ENGINE_RESOURCE_URLS.TASK_ASSIGNMENT, ['R_RE_REPORT']],
      RedEngineOrgManage: [RED_ENGINE_RESOURCE_URLS.ORG_TREE, ['R_RE_ORGREV', 'SYS_ADMIN']],
      RedEngineUserMap: [RED_ENGINE_RESOURCE_URLS.USER_MAP, ['R_RE_ORGREV', 'SYS_ADMIN']]
    };
    for (const [name, [requiredResource, requiredRoleCodes]] of Object.entries(expected)) {
      const route = router.getRoutes().find((item) => item.name === name);
      expect(route?.meta.requiredResource, name).toBe(requiredResource);
      expect(route?.meta.requiredRoleCodes, name).toEqual(requiredRoleCodes);
    }

    const dashboard = router.getRoutes().find((item) => item.name === 'RedEngineDashboard');
    expect(dashboard?.meta.requiredRoleCodes).toEqual([
      'R_RE_REPORT', 'R_RE_SECR', 'R_RE_ORGREV', 'SYS_ADMIN'
    ]);
    expect(router.getRoutes().some((item) => item.name === 'RedEngineArchive')).toBe(false);
    expect(router.getRoutes().some((item) => item.name === 'RedEngineExport')).toBe(false);
    expect(router.resolve('/redengine/archive').matched).toHaveLength(0);
    expect(router.resolve('/redengine/export').matched).toHaveLength(0);
    expect(router.resolve('/redengine/archive/2026').matched).toHaveLength(0);
    expect(router.resolve('/redengine/export/submit').matched).toHaveLength(0);

    const taskManagement = router.getRoutes().find((item) => item.name === 'RedEngineTaskManagement');
    const taskNew = router.getRoutes().find((item) => item.name === 'RedEngineTaskNew');
    const taskDetail = router.getRoutes().find((item) => item.name === 'RedEngineTaskDetail');
    const taskEntry = router.getRoutes().find((item) => item.name === 'RedEngineTaskEntry');
    expect(taskManagement?.path).toBe('/redengine/task-management');
    expect(taskNew?.path).toBe('/redengine/task-management/new');
    expect(taskDetail?.path).toBe('/redengine/task-management/:taskId');
    expect(taskEntry?.path).toBe('/redengine/task-entry');
    expect(taskNew?.meta.hideInMenu).toBe(true);
    expect(taskDetail?.meta.hideInMenu).toBe(true);
    expect(taskEntry?.meta.hideInMenu).toBe(true);

    const menuByPath = new Map(menuItems.map((item) => [item.path, item]));
    const routeToMenuPath = {
      RedEngineReport: '/redengine/report',
      RedEngineRecords: '/redengine/records',
      RedEngineBranchReview: '/redengine/branch-review',
      RedEngineCockpit: '/redengine/cockpit',
      RedEngineWarning: '/redengine/warning',
      RedEngineReview: '/redengine/review',
      RedEngineTaskManagement: '/redengine/task-management',
      RedEngineOrgManage: '/redengine/org-manage',
      RedEngineUserMap: '/redengine/user-map'
    };
    for (const [routeName, menuPath] of Object.entries(routeToMenuPath)) {
      const route = router.getRoutes().find((item) => item.name === routeName);
      expect(menuByPath.get(menuPath)?.res, `${routeName} menu resource`)
        .toBe(route?.meta.requiredResource);
    }

    expect(RED_ENGINE_RESOURCE_URLS.HOME).toBe('/api/re/home/**');
    expect(RED_ENGINE_RESOURCE_URLS.TASK_DETAIL).toBe('/api/re/tasks/*');
    expect(RED_ENGINE_RESOURCE_URLS.TASK_ASSIGNMENT).toBe('/api/re/tasks/assignments/*');
    expect(router.resolve('/redengine/task-management/new').name).toBe('RedEngineTaskNew');
    expect(router.resolve('/redengine/task-management/42').name).toBe('RedEngineTaskDetail');
    expect(router.resolve('/redengine/task-entry?assignmentId=42').name).toBe('RedEngineTaskEntry');

    const workspace = router.getRoutes().find((route) => route.name === 'Workspace');
    expect(workspace?.meta.requiredMenu).toBe('/workspace');
    expect(workspace?.meta.fallbackToAuthorizedMenu).toBe(true);
  });

  it('直接访问组织审核员专属工作台时按角色 fail-close，系统管理员也不越权', async () => {
    const { default: router, checkRouteRoleAccess } = await import('@/router');
    const route = router.resolve('/redengine/review');

    expect(checkRouteRoleAccess(route, { roles: ['R_RE_REPORT'], isSystemAdmin: false }))
      .toEqual({ path: '/no-access' });
    expect(checkRouteRoleAccess(route, { roles: ['SYS_ADMIN'], isSystemAdmin: true }))
      .toEqual({ path: '/no-access' });
    expect(checkRouteRoleAccess(route, { roles: [{ roleCode: 'R_RE_ORGREV' }], isSystemAdmin: false }))
      .toBe(true);
  });
});
