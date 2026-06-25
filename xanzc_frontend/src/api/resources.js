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

// 兜底置空：后端不返回数据 = 空，不再显示假菜单/资源树
const mockResourceTree = [];

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

// === 分配角色（资源维度）===
// GET /api/admin/resources/{resourceId}/roles → List<String> 已绑角色ID
export function getResourceRoles(resourceId) {
  return call('get', `/admin/resources/${encodeURIComponent(resourceId)}/roles`, {}, []);
}
// PUT /api/admin/resources/{resourceId}/roles → 全量设置该资源绑定角色
export function assignResourceRoles(resourceId, roleIds, reason = '菜单分配角色') {
  return call('put', `/admin/resources/${encodeURIComponent(resourceId)}/roles`,
    { data: { roleIds, reason } }, { ok: true });
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
