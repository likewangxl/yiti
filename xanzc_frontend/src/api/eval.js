// 内部评价模块 API
// 后端 19 个端点：标签 4 + 人员标签 3 + 规则 5 + 任务 4 + 打分 3

import { call, unwrapPage } from './http';

// 导出/下载类请求超时（默认全局 15s 太短）：批次明细可达 20 万行，后端 SXSSF 流式生成 Excel
// 约需 1 分钟以上，前端必须放宽超时，否则后端还没生成完 axios 就 abort 取消了。
const EXPORT_TIMEOUT_MS = 300000; // 5 分钟

// ============================================================
// 标签管理 (EvalTagController: /api/admin/eval/tags)
// ============================================================

export function listTags(params = {}) {
  return call('get', '/admin/eval/tags', { params: { page: 1, pageSize: 50, ...params } }, { records: [], total: 0 });
}

export function createTag(tagName) {
  return call('post', '/admin/eval/tags', { params: { tagName } }, { tagId: Date.now() });
}

export function updateTag(tagId, tagName, status) {
  const params = {};
  if (tagName != null) params.tagName = tagName;
  if (status != null) params.status = status;
  return call('put', `/admin/eval/tags/${tagId}`, { params }, { ok: true });
}

export function deleteTag(tagId) {
  return call('delete', `/admin/eval/tags/${tagId}`, {}, { ok: true });
}

// V1.x：查询全部启用标签（不分页），供下拉选项使用
export function listAllTags(params = {}) {
  return call('get', '/admin/eval/tags/all', { params }, []);
}

// ============================================================
// 人员标签关联 (EvalUserTagController: /api/admin/eval/user-tags)
// ============================================================

export function listUserTags(userId) {
  return call('get', '/admin/eval/user-tags', { params: { userId } }, []);
}

// 2026-05-29：人员标签列表化改造
// 分页查询人员标签列表（含部门/岗位/角色 + 单一评价角色标签）
export function pageUserRoles(params = {}) {
  return call('get', '/admin/eval/user-tags/page', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

// 2026-06-10：单一角色化——覆盖式保存人员评价角色（单标签）+ 是否参与评价(1/0)
export function saveUserRoles(userId, tagId, evalEnabled) {
  return call('put', `/admin/eval/user-tags/${userId}/roles`, { data: { tagId, evalEnabled } }, { ok: true });
}

// 2026-05-29：人员标签 导入 / 模板下载 / 导出
export async function importUserRoles(file) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/eval/user-tags/import', {
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, null);
}

export function downloadImportTemplate() {
  return call('get', '/admin/eval/user-tags/import-template', { responseType: 'blob' }, null);
}

export function exportUserRoles(keyword, evalEnabled) {
  return call('get', '/admin/eval/user-tags/export', { params: { keyword, evalEnabled }, responseType: 'blob', timeout: EXPORT_TIMEOUT_MS }, null);
}

// ============================================================
// 评价规则 (EvalRuleController: /api/admin/eval/rules)
// ============================================================

export function listRules(params = {}) {
  return call('get', '/admin/eval/rules', { params: { page: 1, pageSize: 50, ...params } }, { records: [], total: 0 });
}

export function getRuleDetail(ruleId) {
  return call('get', `/admin/eval/rules/${ruleId}`, {}, { rule: {}, groups: [] });
}

export function createRule(data) {
  return call('post', '/admin/eval/rules', { data }, { ruleId: Date.now() });
}

export function updateRule(ruleId, data) {
  return call('put', `/admin/eval/rules/${ruleId}`, { data }, { ok: true });
}

export function deleteRule(ruleId) {
  return call('delete', `/admin/eval/rules/${ruleId}`, {}, { ok: true });
}

// ============================================================
// 评价任务 (EvalTaskController: /api/admin/eval/tasks)
// ============================================================

export function listTasks(params = {}) {
  return call('get', '/admin/eval/tasks', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

export function getTaskDetail(taskId) {
  return call('get', `/admin/eval/tasks/${taskId}`, {}, { task: {}, targets: [] });
}

export function createTask(data) {
  return call('post', '/admin/eval/tasks', { data }, { taskId: Date.now() });
}

export function closeTask(taskId) {
  return call('put', `/admin/eval/tasks/${taskId}/close`, {}, { ok: true });
}

// ============================================================
// 待处理任务（导入式评价任务）
// 管理端导入 (EvalAssignAdminController: /api/admin/eval/assign)
// ============================================================

// 下载评价任务导入模板（10 列）
export function downloadAssignTemplate() {
  return call('get', '/admin/eval/assign/import-template', { responseType: 'blob' }, null);
}

// 导入评价任务：file + taskType + taskName + deadline(yyyy-MM-dd HH:mm:ss)
// 同一端点两种回包，统一用 blob 接收再按 Content-Type 分流：
//   - 错误数 ≤ 阈值：后端回 JSON 信封(ResponseWrapper)，解包后返回结果 DTO({success, importedCount, errors}) 供前端展示
//   - 错误数 > 阈值：后端回 CSV 文件流(text/csv)，返回 { csv: true, blob } 交由调用方触发下载
export async function importAssign(file, taskType, taskName, deadline) {
  const fd = new FormData();
  fd.append('file', file);
  // responseType=blob：同一端点可能回 JSON 或 CSV 文件流，先拿二进制，避免拦截器按 JSON 误解析
  const blob = await call('post', '/admin/eval/assign/import', {
    params: { taskType, taskName, deadline },
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' },
    responseType: 'blob'
  }, null);
  // 错误数超过阈值：后端直接回 CSV 文件流，交调用方触发下载
  if (blob && typeof blob.type === 'string' && blob.type.includes('csv')) {
    return { csv: true, blob };
  }
  // 否则是 JSON 信封：responseType=blob 下响应拦截器不再自动解包，这里手动解析
  const text = await blob.text();
  let body;
  try { body = JSON.parse(text); } catch { throw new Error('导入响应解析失败'); }
  if (body && (body.code === '00000' || body.code === 0 || body.code === '0')) {
    return body.data;
  }
  throw new Error((body && (body.message || body.msg)) || '导入失败');
}

// ============================================================
// 批次管理 (EvalAssignBatchController: /api/admin/eval/assign/batches)
// ============================================================

// 管理端-分页查询导入批次列表
export function listAssignBatches(params = {}) {
  return call('get', '/admin/eval/assign/batches', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

// 管理端-查询批次详情（含分页明细）
export function getAssignBatchDetail(batchId, params = {}) {
  return call('get', `/admin/eval/assign/batches/${batchId}`, { params: { page: 1, pageSize: 50, ...params } }, { batch: {}, items: { records: [], total: 0 } });
}

// 管理端-确认发布草稿批次
export function publishAssignBatch(batchId) {
  return call('post', `/admin/eval/assign/batches/${batchId}/publish`, {}, { ok: true });
}

// 管理端-导出批次明细 Excel（大批次可达 20 万行，放宽超时避免后端生成未完前端就取消）
export function exportAssignBatchItems(batchId) {
  return call('get', `/admin/eval/assign/batches/${batchId}/export`, { params: {}, responseType: 'blob', timeout: EXPORT_TIMEOUT_MS }, null);
}

// ============================================================
// 待处理任务（用户端） (EvalPendingController: /api/eval/pending-tasks)
// ============================================================

// 我的待处理任务汇总（按被打分人部门聚合未提交明细）
export function listPendingTasks() {
  return call('get', '/eval/pending-tasks', {}, []);
}

// 某批次+部门下分配给我的明细
export function listPendingItems(batchId, dept) {
  return call('get', '/eval/pending-tasks/items', { params: { batchId, dept } }, []);
}

// 提交某条明细的打分
export function submitPendingScore(itemId, score) {
  return call('post', '/eval/pending-tasks/submit', { data: { itemId, score } }, { ok: true });
}

// 批量提交某部门下多人打分（一个事务 all-or-none）。items: [{ itemId, score }]
export function submitPendingScoreBatch(items) {
  return call('post', '/eval/pending-tasks/submit-batch', { data: { items } }, { ok: true });
}

// ============================================================
// 我的评价（规则驱动 / 自动生成，旧流程，前端入口已隐藏）(EvalScoreController: /api/eval)
// ============================================================

export function listMyTasks(params = {}) {
  return call('get', '/eval/my-tasks', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

export function listMyTaskTargets(taskId) {
  return call('get', `/eval/my-tasks/${taskId}/targets`, {}, []);
}

export function submitScore(data) {
  return call('post', '/eval/scores', { data }, { ok: true });
}

// ============================================================
// 统一评价任务列表（管理端，合并规则任务 + 导入批次）
// ============================================================

export function listUnifiedTasks(params = {}) {
  return call('get', '/admin/eval/tasks/unified', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

export function exportRuleTask(taskId) {
  return call('get', `/admin/eval/tasks/${taskId}/export`, { params: {}, responseType: 'blob', timeout: EXPORT_TIMEOUT_MS }, null);
}

// 删除评价任务（硬删除，需截止时间已过）
export function deleteUnifiedTask(sourceType, sourceId) {
  return call('delete', `/admin/eval/tasks/unified/${sourceType}/${sourceId}`, {}, { ok: true });
}
