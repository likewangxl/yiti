import { call } from './http';

const idPart = id => encodeURIComponent(String(id));
const pageFallback = { records: [], total: 0 };

// 页面一、二：营销客户列表 / 我的客户
export const listMarketingCustomers = (params = {}) =>
  call('get', '/marketing/customers', { params }, null);
export const listMyCustomers = (params = {}) =>
  call('get', '/marketing/customers/mine', { params }, null);
export const getMarketingCustomer = id =>
  call('get', `/marketing/customers/${idPart(id)}`, {}, null);
export const updateMarketingCustomerProfile = (id, data) =>
  call('put', `/marketing/customers/${idPart(id)}/profile`, { data });
export const transferCustomerOwner = (id, data) =>
  call('post', `/marketing/customers/${idPart(id)}/transfer`, { data });
export const restoreCustomerOwnershipAuto = (id, data) =>
  call('post', `/marketing/customers/${idPart(id)}/ownership/restore-auto`, { data });

// 页面三 Tab一：每条手工线索一行，不聚合
export const listManualLeads = (params = {}) =>
  call('get', '/marketing/leads', { params: { ...params, leadSource: 'MANUAL' } }, null);
export const getMarketingLead = async id =>
  toMarketingLeadView(await call('get', `/marketing/leads/${idPart(id)}`, {}, null));
export const lookupMarketingCustomer = async (params = {}) =>
  toMarketingLeadAmounts(await call('get', '/marketing/leads/lookup', { params }, null));
export const lookupMarketingCustomerByCreditCode = unifiedCreditCode =>
  lookupMarketingCustomer({ unifiedCreditCode });
export const createMarketingLead = data =>
  call('post', '/marketing/leads', { data: toMarketingLeadPayload(data) });
export const updateMarketingLead = (id, data) =>
  call('put', `/marketing/leads/${idPart(id)}`, { data: toMarketingLeadPayload(data) });
export const submitMarketingLead = id =>
  call('post', `/marketing/leads/${idPart(id)}/submit`, { data: {} });
export const cancelMarketingLead = (id, data = {}) =>
  call('post', `/marketing/leads/${idPart(id)}/cancel`, { data });
export const deleteMarketingLeadDraft = id =>
  call('delete', `/marketing/leads/${idPart(id)}`, {});

const MARKETING_LEAD_AMOUNT_FIELDS = ['creditAmount', 'creditExposureAmount'];

/** 页面金额以万元录入，营销线索 REST 契约以元传输。 */
export function marketingLeadWanToYuan(value) {
  if (value === null || value === undefined || value === '') return value;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number * 10000).toFixed(4)) : value;
}

export function marketingLeadYuanToWan(value) {
  if (value === null || value === undefined || value === '') return value;
  const number = Number(value);
  return Number.isFinite(number) ? Number((number / 10000).toFixed(4)) : value;
}

function toMarketingLeadPayload(data = {}) {
  const payload = { ...data };
  MARKETING_LEAD_AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(payload, field)) {
      payload[field] = marketingLeadWanToYuan(payload[field]);
    }
  });
  return payload;
}

function toMarketingLeadAmounts(lead) {
  if (!lead || typeof lead !== 'object') return lead;
  const view = { ...lead };
  MARKETING_LEAD_AMOUNT_FIELDS.forEach(field => {
    if (Object.prototype.hasOwnProperty.call(view, field)) {
      view[field] = marketingLeadYuanToWan(view[field]);
    }
  });
  return view;
}

function toMarketingLeadView(result) {
  if (!result || typeof result !== 'object') return result;
  if (result.lead) return { ...result, lead: toMarketingLeadAmounts(result.lead) };
  return toMarketingLeadAmounts(result);
}

export function uploadMarketingLeadAttachment(file) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/files/upload', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// 页面三 Tab二：批量导入批次与明细
export const listLeadImportBatches = (params = {}) =>
  call('get', '/marketing/lead-import-batches', { params }, null);
export const getLeadImportBatch = id =>
  call('get', `/marketing/lead-import-batches/${idPart(id)}`, {}, null);
export const getLeadImportBatchDetails = (id, params = {}) =>
  call('get', `/marketing/lead-import-batches/${idPart(id)}/details`, { params }, null);
export const previewLeadImportBatch = file => {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/marketing/lead-import-batches/preview', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const createLeadImportBatch = file => {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/marketing/lead-import-batches', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const confirmLeadImportBatch = (id, data) =>
  call('post', `/marketing/lead-import-batches/${idPart(id)}/confirm`, { data });
export const downloadLeadImportSourceFile = id =>
  call('get', `/marketing/lead-import-batches/${idPart(id)}/source-file`, { responseType: 'blob' }, null);
export const downloadLeadImportErrorFile = id =>
  call('get', `/marketing/lead-import-batches/${idPart(id)}/error-file`, { responseType: 'blob' }, null);

// 页面四：线索审批
export const listLeadApprovalPending = (params = {}) =>
  call('get', '/marketing/lead-approvals/pending', { params }, null);
export const listLeadApprovalHistory = (params = {}) =>
  call('get', '/marketing/lead-approvals/history', { params }, null);
export const getLeadApprovalDetail = id =>
  call('get', `/marketing/lead-approvals/${idPart(id)}`, {}, null);
export const approveLead = (id, data) =>
  call('post', `/marketing/lead-approvals/${idPart(id)}/approve`, { data });
export const rejectLead = (id, data) =>
  call('post', `/marketing/lead-approvals/${idPart(id)}/reject`, { data });

// 页面五：营销客户标签
export const listMarketingCustomerTags = (params = {}) =>
  call('get', '/marketing/customer-tags', { params }, pageFallback);
export const getMarketingCustomerTag = id =>
  call('get', `/marketing/customer-tags/${idPart(id)}`, {}, null);
export const createMarketingCustomerTag = data =>
  call('post', '/marketing/customer-tags', { data });
export const updateMarketingCustomerTag = (id, data) =>
  call('put', `/marketing/customer-tags/${idPart(id)}`, { data });
export const listMarketingCustomerTagCustomers = (id, params = {}) =>
  call('get', `/marketing/customer-tags/${idPart(id)}/customers`, { params }, pageFallback);
export const previewCustomerTagImportBatch = ({ tagId, importMode, file }) => {
  const data = new FormData();
  data.append('tagId', String(tagId));
  data.append('importMode', importMode);
  data.append('file', file);
  return call('post', '/marketing/customer-tag-import-batches/preview', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const createCustomerTagImportBatch = ({ tagId, importMode, file }) => {
  const data = new FormData();
  data.append('tagId', String(tagId));
  data.append('importMode', importMode);
  data.append('file', file);
  return call('post', '/marketing/customer-tag-import-batches', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const downloadCustomerTagImportTemplate = () =>
  call('get', '/marketing/customer-tag-import-batches/import-template', { responseType: 'blob' }, null);
export const listCustomerTagImportBatches = (params = {}) =>
  call('get', '/marketing/customer-tag-import-batches', { params }, pageFallback);
export const getCustomerTagImportBatch = id =>
  call('get', `/marketing/customer-tag-import-batches/${idPart(id)}`, {}, null);
export const getCustomerTagImportBatchDetails = (id, params = {}) =>
  call('get', `/marketing/customer-tag-import-batches/${idPart(id)}/details`, { params }, pageFallback);
export const cancelCustomerTagImportBatch = (id, data) =>
  call('post', `/marketing/customer-tag-import-batches/${idPart(id)}/cancel`, { data });
export const downloadCustomerTagImportSourceFile = id =>
  call('get', `/marketing/customer-tag-import-batches/${idPart(id)}/source-file`, { responseType: 'blob' }, null);

// 页面六：标签客户审核
export const listPendingTagCustomerApprovals = (params = {}) =>
  call('get', '/marketing/customer-tag-approvals/pending', { params }, pageFallback);
export const listTagCustomerApprovalHistory = (params = {}) =>
  call('get', '/marketing/customer-tag-approvals/history', { params }, pageFallback);
export const getPendingTagCustomers = (tagId, params = {}) =>
  call('get', `/marketing/customer-tag-approvals/tags/${idPart(tagId)}/customers`, { params }, pageFallback);
export const getTagApprovalDetail = tagId =>
  call('get', `/marketing/customer-tag-approvals/tags/${idPart(tagId)}`, {}, null);
export const approveCustomerTag = (tagId, data = {}) =>
  call('post', `/marketing/customer-tag-approvals/tags/${idPart(tagId)}/approve`, { data });
export const rejectCustomerTag = (tagId, data) =>
  call('post', `/marketing/customer-tag-approvals/tags/${idPart(tagId)}/reject`, { data });
export const approveTagCustomer = (detailId, data = {}) =>
  call('post', `/marketing/customer-tag-approvals/${idPart(detailId)}/approve`, { data });
export const approveTagCustomers = data =>
  call('post', '/marketing/customer-tag-approvals/batch-approve', { data });
export const rejectTagCustomer = (detailId, data) =>
  call('post', `/marketing/customer-tag-approvals/${idPart(detailId)}/reject`, { data });
export const rejectTagCustomers = data =>
  call('post', '/marketing/customer-tag-approvals/batch-reject', { data });
