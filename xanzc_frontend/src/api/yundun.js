import { API_BASE, call } from './http';

const endpoint = kind => kind === 'credit'
  ? '/yundun/credit-violations'
  : '/yundun/accountability-violations';

export function listViolations(kind, params = {}) {
  return call('get', endpoint(kind), { params }, { records: [], total: 0 });
}

export function getViolation(kind, id) {
  return call('get', `${endpoint(kind)}/${id}`, {}, {});
}

export function createViolation(kind, data) {
  return call('post', endpoint(kind), { data });
}

export function updateViolation(kind, id, data) {
  return call('put', `${endpoint(kind)}/${id}`, { data });
}

export function batchDeleteViolations(kind, ids, reason) {
  return call('post', `${endpoint(kind)}/batch-delete`, { data: { ids, reason } });
}

export function importViolations(kind, file, reason) {
  const data = new FormData();
  data.append('file', file);
  data.append('reason', reason);
  return call('post', `${endpoint(kind)}/import`, { data });
}

export function downloadViolationImportTemplate(kind) {
  return call('get', `${endpoint(kind)}/import-template`, { responseType: 'blob' }, null);
}

export async function exportViolations(kind, params, filename) {
  const blob = await call('get', `${endpoint(kind)}/export`, {
    params,
    responseType: 'blob',
    paramsSerializer: { indexes: null }
  });
  if (!blob) return;
  const data = blob instanceof Blob ? blob : new Blob([blob]);
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  const stamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  link.href = url;
  link.download = `${filename}_${stamp}.xlsx`;
  document.body.appendChild(link);
  link.click();
  setTimeout(() => { URL.revokeObjectURL(url); link.remove(); }, 0);
}

export const yundunApiBase = API_BASE;
