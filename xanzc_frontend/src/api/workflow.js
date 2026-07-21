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

// ===================== 审批流监控（ProcessMonitorController / ProcessController） =====================

/** 审批流监控列表（分页） */
export function monitorProcesses(params = {}) {
  return call('get', '/workflow/monitor/processes', { params: { pageSize: 20, ...params } }, []).then(unwrapPage);
}

/** 流程实例详情（发起人/当前节点/状态/耗时等） */
export function getProcessInfo(processInstanceId) {
  return call('get', `/workflow/processes/${processInstanceId}`, {}, {});
}

/** 流程历史节点（审批日志，按时间顺序） */
export function getProcessHistory(processInstanceId) {
  return call('get', `/workflow/processes/${processInstanceId}/history`, {}, []);
}

/** 流程进度图结构化数据（节点状态/处理人） */
export function getProcessNodes(processInstanceId) {
  return call('get', `/workflow/processes/${processInstanceId}/nodes`, {}, {});
}

/** 流程进度图 PNG 地址，供 <img> 直接引用（同源走 session cookie，无需单独取 blob） */
// processDiagramUrl 已于 2026-07-21 移除：后端 /processes/{id}/diagram 依赖 Flowable 部署时生成的
// PNG 资源，而本库全部流程定义 HAS_GRAPHICAL_NOTATION_ 均为 0（BPMN 无 BPMNDI 图形信息），
// getProcessDiagram 恒返回 null → 接口恒 500，该图从未成功渲染过。唯一调用方 WorkflowMonitor.vue
// 的「流程进度图」区块已一并移除，节点进度改由「流程节点」表格承载。
// 若将来要恢复流程图，需先让 BPMN 带上 BPMNDI（设计器已有 x/y 坐标，可在 FlowBpmnGenerator 补 DI），
// 否则光调这个接口仍是 500。

// ===================== 任务转交待认领（TaskTransferController / TaskTransferService） =====================
// 两阶段转交：发起（秘书/行长，挂在监控域下）→ 接收人收件箱认领/拒绝 → 发起人发件箱查看/撤回。
// 旧单阶段「一步到位」transferTask（TaskController，已下线）不是同一回事，前端本就未接过它。

/**
 * 发起转交（待认领）。挂在监控域下，仅秘书岗/行长可调（对齐 WORKFLOW_MONITOR·TRANSFER 鉴权）。
 * @param {string} taskId
 * @param {{toEmpId:string, reason:string}} payload
 * @returns 转交记录ID
 */
export function transferInitiate(taskId, { toEmpId, reason }) {
  return call('post', `/workflow/monitor/tasks/${taskId}/transfer`, { data: { toEmpId, reason } }, { ok: true });
}

/**
 * 查询该任务可选的转交接收人。
 * 后端与 initiate 的资格校验同源：返回的人提交必定通过，不会再被 WF-40912 打回。
 * @param {string} taskId
 * @returns {Promise<Array<{empId:string, displayName:string, orgCode:string, orgName:string}>>}
 */
export function transferCandidates(taskId) {
  return call('get', `/workflow/monitor/tasks/${taskId}/transfer-candidates`, {}, []).then(r => r || []);
}

/**
 * 某流程实例的转交历史（审批流监控详情抽屉）。
 * 含待认领与全部终态（已认领/已拒绝/已撤回/已失效）及拒绝原因，按发起时间正序。
 * @param {string} processInstanceId
 */
export function processTransferHistory(processInstanceId) {
  return call('get', `/workflow/monitor/processes/${processInstanceId}/transfers`, {}, []).then(r => r || []);
}

/** 转交收件箱：当前登录用户待认领的转交任务列表 */
export function transferInbox() {
  return call('get', '/workflow/transfers/inbox', {}, []).then(r => r || []);
}

/** 认领转交（接收人本人） */
export function transferAccept(id) {
  return call('post', `/workflow/transfers/${id}/accept`, { data: {} }, { ok: true });
}

/** 拒绝转交（接收人本人，理由必填） */
export function transferDecline(id, { reason }) {
  return call('post', `/workflow/transfers/${id}/decline`, { data: { reason } }, { ok: true });
}

/** 转交发件箱：当前登录用户发起的转交任务列表（待认领+已认领） */
export function transferOutbox() {
  return call('get', '/workflow/transfers/outbox', {}, []).then(r => r || []);
}

/** 撤回转交（发起人本人） */
export function transferCancel(id) {
  return call('post', `/workflow/transfers/${id}/cancel`, { data: {} }, { ok: true });
}
