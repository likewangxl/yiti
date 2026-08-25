import { call } from './http';

export const listAvailableCustomers = (params = {}) =>
  call('get', '/customer-pool', { params }, null);

export const claimCustomer = custId =>
  call('post', '/claims', { data: { custId } });

export const listClaimedCustomers = (params = {}) =>
  call('get', '/claims/mine/customers', { params }, null);

export const startFirstTouch = (claimId, data = {}) =>
  call('post', `/claims/${claimId}/touch`, { data });

export const startFollowUpTouch = (claimId, data) =>
  call('post', `/claims/${claimId}/re-touch`, { data });

export const listMyTouchTasks = (params = {}) =>
  call('get', '/touch-tasks', { params }, null);

export const getTouchTask = id =>
  call('get', `/touch-tasks/${id}`, {}, null);

export const listTouchLogs = id =>
  call('get', `/touch-tasks/${id}/logs`, {}, null);

export const addTouchLog = (id, data) =>
  call('post', `/touch-tasks/${id}/logs`, { data });

export const completeTouchTask = id =>
  call('post', `/touch-tasks/${id}/success`, { data: {} });

export const cancelTouchTask = (id, reason) =>
  call('post', `/touch-tasks/${id}/cancel`, { data: { reason } });

export const listTouchOverview = (params = {}) =>
  call('get', '/admin/touch-tasks', { params }, null);

export const getTouchSummary = (params = {}) =>
  call('get', '/admin/touch-tasks/summary', { params }, null);

export const exportTouchOverview = (params = {}) =>
  call('get', '/admin/touch-tasks/export', { params, responseType: 'blob' }, null);

export const getMarketingCustomer = id =>
  call('get', `/customers/${id}`, {}, null);
export const listMarketingCustomers = (params = {}) => call('get', '/customers', { params }, null);
export const exportMarketingCustomers = (params = {}) =>
  call('get', '/customers/export', { params, responseType: 'blob' }, null);

// 客户线索录入
export const listLeads = (params = {}) => call('get', '/leads', { params }, null);
export const getLead = id => call('get', `/leads/${id}`, {}, null);
export const lookupLeadMainManager = (params = {}) =>
  call('get', '/leads/main-manager', { params }, null);
export const createLead = data => call('post', '/leads', { data });
export const updateLead = (id, data) => call('put', `/leads/${id}`, { data });
export const deleteLead = id => call('delete', `/leads/${id}`, {});
export const submitLead = id => call('post', `/leads/${id}/submit`, { data: {} });
export const previewLeadImport = file => {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/leads/import/preview', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const executeLeadImport = batchId =>
  call('post', '/leads/import/execute', { data: { batchId } });
export const createLeadEditVersion = data => call('post', '/leads/edit-version', { data });
export const createLeadDeleteVersion = data => call('post', '/leads/delete-version', { data });
export const listLeadVersions = id => call('get', `/leads/${id}/versions`, {}, []);

// 客户线索审批
export const listLeadApprovals = (params = {}) => call('get', '/lead-approvals', { params }, null);
export const getLeadApproval = leadId => call('get', `/lead-approvals/${leadId}`, {}, null);
// 客户池详情复用审批详情资源，但使用语义明确的 API 名称，避免页面误用线索录入详情。
export const getAvailableCustomerLeadDetail = leadId =>
  call('get', `/lead-approvals/${leadId}`, {}, null);
export const exportLeadApprovals = (params = {}) =>
  call('get', '/lead-approvals/export', { params, responseType: 'blob' }, null);

// 客户标签管理与审核
export const listTags = (params = {}) => call('get', '/tags', { params }, null);
export const listEnabledTags = () => call('get', '/tags/enabled', {}, []);
export const createTag = data => call('post', '/tags', { data });
export const updateTag = (id, data) => call('put', `/tags/${id}`, { data });
export const changeTagStatus = (id, status) => call('put', `/tags/${id}/status`, { data: { status } });
export const batchDisableTags = ids => call('post', '/tags/batch-disable', { data: { ids } });
export const batchDeleteTags = ids => call('post', '/tags/batch-delete', { data: { ids } });
export const approveTag = id => call('post', `/tags/${id}/approve`, { data: {} });
export const rejectTag = (id, reason) => call('post', `/tags/${id}/reject`, { data: { reason } });
export const listTagCustomers = id => call('get', `/tags/${id}/customers`, {}, []);
export const importTagCustomers = (id, custIds, mode) =>
  call('post', `/tags/${id}/customers/import`, { data: { custIds, mode } });
export const downloadTagCustomerImportTemplate = id =>
  call('get', `/tags/${id}/customers/import-template`, { responseType: 'blob' }, null);
export const importTagCustomersFile = (id, file, mode) => {
  const data = new FormData();
  data.append('file', file);
  data.append('mode', mode);
  return call('post', `/tags/${id}/customers/import-file`, { data }, null);
};

// 客户触达周期限制
export const listTouchLimitRules = (params = {}) =>
  call('get', '/touch-limit-rules', { params }, { records: [], total: 0 });
export const updateTouchLimitRule = (tagId, data) =>
  call('put', `/touch-limit-rules/${encodeURIComponent(tagId)}`, { data });

// 跨机构客户营销申请
export const validateCrossOrgMarketing = custId =>
  call('get', '/cross-org-marketing/validate', { params: { custId } }, null);
export const createCrossOrgMarketing = data => call('post', '/cross-org-marketing', { data });
export const listCrossOrgMarketing = (params = {}) => call('get', '/cross-org-marketing', { params }, []);
export const getCrossOrgMarketing = id => call('get', `/cross-org-marketing/${id}`, {}, null);
export const approveCrossOrgMarketing = (id, reason) =>
  call('post', `/cross-org-marketing/${id}/approve`, { data: { reason } });
export const rejectCrossOrgMarketing = (id, reason) =>
  call('post', `/cross-org-marketing/${id}/reject`, { data: { reason } });

// 客户转交
export const listCustomerTransfers = (params = {}) => call('get', '/customer-transfers', { params }, null);
export const listTransferCandidates = (params = {}) => call('get', '/customer-transfers/candidates', { params }, []);
export const transferCustomer = data => call('post', '/customer-transfers', { data });

export async function uploadTouchPhoto(file) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/files/upload', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

export const uploadLeadAttachment = uploadTouchPhoto;
