// 历史数据查询 API —— 数据导入查询 + 公告查询（对接 report 模块只读接口）
import { call } from './http';

// === 数据导入查询 ===
// GET /api/reports/data-imports —— 批次列表（分页 + 查询项）
export function listDataImports(params = {}) {
  return call('get', '/reports/data-imports', { params }, { records: [], total: 0 });
}

// GET /api/reports/data-imports/{batchNum} —— 某批次透视数据（按逻辑行分页）
//   返回 { batchNum, dataName, columns:[{key,label}], rows:[{...}], total }
export function getDataImportData(batchNum, params = {}) {
  return call('get', `/reports/data-imports/${encodeURIComponent(batchNum)}`, { params },
    { columns: [], rows: [], total: 0 });
}

// 导出整个批次为 Excel（GET /api/reports/data-imports/{batchNum}/export，blob 触发下载）
export function exportDataImport(batchNum, fallbackName) {
  return downloadBlob(`/api/reports/data-imports/${encodeURIComponent(batchNum)}/export`, fallbackName);
}

// === 公告查询 ===
export function listNotices(params = {}) {
  return call('get', '/reports/notices', { params }, { records: [], total: 0 });
}
export function getNotice(id) {
  return call('get', `/reports/notices/${encodeURIComponent(id)}`, {}, {});
}

// 下载公告附件（GET /api/reports/notices/{id}/attachment，blob 触发下载）
export function downloadNoticeAttachment(noticId, fallbackName) {
  return downloadBlob(`/api/reports/notices/${encodeURIComponent(noticId)}/attachment`, fallbackName);
}

// === 通用 blob 下载（绕过响应拦截器拿原始流 + 解析文件名）===
async function downloadBlob(url, fallbackName = 'download') {
  const { default: axios } = await import('axios');
  const resp = await axios.get(url, { responseType: 'blob', withCredentials: true });
  // 从 Content-Disposition 解析文件名（filename* / filename，含 URL 编码）
  let fileName = fallbackName;
  const cd = resp.headers?.['content-disposition'] || resp.headers?.['Content-Disposition'] || '';
  const m = /filename\*?=(?:UTF-8'')?["']?([^;"']+)/i.exec(cd);
  if (m && m[1]) {
    try { fileName = decodeURIComponent(m[1]); } catch { fileName = m[1]; }
  }
  const blob = resp.data instanceof Blob ? resp.data : new Blob([resp.data]);
  const href = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(href);
}
