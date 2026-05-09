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
  return call('get', '/sys/calendar', { params: { year, month } }, []);
}

// === 任务调度 ===
// yiti: PageResult<JobConfDTO>
export async function listJobs(params = {}) {
  return unwrapPage(await call('get', '/admin/sys/jobs', { params }, sysJobs));
}
export function pauseJob(id) { return call('put', `/admin/sys/jobs/${id}/pause`, {}, { ok: true }); }
export function resumeJob(id) { return call('put', `/admin/sys/jobs/${id}/resume`, {}, { ok: true }); }
export function triggerJob(id) { return call('post', `/admin/sys/jobs/${id}/trigger`, {}, { ok: true }); }

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
