import { call, unwrapPage } from './http';

/**
 * 公告 API
 *
 * 公开端点：/api/portal/announcements (列表/详情/最近)
 * 管理端点：/api/admin/announcements (新增/删除)
 */

/** 分页查询公告列表（公开端，仅未删除） */
export async function listAnnouncements(params = {}) {
  const r = await call('get', '/portal/announcements', {
    params: { pageNo: 1, pageSize: 20, ...params }
  }, { records: [], total: 0 });
  return r;
}

/** 管理端分页查询（含已删除） */
export async function listAnnouncementsAdmin(params = {}) {
  const r = await call('get', '/admin/announcements', {
    params: { pageNo: 1, pageSize: 20, ...params }
  }, { records: [], total: 0 });
  return r;
}

/** 最近 N 条公告（工作台用） */
export function listRecentAnnouncements(limit = 10) {
  return call('get', '/portal/announcements/recent', { params: { limit } }, []);
}

/** 公告详情（含文件列表） */
export function getAnnouncementDetail(id) {
  return call('get', `/portal/announcements/${id}`, {}, null);
}

/** 新增公告 */
export function createAnnouncement(data) {
  return call('post', '/admin/announcements', { data }, null);
}

/** 上传公告附件（返回 AnnouncementFileDTO） */
export async function uploadAnnouncementFile(announcementId, file) {
  const fd = new FormData();
  fd.append('file', file);
  return call('post', `/admin/announcements/${announcementId}/files`, {
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, null);
}

/** 置顶/取消置顶公告 */
export function togglePinAnnouncement(id) {
  return call('put', `/admin/announcements/${id}/toggle-pin`, {}, { ok: true });
}

/** 逻辑删除公告 */
export function deleteAnnouncement(id) {
  return call('delete', `/admin/announcements/${id}`, {}, { ok: true });
}
