import { call, unwrapPage } from './http';
import {
  sysRoles, sysResources, sysScopeMatrix,
  sysDictTypes, sysDictItems,
  sysJobs, sysAuditLogs, sysNotifications, sysConfig, sysFiles
} from '@/mock';

// === 权限配置 ===
// yiti: GET /admin/roles/  → PageResult<RoleRespDTO>，统一抽数组
export async function listRoles(params = {}) {
  return unwrapPage(await call('get', '/admin/roles/', { params }, sysRoles));
}
// 角色 CRUD（RoleController）
// data: { roleCode(大写下划线 max 10), roleChName(max 100), remark? }
export function createRole(data) {
  return call('post', '/admin/roles/', { data }, { ok: true });
}
// data: { roleChName, remark? }（roleCode 创建后不可改）
export function updateRole(roleId, data) {
  return call('put', `/admin/roles/${roleId}`, { data }, { ok: true });
}
// 逻辑删除；reason 必填（query）
export function deleteRole(roleId, reason = '前端删除') {
  return call('delete', `/admin/roles/${roleId}`, { params: { reason } }, { ok: true });
}
// 查询角色下的用户列表（分页）
export async function listRoleUsers(roleId, params = {}) {
  return unwrapPage(await call('get', `/admin/roles/${roleId}/users`, {
    params: { pageNo: 1, pageSize: 20, ...params }
  }, []));
}

export function listResources() {
  return call('get', '/admin/resources/tree', {}, sysResources);
}
export function getScopeMatrix(roleId) {
  return call('get', '/admin/biz-scopes/matrix', { params: { roleId } }, sysScopeMatrix);
}

// === 字典 ===
export function listDictTypes() {
  return call('get', '/sys/dicts', {}, sysDictTypes);
}
export function listDictItems(dictType) {
  return call('get', `/sys/dicts/${dictType}/items`, {}, () => sysDictItems[dictType] || []);
}

// === 工作日历（仅读，写在管理端）===
export function getCalendar(year, month) {
  return call('get', '/admin/sys/calendar', { params: { year, month } }, []);
}
// 切换某天工作/休息状态：PUT /admin/sys/calendar/{date}
// date 格式 'YYYY-MM-DD'，data: { isWorkday: 0|1, remark: '' }
export function setCalendarDay(date, data) {
  return call('put', `/admin/sys/calendar/${date}`, { data }, { ok: true });
}
// 年初初始化：POST /admin/sys/calendar/init，body: { year }
export function initCalendarYear(year) {
  return call('post', '/admin/sys/calendar/init', { data: { year } }, { ok: true });
}
// 批量导入 xlsx：POST /admin/sys/calendar/import，multipart/form-data
export function importCalendar(file) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/sys/calendar/import', {
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, { ok: true });
}

// === 任务调度 ===
// yiti: PageResult<JobConfDTO>
export async function listJobs(params = {}) {
  return unwrapPage(await call('get', '/admin/sys/jobs', { params }, sysJobs));
}
export function pauseJob(id) { return call('put', `/admin/sys/jobs/${id}/pause`, {}, { ok: true }); }
export function resumeJob(id) { return call('put', `/admin/sys/jobs/${id}/resume`, {}, { ok: true }); }
export function triggerJob(id, reason = '手动触发') {
  return call('post', `/admin/sys/jobs/${id}/trigger`, { data: { reason } }, { ok: true });
}
export async function listJobLogs(jobId, params = {}) {
  return unwrapPage(await call('get', `/admin/sys/jobs/${jobId}/logs`, { params }, []));
}

// === 审计日志 ===
// yiti: PageResult<AuditLogDTO>
export async function listAuditLogs(params = {}) {
  return unwrapPage(await call('get', '/admin/sys/audit-logs', { params }, sysAuditLogs));
}
export function getAuditLog(id) {
  return call('get', `/admin/sys/audit-logs/${id}`, {}, () => sysAuditLogs.find(x => x.id === id) || {});
}

// === 通知 ===
// yiti: PageResult<NotificationDTO>
export async function listNotifications(params = {}) {
  return unwrapPage(await call('get', '/notifications', { params }, sysNotifications));
}

// === 系统配置 ===
// yiti: PageResult<ConfigDTO>
export async function listConfigs(params = {}) {
  return unwrapPage(await call('get', '/admin/sys/configs', { params }, sysConfig));
}

// === 文件管理 ===
// 全局文件列表（管理后台用）：GET /api/admin/sys/files，新加的接口
// 字段：id, fileName, fileSize, fileType, md5Hash, fileRole, uploadedBy, uploadedTime
export async function listFiles(params = {}) {
  return unwrapPage(await call('get', '/admin/sys/files', { params }, sysFiles));
}
// 业务关联查询（旧接口，需要 bizType + bizId）
export async function listBizFiles(bizType, bizId) {
  return call('get', '/files', { params: { bizType, bizId } }, []);
}
export function deleteFile(fileId) {
  return call('delete', `/files/${fileId}`, {}, { ok: true });
}

// === 权限编辑器写接口 ===
// 拉某角色已勾选的资源 ID 集合
export function getRoleResourceIds(roleId) {
  return call('get', `/admin/roles/${roleId}/resources`, {}, []);
}
// 全量替换该角色资源绑定（PUT）
export function replaceRoleResources(roleId, resourceIds, reason) {
  return call('put', `/admin/roles/${roleId}/resources`, {
    data: { resourceIds, reason }
  }, { ok: true });
}
// UPSERT 数据范围（POST）
export function saveBizScope(roleId, bizType, dataScope, reason) {
  return call('post', '/admin/biz-scopes', {
    data: { roleId, bizType, dataScope, reason }
  }, { ok: true });
}

// === 流程超时规则 ===
export function listTimeoutRules(processDefinitionKey) {
  const params = processDefinitionKey ? { processDefinitionKey } : {};
  return call('get', '/admin/workflow/timeout-rules', { params }, []);
}
export function updateTimeoutRule(id, data) {
  return call('put', `/admin/workflow/timeout-rules/${id}`, { data }, { ok: true });
}
export function createTimeoutRule(data) {
  return call('post', '/admin/workflow/timeout-rules', { data }, { ok: true });
}

// === 流程节点候选人配置 ===
export function listNodeCandidates(processDefinitionKey) {
  const params = processDefinitionKey ? { processDefinitionKey } : {};
  return call('get', '/admin/workflow/node-candidates', { params }, []);
}
export function updateNodeCandidate(id, data) {
  return call('put', `/admin/workflow/node-candidates/${id}`, { data }, { ok: true });
}
export function createNodeCandidate(data) {
  return call('post', '/admin/workflow/node-candidates', { data }, { ok: true });
}

// === 流程节点表单配置 ===
export function listNodeForms(processDefinitionKey) {
  const params = processDefinitionKey ? { processDefinitionKey } : {};
  return call('get', '/admin/workflow/node-forms', { params }, []);
}
export function updateNodeForm(id, data) {
  return call('put', `/admin/workflow/node-forms/${id}`, { data }, { ok: true });
}
export function createNodeForm(data) {
  return call('post', '/admin/workflow/node-forms', { data }, { ok: true });
}

// === 流程定义列表 ===
export function listProcessDefinitions() {
  return call('get', '/admin/workflow/process-definitions', {}, []);
}

// === 菜单分配（参考 xanpd role.vue 分配菜单流程） ===
// 取菜单树（PT_RESOURCE.IS_MENU=1 全量层级）
export function getMenuTree() {
  return call('get', '/admin/resources/menu-tree', {}, []);
}
// 取某角色已勾的菜单 ID 列表
export function getRoleMenuIds(roleId) {
  return call('get', `/admin/roles/${roleId}/menus`, {}, []);
}
// 全量替换该角色菜单绑定（PUT），接口绑定不动
export function replaceRoleMenus(roleId, menuIds, reason) {
  return call('put', `/admin/roles/${roleId}/menus`, {
    data: { menuIds, reason }
  }, { ok: true });
}
