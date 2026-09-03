// @vitest-environment happy-dom
// Task 15 TDD Step 1（RED）：红色引擎侧边栏菜单过滤(canSee)回归测试。
// 契约断言：resourceUrls 只含 '/api/re/submits/my' 时，canSee 只放行"工作台"(res=null 恒可见)
// 与"任务处理"(res 精确匹配 '/api/re/submits/my')；角色矩阵再决定各角色的菜单集合。
//
// canSee/menuItems 从 RedEngineLayout.vue 抽成 ../layout/canSee.js 纯函数模块（Task 15 重构，
// 非行为变更，Layout 改为 import 该模块），使菜单过滤逻辑可脱离组件挂载做纯函数单测；
// 两处共用同一份 menuItems，避免测试数据与生产菜单数据漂移。
import { describe, it, expect } from 'vitest';
import { menuItems, canSee, RED_ENGINE_RESOURCE_URLS } from '../layout/canSee.js';

describe('红色引擎侧边栏菜单过滤(canSee)', () => {
  it('resourceUrls 只含 /api/re/submits/my 时，只放行 工作台 + 任务处理', () => {
    const resourceUrls = new Set(['/api/re/submits/my']);
    const visibleTitles = menuItems.filter((item) => canSee(item, resourceUrls)).map((item) => item.title);
    expect(visibleTitles).toEqual(['工作台', '任务处理']);
  });

  it('resourceUrls 为 null（拉取中/失败）时仅放行无需鉴权的工作台', () => {
    const visibleTitles = menuItems.filter((item) => canSee(item, null)).map((item) => item.title);
    expect(visibleTitles).toEqual(['工作台']);
  });

  it('通配资源按路径边界匹配，不把相似前缀当成已授权', () => {
    const reviewItem = menuItems.find((item) => item.res === '/api/re/reviews/**');
    expect(canSee(reviewItem, new Set(['/api/re/reviews']))).toBe(true);
    expect(canSee(reviewItem, new Set(['/api/re/reviews/42']))).toBe(true);
    expect(canSee(reviewItem, new Set(['/api/re/reviewshop']))).toBe(false);
  });

  it('菜单数据源已包含"用户党组织映射"入口，res 精确指向 /api/re/user-party-maps', () => {
    const item = menuItems.find((m) => m.path === '/redengine/user-map');
    expect(item).toBeTruthy();
    expect(item.res).toBe(RED_ENGINE_RESOURCE_URLS.USER_MAP);
  });

  it('按已确认角色矩阵过滤菜单，不再展示归档/导出入口', () => {
    const allResources = new Set([
      '/api/re/submits',
      '/api/re/submits/my',
      '/api/re/reviews',
      '/api/re/cockpit',
      '/api/re/home',
      '/api/re/tasks',
      '/api/re/orgs/tree',
      '/api/re/user-party-maps'
    ]);
    const visiblePaths = (roles) => menuItems
      .filter((item) => canSee(item, allResources, roles))
      .map((item) => item.path);

    expect(visiblePaths(['R_RE_REPORT'])).toEqual([
      '/redengine/dashboard',
      '/redengine/report',
      '/redengine/records',
      '/redengine/warning'
    ]);
    expect(visiblePaths(['R_RE_SECR'])).toEqual([
      '/redengine/dashboard',
      '/redengine/branch-review',
      '/redengine/warning'
    ]);
    expect(visiblePaths(['R_RE_ORGREV'])).toEqual([
      '/redengine/dashboard',
      '/redengine/cockpit',
      '/redengine/warning',
      '/redengine/review',
      '/redengine/task-management',
      '/redengine/org-manage',
      '/redengine/user-map'
    ]);
    expect(visiblePaths(['SYS_ADMIN'])).toEqual([
      '/redengine/dashboard',
      '/redengine/report',
      '/redengine/records',
      '/redengine/cockpit',
      '/redengine/warning',
      '/redengine/task-management',
      '/redengine/org-manage',
      '/redengine/user-map'
    ]);
    expect(visiblePaths(['BRANCH_REVIEWER'])).toEqual([]);
    expect(menuItems.some((item) => item.path === '/redengine/archive')).toBe(false);
    expect(menuItems.some((item) => item.path === '/redengine/export')).toBe(false);
  });

  it('组织审核员的菜单文字使用“工作台”，且任务管理使用独立入口', () => {
    expect(menuItems.find((item) => item.path === '/redengine/review')?.title).toBe('工作台');
    expect(menuItems.find((item) => item.path === '/redengine/task-management')?.title).toBe('任务管理');
  });

  it('预警与驾驶舱使用各自 Controller 的资源根路径', () => {
    expect(menuItems.find((item) => item.path === '/redengine/warning')?.res)
      .toBe(RED_ENGINE_RESOURCE_URLS.HOME);
    expect(menuItems.find((item) => item.path === '/redengine/cockpit')?.res)
      .toBe(RED_ENGINE_RESOURCE_URLS.COCKPIT);
  });
});
