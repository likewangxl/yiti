import { call, unwrapPage } from './http';

/**
 * 中台支持申请 API。
 *
 * 该模块不为写请求传入 fallback：草稿创建、提交、撤回、过程记录、派单和办理
 * 的失败必须回到页面，由页面保留用户输入并展示服务端真实错误。
 */
const REQUESTS_BASE = '/support-requests';
const DEPT_REQUESTS_BASE = '/support-dept/requests';

const idPath = id => encodeURIComponent(String(id));

function normalizePage(value) {
  if (Array.isArray(value)) return { records: value, total: value.length };
  if (!value || typeof value !== 'object') return { records: [], total: 0 };
  // Keep the API module easy to exercise with a minimal http mock.  The
  // production http client always exports unwrapPage, while a focused caller
  // may only mock call when testing endpoint contracts.
  const page = typeof unwrapPage === 'function' ? (unwrapPage(value) || {}) : value;
  if (Array.isArray(page)) return { records: page, total: page.length };
  const records = page.records || page.list || page.content || page.rows || [];
  return {
    ...page,
    records: Array.isArray(records) ? records : [],
    total: Number(page.total ?? page.totalCount ?? (Array.isArray(records) ? records.length : 0)) || 0
  };
}

function ownPayload(source = {}) {
  return Object.fromEntries(
    [
      'sourceType', 'sourceTouchTaskId', 'custId', 'productIds', 'otherDemand',
      'supportDeptId', 'confirmParallel', 'attachmentIds'
    ]
      .filter(field => Object.prototype.hasOwnProperty.call(source, field))
      .map(field => [field, source[field]])
  );
}

/** 发起侧：分页查询当前用户可见的中台支持申请。 */
export async function listSupportRequests(params = {}) {
  return normalizePage(await call('get', REQUESTS_BASE, { params }));
}

/** 发起侧：查询申请详情。 */
export const getSupportRequest = id => call('get', `${REQUESTS_BASE}/${idPath(id)}`);

/** 发起侧：创建申请（后端会按产品拆单，返回 requests）。 */
export const createSupportRequest = data =>
  call('post', REQUESTS_BASE, { data: ownPayload(data) });

/** 发起侧：提交单条草稿。多产品拆单由页面逐条调用。 */
export const submitSupportRequest = id =>
  call('post', `${REQUESTS_BASE}/${idPath(id)}/submit`, { data: {} });

/** 发起侧：撤回审批中/办理中的申请，并提交撤回理由。 */
export const withdrawSupportRequest = (id, reason) =>
  call('post', `${REQUESTS_BASE}/${idPath(id)}/cancel`, {
    data: { reason: String(reason || '').trim() }
  });

// 与后端现有命名保持兼容；页面统一使用 withdrawSupportRequest。
export const cancelSupportRequest = withdrawSupportRequest;

/** 发起侧：查询申请过程记录。 */
export const listSupportRequestLogs = id =>
  call('get', `${REQUESTS_BASE}/${idPath(id)}/logs`);

// 兼容“申请日志”命名，避免调用方绑定技术实现名称。
export const listSupportLogs = listSupportRequestLogs;
export const getSupportRequestLogs = listSupportRequestLogs;

/** 承接侧：分页查询待办/已办申请。 */
export async function listSupportDeptRequests(params = {}) {
  return normalizePage(await call('get', DEPT_REQUESTS_BASE, { params }));
}

/** 承接侧：查询申请详情。 */
export const getSupportDeptRequest = id =>
  call('get', `${DEPT_REQUESTS_BASE}/${idPath(id)}`);

/** 承接侧：查询过程记录。 */
export const listSupportDeptLogs = id =>
  call('get', `${DEPT_REQUESTS_BASE}/${idPath(id)}/logs`);
export const listSupportDeptRequestLogs = listSupportDeptLogs;

/** 承接侧：新增过程记录（内容、定位、打卡时间、照片等由后端校验）。 */
export const addSupportDeptLog = (id, data) =>
  call('post', `${DEPT_REQUESTS_BASE}/${idPath(id)}/logs`, { data });

// 统一日志命名别名，便于详情页按视图切换。
export const addSupportRequestLog = addSupportDeptLog;
export const addSupportDeptRequestLog = addSupportDeptLog;

/** 承接侧：秘书指定承接员工。 */
export function dispatchSupportRequest(id, assignedEmpIdOrData, dispatchRemark) {
  const data = assignedEmpIdOrData && typeof assignedEmpIdOrData === 'object'
    ? assignedEmpIdOrData
    : { assignedEmpId: assignedEmpIdOrData, dispatchRemark };
  return call('post', `${DEPT_REQUESTS_BASE}/${idPath(id)}/dispatch`, { data });
}

/** 承接侧：办理成功/驳回。支持旧版 boolean success 与新版 result/summary payload。 */
export const completeSupportRequest = (id, data = {}) =>
  call('post', `${DEPT_REQUESTS_BASE}/${idPath(id)}/complete`, {
    data: typeof data === 'boolean' ? { success: data } : data
  });

export const completeSupportDeptRequest = completeSupportRequest;
export const dispatchSupportDeptRequest = dispatchSupportRequest;

/** 客户候选：中台支持页面使用营销正式资源，而不是旧 /customers 路径。 */
export async function listSupportCustomers(params = {}) {
  const value = await call('get', '/marketing/customers', { params });
  return value;
}

/** 产品候选：后端已过滤启用且允许中台支持的产品。 */
export const listSupportProducts = (params = {}) =>
  call('get', `${REQUESTS_BASE}/available-products`, { params });

export const listAvailableSupportProducts = listSupportProducts;
export const listSupportAvailableProducts = listSupportProducts;

/** 过程照片：沿用系统文件中心上传接口。 */
export function uploadSupportPhoto(file) {
  const data = new FormData();
  data.append('file', file);
  return call('post', '/files/upload', {
    data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

export const uploadSupportAttachment = uploadSupportPhoto;
