// 资源/菜单 API —— 对接 yiti `/api/admin/resources`（ResourceController）
//
// 后端约定：菜单与 REST 接口资源合并在 PT_RESOURCE 同一张表，
// 通过 isMenu 区分（0=菜单, 1=普通资源）。
//
// ResourceTreeNodeDTO 字段：
//   resourceId / resourceUrl / resourceMethod / menuName
//   isMenu      (0=菜单, 1=非菜单)
//   menuEndFlag ("0"=非叶子, "1"=叶子)
//   menuRankNo  (排序)
//   status      (0=启用, 1=停用)
//   children    (List<ResourceTreeNodeDTO>)

import { call } from './http';

// === Mock 兜底数据 ===
const mockResourceTree = [
  {
    resourceId: 'M-WORKSPACE', menuName: '工作台', resourceUrl: '/workspace', resourceMethod: 'GET',
    isMenu: 0, menuEndFlag: '1', menuRankNo: 1, status: 0, children: []
  },
  {
    resourceId: 'M-PERF', menuName: '绩效与考核', resourceUrl: '/perf', resourceMethod: 'GET',
    isMenu: 0, menuEndFlag: '0', menuRankNo: 2, status: 0,
    children: [
      { resourceId: 'M-PERF-METRICS',  menuName: '指标库',  resourceUrl: '/perf/metrics',   resourceMethod: 'GET', isMenu: 0, menuEndFlag: '1', menuRankNo: 1, status: 0, children: [] },
      { resourceId: 'M-PERF-KPI',      menuName: 'KPI规则', resourceUrl: '/perf/kpi-rules', resourceMethod: 'GET', isMenu: 0, menuEndFlag: '1', menuRankNo: 2, status: 0, children: [] },
      { resourceId: 'M-PERF-TARGET',   menuName: '目标管理', resourceUrl: '/perf/targets',  resourceMethod: 'GET', isMenu: 0, menuEndFlag: '1', menuRankNo: 3, status: 0, children: [] }
    ]
  },
  {
    resourceId: 'M-SYS', menuName: '系统设置', resourceUrl: '/system', resourceMethod: 'GET',
    isMenu: 0, menuEndFlag: '0', menuRankNo: 9, status: 0,
    children: [
      { resourceId: 'M-SYS-USER', menuName: '用户管理', resourceUrl: '/system/users', resourceMethod: 'GET', isMenu: 0, menuEndFlag: '1', menuRankNo: 1, status: 0, children: [] },
      { resourceId: 'M-SYS-ROLE', menuName: '角色管理', resourceUrl: '/system/roles', resourceMethod: 'GET', isMenu: 0, menuEndFlag: '1', menuRankNo: 2, status: 0, children: [] }
    ]
  },
  {
    resourceId: 'R-PERF-API', menuName: '绩效接口', resourceUrl: '/api/perf/**', resourceMethod: '*',
    isMenu: 1, menuEndFlag: '1', menuRankNo: 100, status: 0, children: []
  }
];

// 获取资源/菜单树
//   params: { status?, sysCode? }
export async function listResourceTree(params = {}) {
  const r = await call('get', '/admin/resources/tree', { params }, mockResourceTree);
  return Array.isArray(r) ? r : [];
}

// 新增节点
//   data: { resourceUrl, resourceMethod, menuName, isMenu, menuEndFlag?, menuRankNo?, parentResourceId?, sysCode? }
export function createResource(data) {
  return call('post', '/admin/resources', { data }, { ok: true });
}

// 修改节点
export function updateResource(resourceId, data) {
  return call('put', `/admin/resources/${resourceId}`, { data }, { ok: true });
}

// 逻辑删除；reason 必填
export function deleteResource(resourceId, reason = '前端删除') {
  return call('delete', `/admin/resources/${resourceId}`, { params: { reason } }, { ok: true });
}

// === 常量 ===
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', '*'];
export const IS_MENU_OPTIONS = [
  { value: 0, label: '菜单' },
  { value: 1, label: '接口资源' }
];
export const END_FLAG_OPTIONS = [
  { value: '0', label: '父节点（可包含子节点）' },
  { value: '1', label: '叶子节点' }
];
