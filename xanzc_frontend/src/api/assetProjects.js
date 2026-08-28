import { call, unwrapPage } from './http';

const BASE = '/marketing/asset-projects';
const AMOUNT_FIELDS = [
  'projectTotalInvestment',
  'projectLoanAmount',
  'creditAmount',
  'creditExposureAmount'
];
const SAVE_FIELDS = [
  'custId',
  'sourceTouchTaskId',
  'sourceWorklogId',
  'projectName',
  'projectType',
  'bizType',
  'guaranteeType',
  ...AMOUNT_FIELDS,
  'urgent',
  'keyProject',
  'lockVersion',
  'attachmentIds'
];

export async function listAssetProjects(params = {}) {
  const page = unwrapPage(await call('get', BASE, { params }));
  if (Array.isArray(page)) return { records: page.map(toView), total: page.length };
  return { ...(page || {}), records: (page?.records || []).map(toView), total: Number(page?.total || 0) };
}

export const getAssetProject = async id => toView(await call('get', `${BASE}/${encodeURIComponent(id)}`));
export const createAssetProject = async data => toView(await call('post', BASE, { data: toPayload(data) }));
export const updateAssetProject = async (id, data) =>
  toView(await call('put', `${BASE}/${encodeURIComponent(id)}`, { data: toPayload(data) }));
export const submitAssetProject = id => call('post', `${BASE}/${encodeURIComponent(id)}/submit`, { data: {} });
export const deleteAssetProject = (id, lockVersion, reason) =>
  call('delete', `${BASE}/${encodeURIComponent(id)}`, {
    params: { lockVersion, reason: String(reason || '').trim() }
  });
export const cancelAssetProject = (id, reason) =>
  call('post', `${BASE}/${encodeURIComponent(id)}/cancel`, { data: { reason: String(reason || '').trim() } });
export const getAssetProjectUrgentContext = id =>
  call('get', `${BASE}/${encodeURIComponent(id)}/urgent-context`);
export const requestAssetProjectUrgent = (id, reason) =>
  call('post', `${BASE}/${encodeURIComponent(id)}/urgent-applies`, { data: { reason: String(reason || '').trim() } });
export const listAssetProjectUrgentApplies = id =>
  call('get', `${BASE}/${encodeURIComponent(id)}/urgent-applies`);

export async function listAssetProjectCustomers(params = {}) {
  const page = unwrapPage(await call('get', '/marketing/customers', { params }));
  return Array.isArray(page) ? page : (page?.records || []);
}

export const getAssetProjectCustomer = id =>
  call('get', `/marketing/customers/${encodeURIComponent(id)}`);

export async function uploadAssetProjectAttachment(file) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/files/upload', { data, headers: { 'Content-Type': 'multipart/form-data' } });
}

export function wanToYuan(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number * 10000).toFixed(2)) : value;
}

export function yuanToWan(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number / 10000).toFixed(2)) : value;
}

function toPayload(source = {}) {
  const payload = Object.fromEntries(
    SAVE_FIELDS
      .filter(field => Object.prototype.hasOwnProperty.call(source, field))
      .map(field => [field, source[field]])
  );
  AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(payload, field)) payload[field] = wanToYuan(payload[field]);
  });
  return payload;
}

function toView(source) {
  if (!source || typeof source !== 'object') return source;
  const view = { ...source };
  AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(view, field)) view[field] = yuanToWan(view[field]);
  });
  return view;
}
