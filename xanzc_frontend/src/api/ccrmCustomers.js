import { call } from './http';

// CCRM 客户源是独立的数据提供源：不复用客户主档、线索、触达或 M98 API。
// 所有写操作不传 fallback，避免后端失败时伪装成前端 mock 成功。

export function listCcrmCustomers(params = {}) {
  return call('get', '/ccrm/customers', { params }, null);
}

export function getCcrmCustomer(id) {
  return call('get', `/ccrm/customers/${encodeURIComponent(id)}`, {}, null);
}

// 供线索录入页面查询历史客户与当前主办权；只读有效且完整的可采用记录由后端保证。
export function lookupCcrmCustomer(params = {}) {
  return call('get', '/ccrm/customers/lookup', { params }, null);
}

export function createCcrmCustomer(data) {
  return call('post', '/ccrm/customers', { data });
}

export function updateCcrmCustomer(id, data) {
  return call('put', `/ccrm/customers/${encodeURIComponent(id)}`, { data });
}

export function deleteCcrmCustomer(id, payload = {}) {
  const data = typeof payload === 'string' ? { reason: payload } : payload;
  return call('delete', `/ccrm/customers/${encodeURIComponent(id)}`, { data });
}

export function downloadCcrmImportTemplate() {
  return call('get', '/ccrm/customers/import-template', { responseType: 'blob' }, null);
}

export function previewCcrmImport(file) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/ccrm/customers/import/preview', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function executeCcrmImport(batchId) {
  return call('post', '/ccrm/customers/import/execute', { data: { batchId } });
}

export function exportCcrmCustomers(params = {}) {
  return call('get', '/ccrm/customers/export', { params, responseType: 'blob' }, null);
}
