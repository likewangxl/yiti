// 内部评价模块 API
// 后端 19 个端点：标签 4 + 人员标签 3 + 规则 5 + 任务 4 + 打分 3

import { call, unwrapPage } from './http';

// ============================================================
// 标签管理 (EvalTagController: /api/admin/eval/tags)
// ============================================================

export function listTags(params = {}) {
  return call('get', '/admin/eval/tags', { params: { page: 1, pageSize: 50, ...params } }, { records: [], total: 0 });
}

export function createTag(tagName, tagType) {
  return call('post', '/admin/eval/tags', { params: { tagName, tagType } }, { tagId: Date.now() });
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

// ============================================================
// 人员标签关联 (EvalUserTagController: /api/admin/eval/user-tags)
// ============================================================

export function listUserTags(userId) {
  return call('get', '/admin/eval/user-tags', { params: { userId } }, []);
}

export function bindUserTags(userId, tagIds) {
  return call('post', '/admin/eval/user-tags', { data: { userId, tagIds } }, { ok: true });
}

export function unbindUserTags(userId, tagIds) {
  return call('delete', '/admin/eval/user-tags', { data: { userId, tagIds } }, { ok: true });
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
