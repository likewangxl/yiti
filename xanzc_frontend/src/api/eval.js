// 内部评价模块 API
// 后端 19 个端点：标签 4 + 人员标签 3 + 规则 5 + 任务 4 + 打分 3

import { call, unwrapPage } from './http';

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
  return call('get', '/admin/eval/user-tags/export', { params: { keyword, evalEnabled }, responseType: 'blob' }, null);
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
// 我的评价 (EvalScoreController: /api/eval)
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
