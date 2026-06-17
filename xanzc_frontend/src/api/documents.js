// 常用文档 API —— 对接 yiti DocController(/api/documents) + AdminDocController(/api/admin/documents)
import { call } from './http';

// GET /api/documents —— 分页（keyword/category/status/pageNo/pageSize）
// 返回 PageResult<DocumentDTO>(id/docTitle/docCategory/docCategoryDesc/fileObjectId/fileName/status/updatedTime)
export function listDocuments(params = {}) {
  return call('get', '/documents', { params }, { records: [], total: 0 });
}

// GET /api/documents/{id}/download —— 返回 OBS 预签名下载 URL（字符串）
export function getDocumentDownloadUrl(id) {
  return call('get', `/documents/${id}/download`, {}, null);
}

// POST /api/admin/documents —— 上传/新增（DocumentCreateReqDTO: docTitle/docCategory/fileObjectId）
export function createDocument(data) {
  return call('post', '/admin/documents', { data }, { ok: true });
}

// PUT /api/admin/documents/{id} —— 编辑（DocumentUpdateReqDTO: +status）
export function updateDocument(id, data) {
  return call('put', `/admin/documents/${id}`, { data }, { ok: true });
}

// DELETE /api/admin/documents/{id}
export function deleteDocument(id) {
  return call('delete', `/admin/documents/${id}`, {}, { ok: true });
}

// 触发浏览器下载：拿到预签名 URL 后新开窗口
export async function downloadDocument(id) {
  const url = await getDocumentDownloadUrl(id);
  if (url && typeof url === 'string') {
    window.open(url, '_blank');
  }
  return url;
}
