import { call, unwrapPage } from './http';

// 工作流任务接口 wrapper（对接 workflow-center TaskController /api/workflow/tasks）

/**
 * 我的待办列表（分页）
 * @param {object} params - { bizType?, keyword?, pageNo?, pageSize? }
 * @returns 数组：records
 */
export function listTodoTasks(params = {}) {
  return call('get', '/workflow/tasks', { params: { pageSize: 50, ...params } }, []).then(unwrapPage);
}

/**
 * 我的已办列表（分页）
 */
export function listDoneTasks(params = {}) {
  return call('get', '/workflow/tasks/done', { params: { pageSize: 50, ...params } }, []).then(unwrapPage);
}

/** 任务详情 */
export function getTaskDetail(taskId) {
  return call('get', `/workflow/tasks/${taskId}`, {}, {});
}

/** 签收（候选组 → 受理人） */
export function claimTask(taskId) {
  // POST 无 body，但 call() 的 fallback 只对 GET 生效；这里 POST 真错时会抛
  return call('post', `/workflow/tasks/${taskId}/claim`, { data: {} }, { ok: true });
}

/**
 * 审批通过.
 *
 * @param {string} taskId   任务 ID
 * @param {string} opinion  审批意见
 * @param {object} [formData] 节点表单字段（如 biz_dept_review 的 needsOriginalOwnerApprove），
 *                            后端透传到 Flowable 变量，供下游网关条件分支使用
 */
export function approveTask(taskId, opinion, formData) {
  return call('post', `/workflow/tasks/${taskId}/approve`, { data: { opinion, formData } }, { ok: true });
}

/** 驳回 */
export function rejectTask(taskId, opinion) {
  return call('post', `/workflow/tasks/${taskId}/reject`, { data: { opinion } }, { ok: true });
}
