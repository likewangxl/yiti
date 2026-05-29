import { call } from './http';

// 审批流程设计器（对接 workflow-center FlowDesignController /api/admin/workflow/flows）
// 默认零影响：发布生成的 BPMN 部署到影子 key，不切换线上现有流程。

/** 流程列表（全部）→ 数组 */
export function listFlows() {
  return call('get', '/admin/workflow/flows', {}, []).then(r => (Array.isArray(r) ? r : (r?.records || [])));
}

/** 取流程完整模型（节点+审批人+连线） */
export function getFlow(id) {
  return call('get', `/admin/workflow/flows/${id}`, {}, null);
}

/** 新建流程，返回新 flowDefId */
export function createFlow(graph) {
  return call('post', '/admin/workflow/flows', { data: graph }, null);
}

/** 保存草稿（整图替换） */
export function saveFlow(id, graph) {
  return call('put', `/admin/workflow/flows/${id}`, { data: graph }, { ok: true });
}

/** 发布（校验→生成 BPMN→部署影子 key→版本+1） */
export function publishFlow(id) {
  return call('post', `/admin/workflow/flows/${id}/publish`, { data: {} }, { ok: true });
}

/** 删除草稿（仅 DRAFT 且非只读导入） */
export function deleteFlow(id) {
  return call('delete', `/admin/workflow/flows/${id}`, {}, { ok: true });
}

/** 条件分支可用变量白名单（按业务类型） */
export function listFlowVariables(bizType) {
  return call('get', '/admin/workflow/flows/meta/variables', { params: { bizType } }, []);
}

/** 导入现有已部署流程为只读模型（幂等） */
export function importExistingFlows() {
  return call('post', '/admin/workflow/flows/import-existing', { data: {} }, []);
}
