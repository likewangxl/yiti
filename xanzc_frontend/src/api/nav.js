// 网址导航 API —— 对接 yiti NavController(/api/nav) + AdminNavController(/api/admin/nav)
import { call } from './http';

// GET /api/nav —— 分组列表 { groups: [{ category, navs: [{id,navName,navUrl,navIcon,navCategory,sortOrder,status}] }] }
// params.status='ALL' 返回含禁用项（管理页用）；默认只返回 ACTIVE。
export function listNav(params = {}) {
  return call('get', '/nav', { params }, { groups: [] });
}

// POST /api/admin/nav —— 新增（NavCreateReqDTO: navName/navUrl/navIcon/navCategory/sortOrder）
export function createNav(data) {
  return call('post', '/admin/nav', { data }, { ok: true });
}

// PUT /api/admin/nav/{id} —— 编辑（NavUpdateReqDTO: +status）
export function updateNav(id, data) {
  return call('put', `/admin/nav/${id}`, { data }, { ok: true });
}

// DELETE /api/admin/nav/{id}
export function deleteNav(id) {
  return call('delete', `/admin/nav/${id}`, {}, { ok: true });
}

// PUT /api/admin/nav/sort —— 批量排序 [{id, sortOrder}]
export function sortNav(items) {
  return call('put', '/admin/nav/sort', { data: items }, { ok: true });
}
