// 红色引擎侧边栏菜单数据源 + 可见性判断（纯函数模块）。
// Task 15 从 RedEngineLayout.vue 抽出（重构，非行为变更）：原逻辑 100% 保留，
// 唯一目的是让 canSee() 与 menuItems 能脱离组件挂载被 Vitest 直接单测
// （见 xanzc_frontend/src/views/redengine/__tests__/RedEngineMenuFilter.spec.js）。
// RedEngineLayout.vue 改为 import { menuItems, canSee } from './canSee'，
// 两处共用同一份数据/逻辑，避免测试数据与生产菜单数据漂移。

// 菜单数据源（源 dynamicRoutes meta 翻译而来）：每项带 res 字段（资源 URL），
// res=null 表示无需鉴权（工作台默认可见）；res 以 /** 或 /* 结尾表示一组接口共用同一菜单项，做前缀匹配。
// Task 15 新增两项：党组织管理(org-manage，Task 13 起已在数组中但路由此前未接，本次补齐路由)、
// 用户党组织映射(user-map，本次新增，res 与种子 P_RE_MAP_LIST 精确匹配)。
export const menuItems = [
  { path: '/redengine/dashboard', title: '工作台', icon: 'HomeFilled', res: null },
  { path: '/redengine/report', title: '四大维度材料上报', icon: 'EditPen', res: '/api/re/submits' },
  { path: '/redengine/records', title: '上报记录', icon: 'Document', res: '/api/re/submits/my' },
  { path: '/redengine/branch-review', title: '支部审核工作台', icon: 'Stamp', res: '/api/re/reviews/**' },
  { path: '/redengine/cockpit', title: '全局数据驾驶舱', icon: 'DataAnalysis', res: '/api/re/cockpit/**' },
  { path: '/redengine/warning', title: '红黄牌预警池', icon: 'WarningFilled', res: '/api/re/cockpit/**' },
  { path: '/redengine/review', title: '沉浸式审核工作台', icon: 'Checked', res: '/api/re/reviews/**' },
  { path: '/redengine/archive', title: '年度考核归档', icon: 'Trophy', res: '/api/re/cockpit/**' },
  { path: '/redengine/export', title: '数据导出', icon: 'Download', res: '/api/re/export/*' },
  { path: '/redengine/org-manage', title: '党组织管理', icon: 'Setting', res: '/api/re/orgs' },
  // Task 15 新增：用户党组织映射管理页；res 与种子 P_RE_MAP_LIST（GET /api/re/user-party-maps）
  // 字面量精确一致，canSee() 走精确匹配分支命中。
  { path: '/redengine/user-map', title: '用户党组织映射', icon: 'Connection', res: '/api/re/user-party-maps' }
];

/**
 * 判断某菜单项/子视图内某资源对当前用户是否可见。
 * - item.res 为空 → 恒可见（工作台）
 * - resourceUrls 未就绪(null/undefined) → 降级全显示（GET /api/auth/permissions 拉取中或失败）
 * - item.res 以 /** 或 /* 结尾 → 前缀匹配（resourceUrls 中是否存在以该前缀开头的条目）
 * - 否则精确匹配 resourceUrls 集合
 *
 * @param {{res?: string|null}} item 菜单项（或任意携带 res 字段的对象）
 * @param {Set<string>|null|undefined} resourceUrls 当前用户可访问的资源 URL 集合
 * @returns {boolean}
 */
export function canSee(item, resourceUrls) {
  if (!item || !item.res) return true;
  if (!resourceUrls) return true;
  const res = item.res;
  if (res.endsWith('/**') || res.endsWith('/*')) {
    const prefix = res.replace(/\/\*+$/, '');
    for (const u of resourceUrls) {
      if (u.startsWith(prefix)) return true;
    }
    return false;
  }
  return resourceUrls.has(res);
}
