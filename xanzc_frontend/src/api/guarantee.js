// 担保信息查询 API —— 对接 yiti GuaranteeController(/api/guarantee)
import { call } from './http';

// GET /api/guarantee —— 分页（clientName/pageNo/pageSize）
// http 拦截器自动取 body.page → { records, total, pageNo, pageSize }
export function listGuarantees(params = {}) {
  return call('get', '/guarantee', { params }, { records: [], total: 0 });
}

// GET /api/guarantee/{id} —— 详情（编辑反显）
export function getGuarantee(id) {
  return call('get', `/guarantee/${id}`, {}, {});
}

// POST /api/guarantee —— 新增
export function createGuarantee(data) {
  return call('post', '/guarantee', { data }, { ok: true });
}

// PUT /api/guarantee/{id} —— 编辑
export function updateGuarantee(id, data) {
  return call('put', `/guarantee/${id}`, { data }, { ok: true });
}

// POST /api/guarantee/batch-delete —— 批量删除（多选）
export function batchDeleteGuarantees(ids) {
  return call('post', '/guarantee/batch-delete', { data: { ids } }, { ok: true });
}

// GET /api/guarantee/export —— 导出，blob 触发浏览器下载
// params: { clientName?, ids?: number[] }（ids 非空=多选导出，否则按 clientName 全量）
export async function exportGuarantees(params = {}) {
  const blob = await call('get', '/guarantee/export', {
    params,
    responseType: 'blob',
    // ids 数组按重复参数序列化（ids=1&ids=2），与后端 List<Long> 绑定一致
    paramsSerializer: { indexes: null }
  }, null);
  if (!blob) return;
  const data = blob instanceof Blob ? blob : new Blob([blob]);
  const url = URL.createObjectURL(data);
  const a = document.createElement('a');
  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  a.href = url; a.download = `担保信息_${ts}.xlsx`;
  document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 0);
}
