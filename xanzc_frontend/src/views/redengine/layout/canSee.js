// 红色引擎侧边栏菜单数据源 + 可见性判断（纯函数模块）。
// Task 15 从 RedEngineLayout.vue 抽出，使 canSee() 与 menuItems 能脱离组件挂载被 Vitest 直接单测
// （见 xanzc_frontend/src/views/redengine/__tests__/RedEngineMenuFilter.spec.js）。
// RedEngineLayout.vue 改为 import { menuItems, canSee } from './canSee'，
// 两处共用同一份数据/逻辑，避免测试数据与生产菜单数据漂移。
import { matchesRequiredResource } from '@/utils/resourcePermission';

export const RED_ENGINE_ROLE_CODES = Object.freeze({
  REPORTER: 'R_RE_REPORT',
  SECRETARY: 'R_RE_SECR',
  ORG_REVIEWER: 'R_RE_ORGREV',
  SYSTEM_ADMIN: 'SYS_ADMIN'
});

// 资源 URL 以当前 red-engine-center Controller 的真实 HTTP mapping 为准。
// @BizAuth 只携带 BizType/BizAction，不携带 resourceUrl；资源最终由 PT_RESOURCE 的
// URL + Method 进行匹配。集合/详情/assignment 资源分开声明，避免隐藏入口复用错误资源。
export const RED_ENGINE_RESOURCE_URLS = Object.freeze({
  REPORT: '/api/re/submits',
  RECORDS: '/api/re/submits/my',
  REVIEW: '/api/re/reviews/**',
  COCKPIT: '/api/re/cockpit/**',
  HOME: '/api/re/home/**',
  TASK_COLLECTION: '/api/re/tasks',
  TASK_DETAIL: '/api/re/tasks/*',
  TASK_ASSIGNMENT: '/api/re/tasks/assignments/*',
  ORG_TREE: '/api/re/orgs/tree',
  USER_MAP: '/api/re/user-party-maps'
});

const REPORTER_ROLES = [RED_ENGINE_ROLE_CODES.REPORTER, RED_ENGINE_ROLE_CODES.SYSTEM_ADMIN];
const SECRETARY_ROLES = [RED_ENGINE_ROLE_CODES.SECRETARY];
const ORG_REVIEWER_ROLES = [RED_ENGINE_ROLE_CODES.ORG_REVIEWER, RED_ENGINE_ROLE_CODES.SYSTEM_ADMIN];
const ALL_RED_ENGINE_ROLES = [
  RED_ENGINE_ROLE_CODES.REPORTER,
  RED_ENGINE_ROLE_CODES.SECRETARY,
  RED_ENGINE_ROLE_CODES.ORG_REVIEWER,
  RED_ENGINE_ROLE_CODES.SYSTEM_ADMIN
];

// 菜单数据源（源 dynamicRoutes meta 翻译而来）：每项带 res 字段（资源 URL），
// res=null 表示无需资源鉴权；res 以 /** 或 /* 结尾表示一组接口共用同一菜单项，做前缀匹配。
// allowedRoles 是红色引擎页面的角色白名单，和资源权限叠加生效；未登录或未知角色不放行。
export const menuItems = [
  {
    path: '/redengine/dashboard',
    title: '工作台',
    icon: 'HomeFilled',
    res: null,
    allowedRoles: ALL_RED_ENGINE_ROLES
  },
  {
    path: '/redengine/report',
    title: '四大维度材料上报',
    icon: 'EditPen',
    res: RED_ENGINE_RESOURCE_URLS.REPORT,
    allowedRoles: REPORTER_ROLES
  },
  {
    path: '/redengine/records',
    title: '上报信息',
    icon: 'Document',
    res: RED_ENGINE_RESOURCE_URLS.RECORDS,
    allowedRoles: REPORTER_ROLES
  },
  {
    path: '/redengine/branch-review',
    title: '支部审核工作台',
    icon: 'Stamp',
    res: RED_ENGINE_RESOURCE_URLS.REVIEW,
    allowedRoles: SECRETARY_ROLES
  },
  {
    path: '/redengine/cockpit',
    title: '全局数据驾驶舱',
    icon: 'DataAnalysis',
    res: RED_ENGINE_RESOURCE_URLS.COCKPIT,
    allowedRoles: ORG_REVIEWER_ROLES
  },
  {
    path: '/redengine/warning',
    title: '红黄牌预警池',
    icon: 'WarningFilled',
    res: RED_ENGINE_RESOURCE_URLS.HOME,
    allowedRoles: ALL_RED_ENGINE_ROLES
  },
  {
    path: '/redengine/review',
    title: '工作台',
    icon: 'Checked',
    res: RED_ENGINE_RESOURCE_URLS.REVIEW,
    allowedRoles: [RED_ENGINE_ROLE_CODES.ORG_REVIEWER]
  },
  {
    path: '/redengine/task-management',
    title: '任务管理',
    icon: 'List',
    res: RED_ENGINE_RESOURCE_URLS.TASK_COLLECTION,
    allowedRoles: ORG_REVIEWER_ROLES
  },
  {
    path: '/redengine/org-manage',
    title: '党组织管理',
    icon: 'Setting',
    res: RED_ENGINE_RESOURCE_URLS.ORG_TREE,
    allowedRoles: ORG_REVIEWER_ROLES
  },
  {
    path: '/redengine/user-map',
    title: '用户党组织映射',
    icon: 'Connection',
    res: RED_ENGINE_RESOURCE_URLS.USER_MAP,
    allowedRoles: ORG_REVIEWER_ROLES
  }
];

function roleCodeOf(role) {
  if (typeof role === 'string') return role;
  if (!role || typeof role !== 'object') return '';
  return role.roleCode || role.roleId || role.code || '';
}

/**
 * 将平台返回的字符串/角色对象统一为角色编码；支持 Set 以便测试和权限快照直接复用。
 */
export function normalizeRoleCodes(roles, isSystemAdmin = false) {
  const source = roles instanceof Set
    ? [...roles]
    : Array.isArray(roles)
      ? roles
      : roles == null
        ? []
        : [roles];
  const codes = new Set(source.map(roleCodeOf).filter(Boolean));
  if (isSystemAdmin) codes.add(RED_ENGINE_ROLE_CODES.SYSTEM_ADMIN);
  return codes;
}

export function hasAllowedRole(roles, allowedRoles, isSystemAdmin = false) {
  const roleCodes = normalizeRoleCodes(roles, isSystemAdmin);
  return (allowedRoles || []).some((code) => roleCodes.has(code));
}

/**
 * 判断某菜单项/子视图内某资源对当前用户是否可见。
 * - item.allowedRoles 存在时，先按角色白名单过滤，再检查资源权限
 * - item.res 为空 → 资源恒可见（工作台）
 * - resourceUrls 未就绪(null/undefined) → fail-close，仅无资源要求的工作台可见
 * - item.res 以 /** 或 /* 结尾 → 前缀匹配（resourceUrls 中是否存在以该前缀开头的条目）
 * - 否则精确匹配 resourceUrls 集合
 *
 * @param {{res?: string|null}} item 菜单项（或任意携带 res 字段的对象）
 * @param {Set<string>|null|undefined} resourceUrls 当前用户可访问的资源 URL 集合
 * @param {Array<string|object>|Set<string>|string|undefined} roles 当前用户角色；省略时保持资源纯函数兼容行为
 * @param {boolean} isSystemAdmin 用户是否被权限服务标记为系统管理员
 * @returns {boolean}
 */
export function canSee(item, resourceUrls, roles, isSystemAdmin = false) {
  if (!item) return true;
  if (roles !== undefined && item.allowedRoles && !hasAllowedRole(roles, item.allowedRoles, isSystemAdmin)) {
    return false;
  }
  if (!item.res) return true;
  if (isSystemAdmin) return true;
  return matchesRequiredResource(item.res, resourceUrls);
}
