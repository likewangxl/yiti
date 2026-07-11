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
// 异步接口：后端立即返回 { batchId, status }（status=3=IMPORTING 处理中）；
// 校验/入库在后台线程执行，前端通过轮询 getAssignBatchDetail(batchId) 获取最终结果。
// 单独设 60s 超时（后端需全量解析 Excel，可能耗时数秒）。
export function importAssign(file, taskType, taskName, deadline) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/eval/assign/import', {
    params: { taskType, taskName, deadline },
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  }, null);
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
// 奖励分配（管理端） (EvalRewardAdminController: /api/admin/eval/reward)
// ============================================================

// 下载奖励分配导入模板（8 列）
export function downloadRewardTemplate() {
  return call('get', '/admin/eval/reward/import-template', { responseType: 'blob' }, null);
}

// 导入奖励分配：file + taskName + deadline(yyyy-MM-dd HH:mm:ss)
// 异步接口：后端立即返回 { batchId, status }（status=3=IMPORTING）；前端轮询 getRewardBatchDetail 获取结果。
export function importReward(file, taskName, deadline) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/eval/reward/import', {
    params: { taskName, deadline },
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  }, null);
}

// 管理端-分页查询奖励分配批次列表
export function listRewardBatches(params = {}) {
  return call('get', '/admin/eval/reward/batches', { params: { page: 1, pageSize: 20, ...params } }, { records: [], total: 0 });
}

// 管理端-查询奖励分配批次详情（含分页明细）
export function getRewardBatchDetail(batchId, params = {}) {
  return call('get', `/admin/eval/reward/batches/${batchId}`, { params: { page: 1, pageSize: 50, ...params } }, { batch: {}, items: { records: [], total: 0 } });
}

// 管理端-确认发布奖励分配草稿批次
export function publishRewardBatch(batchId) {
  return call('post', `/admin/eval/reward/batches/${batchId}/publish`, {}, { ok: true });
}

// 管理端-导出奖励分配批次明细 Excel
export function exportRewardBatchItems(batchId) {
  return call('get', `/admin/eval/reward/batches/${batchId}/export`, { params: {}, responseType: 'blob', timeout: EXPORT_TIMEOUT_MS }, null);
}

// ============================================================
// 奖励分配（用户端） (EvalRewardPendingController: /api/eval/reward-tasks)
// ============================================================

// 我的奖励分配待处理汇总（按部门聚合未提交明细）
export function listRewardPendingTasks() {
  return call('get', '/eval/reward-tasks', {}, []);
}

// 某批次+部门下分配给我的明细
export function listRewardPendingItems(batchId, dept) {
  return call('get', '/eval/reward-tasks/items', { params: { batchId, dept } }, []);
}

// 一次性提交某部门下全部被分配人的分配值。items: [{ itemId, assignValue }]
export function submitRewardBatch(batchId, dept, items) {
  return call('post', '/eval/reward-tasks/submit-batch', { data: { batchId, dept, items } }, { ok: true });
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
