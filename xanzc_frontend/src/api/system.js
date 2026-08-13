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
// V1.x：查询全部角色（不分页），供下拉选项使用
export function listAllRoles(params = {}) {
  return call('get', '/admin/roles/all', { params }, sysRoles);
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
export function triggerJob(id, reason = '手动触发', dataDate, allocDate) {
  const data = { reason };
  if (dataDate) data.dataDate = dataDate; // 计算类任务按指定数据日期启动
  if (allocDate) data.allocDate = allocDate; // 1级指标批量计算按指定业绩分配日期（非必输）
  return call('post', `/admin/sys/jobs/${id}/trigger`, { data }, { ok: true });
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
  return call('get', `/admin/roles/${roleId}/resources`, {});
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

// === 业务标签（AdminPersonTagController: /api/admin/sys/person-tags，全平台通用；员工/机构两维度） ===
// 标签分页（含关联成员数）：{ keyword?, pageNo, pageSize } → PageResult
export function listPersonTags(params = {}) {
  return call('get', '/admin/sys/person-tags', {
    params: { pageNo: 1, pageSize: 20, ...params }
  }, { records: [], total: 0 });
}
// data: { tagName(必填 max100 全局唯一), remark? }
export function createPersonTag(data) {
  return call('post', '/admin/sys/person-tags', { data }, { ok: true });
}
export function updatePersonTag(tagId, data) {
  return call('put', `/admin/sys/person-tags/${tagId}`, { data }, { ok: true });
}
// 删除标签：后端级联删除该标签下全部人员关联
export function deletePersonTag(tagId) {
  return call('delete', `/admin/sys/person-tags/${tagId}`, {}, { ok: true });
}
// 成员分页（按维度 dim=EMP/ORG）：EMP 仅工号，ORG 为机构编号+名称。params: { dim, pageNo, pageSize }
export function listPersonTagMembers(tagId, params = {}) {
  return call('get', `/admin/sys/person-tags/${tagId}/members`, {
    params: { dim: 'EMP', pageNo: 1, pageSize: 20, ...params }
  }, { records: [], total: 0 });
}
// 批量新增成员（员工工号 + 机构编号可同时提交，已在标签下的同维度成员后端自动跳过），data 为实际新增条数
// payload: { usernames?: string[], orgDeptNos?: string[] }
export function addPersonTagMembers(tagId, payload) {
  return call('post', `/admin/sys/person-tags/${tagId}/members`, { data: payload }, 0);
}
// 修改成员：按行维度换成另一个工号(EMP)或机构编号(ORG)。data: { username? } | { orgDeptNo? }
export function updatePersonTagMember(tagId, id, data) {
  return call('put', `/admin/sys/person-tags/${tagId}/members/${id}`, { data }, { ok: true });
}
export function removePersonTagMember(tagId, id) {
  return call('delete', `/admin/sys/person-tags/${tagId}/members/${id}`, {}, { ok: true });
}
// 全局导入（按维度 dim）：EMP 列=标签名称/工号，ORG 列=标签名称/机构名称；缺标签自动新建，同步原子
export function importPersonTags(file, dim = 'EMP') {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', '/admin/sys/person-tags/import', {
    data: fd,
    params: { dim },
    headers: { 'Content-Type': 'multipart/form-data' }
  }, null);
}
export function downloadPersonTagTemplate(dim = 'EMP') {
  return call('get', '/admin/sys/person-tags/import-template', { params: { dim }, responseType: 'blob' }, null);
}
// 成员导入（按维度全量覆盖，只覆盖该维度、不影响另一维度，调用前必须先向用户确认）
export function importPersonTagMembers(tagId, file, dim = 'EMP') {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', `/admin/sys/person-tags/${tagId}/import`, {
    data: fd,
    params: { dim },
    headers: { 'Content-Type': 'multipart/form-data' }
  }, null);
}
export function downloadPersonTagMemberTemplate(dim = 'EMP') {
  return call('get', '/admin/sys/person-tags/member-import-template', { params: { dim }, responseType: 'blob' }, null);
}
