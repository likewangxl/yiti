import { call, unwrapPage } from './http';

/**
 * 资产立项申请 API。
 *
 * 业务申请页只在创建草稿后上传附件，避免文件对象脱离业务单形成孤儿关联。
 * 所有写接口不提供 mock fallback，真实请求失败必须让调用方感知。
 */
export async function listLoanApplications(params = {}) {
  const {
    keyword,
    status,
    ownerOrgId,
    pageNo = 1,
    pageSize = 20
  } = params;
  const query = { pageNo, pageSize };
  if (keyword) query.keyword = keyword;
  if (status) query.status = status;
  if (ownerOrgId) query.ownerOrgId = ownerOrgId;
  const result = await call('get', '/loans', {
    params: query
  });
  const page = unwrapPage(result);
  if (Array.isArray(page)) return { records: page.map(toLoanView), total: page.length };
  if (!page) return { records: [], total: 0 };
  return { ...page, records: Array.isArray(page.records) ? page.records.map(toLoanView) : [] };
}

export async function getLoanApplication(id) {
  if (!id) return Promise.resolve(null);
  return toLoanView(await call('get', `/loans/${encodeURIComponent(id)}`));
}

export async function createLoanApplication(data) {
  return toLoanView(await call('post', '/loans', { data: toLoanPayload(data) }));
}

export async function updateLoanApplication(id, data) {
  return toLoanView(await call('put', `/loans/${encodeURIComponent(id)}`, { data: toLoanPayload(data) }));
}

export function submitLoanApplication(id, { confirmParallel = false } = {}) {
  const config = { data: {} };
  if (confirmParallel) config.headers = { 'X-Confirm-Parallel': 'true' };
  return call('post', `/loans/${encodeURIComponent(id)}/submit`, config);
}

export function deleteLoanApplication(id) {
  return call('delete', `/loans/${encodeURIComponent(id)}`, {});
}

/**
 * 撤回申请的审计理由按后端 CancelLoanReq 契约进入 JSON body。
 */
export function cancelLoanApplication(id, reason) {
  const value = String(reason || '').trim();
  return call('post', `/loans/${encodeURIComponent(id)}/cancel`, {
    data: { reason: value }
  });
}

export function listLoanAttachments(id) {
  return call('get', '/files', { params: { bizType: 'LOAN', bizId: id } });
}

/**
 * 资产立项客户候选。不使用全局客户页的 mock fallback，避免后端异常时
 * 把演示客户 ID 当成真实主档提交。资格仍由资产立项服务端二次校验。
 */
export async function listLoanCustomerCandidates(params = {}) {
  const result = unwrapPage(await call('get', '/customers', { params }));
  const rows = Array.isArray(result) ? result : (result?.records || []);
  return rows.map(customer => ({
    ...customer,
    id: customer.id || customer.custId || customer.custNo,
    name: customer.custName || customer.name || customer.customerName || customer.id
  }));
}

export function uploadLoanAttachment(file, loanId) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/files/upload', {
    data,
    params: { bizType: 'LOAN', bizId: loanId },
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

// 兼容业务页中已有的简短命名，统一指向同一份真实契约。
export const listLoans = listLoanApplications;
export const getLoan = getLoanApplication;
export const createLoan = createLoanApplication;
export const updateLoan = updateLoanApplication;
export const submitLoan = submitLoanApplication;
export const deleteLoan = deleteLoanApplication;
export const cancelLoan = cancelLoanApplication;
export const listLoanFiles = listLoanAttachments;

const AMOUNT_FIELDS = ['creditAmount', 'creditExposureAmount'];

/** 页面金额以万元输入/展示，贷款 REST 契约以元传输。 */
export function wanToYuan(value) {
  if (value === null || value === undefined || value === '') return value;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number * 10000).toFixed(4)) : value;
}

export function yuanToWan(value) {
  if (value === null || value === undefined || value === '') return value;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number / 10000).toFixed(4)) : value;
}

function toLoanPayload(data = {}) {
  const payload = { ...data };
  AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(payload, field)) payload[field] = wanToYuan(payload[field]);
  });
  return payload;
}

function toLoanView(loan) {
  if (!loan || typeof loan !== 'object') return loan;
  const view = { ...loan };
  AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(view, field)) view[field] = yuanToWan(view[field]);
  });
  return view;
}
