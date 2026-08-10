// @vitest-environment happy-dom
// Task 15 TDD Step 1（RED）：红色引擎侧边栏菜单过滤(canSee)回归测试。
// 简报断言：resourceUrls 只含 '/api/re/submits/my' 时，canSee 只放行"工作台"(res=null 恒可见)
// 与"上报记录"(res 精确匹配 '/api/re/submits/my')，其余(含四大维度材料上报/审核工作台/驾驶舱/
// 预警池/年度归档/数据导出/党组织管理/用户党组织映射)均应被隐藏。
//
// canSee/menuItems 从 RedEngineLayout.vue 抽成 ../layout/canSee.js 纯函数模块（Task 15 重构，
// 非行为变更，Layout 改为 import 该模块），使菜单过滤逻辑可脱离组件挂载做纯函数单测；
// 两处共用同一份 menuItems，避免测试数据与生产菜单数据漂移。
import { describe, it, expect } from 'vitest';
import { menuItems, canSee } from '../layout/canSee.js';

describe('红色引擎侧边栏菜单过滤(canSee)', () => {
  it('resourceUrls 只含 /api/re/submits/my 时，只放行 工作台 + 上报记录', () => {
    const resourceUrls = new Set(['/api/re/submits/my']);
    const visibleTitles = menuItems.filter((item) => canSee(item, resourceUrls)).map((item) => item.title);
    expect(visibleTitles).toEqual(['工作台', '上报记录']);
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
    expect(item.res).toBe('/api/re/user-party-maps');
  });
});
