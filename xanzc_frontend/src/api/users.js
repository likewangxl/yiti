// 用户 API —— 对接 yiti `/api/admin/users`（UserController + UserRoleController）
//
// 后端字段（UserListItemRespDTO / UserDetailRespDTO）：
//   userId / username / userchnname / email / remark
//   isEnabled  (0=启用, 1=未启用)         ← 反语义，前端渲染要注意
//   isLocked   (0=未锁定, 1=已锁定)
//   isExpired  (0=未过期, 1=已过期)
//   passWrongCount / pwdUpdateTime / createTime / updateTime / createAuthor / updateAuthor
//
// 后端不可用时回退 mock（仅 GET），形态与后端一致。

import { call, unwrapPage } from './http';

// === Mock 兜底数据 ===
const mockUsers = [
  { userId: 'admin',     username: 'admin',     userchnname: '系统管理员',  email: 'admin@bank.cn',  remark: '13800138000', isEnabled: 0, isLocked: 0, isExpired: 0, createTime: '2026-01-01 09:00:00', createAuthor: 'system' },
  { userId: 'U10001',    username: 'zhangsan',  userchnname: '张三',        email: 'zs@bank.cn',     remark: '13901390001', isEnabled: 0, isLocked: 0, isExpired: 0, createTime: '2026-02-10 10:30:00', createAuthor: 'admin' },
  { userId: 'U10002',    username: 'lisi',      userchnname: '李四',        email: 'ls@bank.cn',     remark: '13901390002', isEnabled: 0, isLocked: 0, isExpired: 0, createTime: '2026-02-12 11:00:00', createAuthor: 'admin' },
  { userId: 'U10003',    username: 'wangwu',    userchnname: '王五',        email: 'ww@bank.cn',     remark: '13901390003', isEnabled: 1, isLocked: 0, isExpired: 0, createTime: '2026-02-15 14:20:00', createAuthor: 'admin' },
  { userId: 'U10004',    username: 'zhaoliu',   userchnname: '赵六',        email: 'zl@bank.cn',     remark: '13901390004', isEnabled: 0, isLocked: 1, isExpired: 0, createTime: '2026-03-01 09:15:00', createAuthor: 'admin' },
  { userId: 'U10005',    username: 'sunqi',     userchnname: '孙七',        email: 'sq@bank.cn',     remark: '13901390005', isEnabled: 0, isLocked: 0, isExpired: 1, createTime: '2026-03-08 16:40:00', createAuthor: 'admin' }
];

const mockUserRoles = {
  admin:    [{ roleId: 'R_ADMIN',     roleCode: 'SYS_ADMIN',  roleChName: '系统管理员' }],
  U10001:   [{ roleId: 'R_CM',        roleCode: 'CUST_MGR',   roleChName: '客户经理' }],
  U10002:   [{ roleId: 'R_CM',        roleCode: 'CUST_MGR',   roleChName: '客户经理' },
             { roleId: 'R_OG',        roleCode: 'ORG_LEAD',   roleChName: '机构负责人' }],
  U10003:   [],
  U10004:   [{ roleId: 'R_OG',        roleCode: 'ORG_LEAD',   roleChName: '机构负责人' }],
  U10005:   []
};

// 表头展示用：状态文案 + 颜色
export const USER_STATUS_LABEL = { 0: '启用', 1: '停用' };
export const USER_LOCK_LABEL   = { 0: '正常', 1: '锁定' };

// === 用户 CRUD ===

// 分页查询
// 入参：username / userchnname / email / remark / isEnabled / isLocked / pageNo / pageSize
export async function listUsers(params = {}) {
  return unwrapPage(await call('get', '/admin/users', { params: { pageNo: 1, pageSize: 20, ...params } }, mockUsers));
}

// 用户详情
export function getUser(userId) {
  return call('get', `/admin/users/${userId}`, {}, () => mockUsers.find(u => u.userId === userId) || mockUsers[0]);
}

// 用户名是否已存在（用于"用户名"字段的失焦校验）
export function checkUsernameExists(username) {
  return call('get', `/admin/users/${encodeURIComponent(username)}/exists`, {}, () => ({ exists: false }));
}

// 新增用户
//   data: { userId, username, userchnname, email, initialPassword, remark }
export function createUser(data) {
  return call('post', '/admin/users', { data }, { ok: true });
}

// 修改用户（partial update：传谁改谁）
//   data: { username?, userchnname?, email?, remark? }
export function updateUser(userId, data) {
  return call('put', `/admin/users/${userId}`, { data }, { ok: true });
}

// 当前用户改自己密码
export function changeMyPassword(oldPassword, newPassword) {
  return call('put', '/admin/users/me/password', { data: { oldPassword, newPassword } }, { ok: true });
}

// === 批量操作（ids 用逗号串拼路径参数）===

function joinIds(ids) {
  if (Array.isArray(ids)) return ids.map(encodeURIComponent).join(',');
  return encodeURIComponent(ids);
}

export function deleteUsers(ids, reason = '前端删除') {
  return call('delete', `/admin/users/${joinIds(ids)}`, { params: { reason } }, { ok: true });
}
export function resetUsersPassword(ids, reason = '管理员重置') {
  return call('put', `/admin/users/${joinIds(ids)}/reset`, { params: { reason } }, { ok: true });
}
export function activeUsers(ids, reason = '启用') {
  return call('put', `/admin/users/${joinIds(ids)}/active`, { params: { reason } }, { ok: true });
}
export function inactiveUsers(ids, reason = '停用') {
  return call('put', `/admin/users/${joinIds(ids)}/inactive`, { params: { reason } }, { ok: true });
}
export function lockUsers(ids, reason = '锁定') {
  return call('put', `/admin/users/${joinIds(ids)}/lock`, { params: { reason } }, { ok: true });
}
export function unlockUsers(ids, reason = '解锁') {
  return call('put', `/admin/users/${joinIds(ids)}/unlock`, { params: { reason } }, { ok: true });
}

// === 用户—角色绑定（UserRoleController）===

// 查询用户已绑定的角色（List<RoleSimpleDTO>）
export function getUserRoles(userId) {
  return call('get', `/admin/users/${userId}/roles`, {}, () => mockUserRoles[userId] || []);
}

// 绑定角色到用户（增量；后端 V1 是增量语义，前端做"全量替换"时需自行算 diff，或在 UI 上做穿梭框时
// 把"右侧全集"提交给后端，后端按"已存在则跳过、新增则添加"处理）
//   roleIds: ["R_CM","R_OG"]
//   reason:  必填（审计）
export function bindUserRoles(userId, roleIds, reason) {
  return call('post', `/admin/users/${userId}/roles`, {
    data: { roleIds, reason }
  }, { ok: true });
}

// 解绑用户的单个角色（reason 走 query）
export function unbindUserRole(userId, roleId, reason) {
  return call('delete', `/admin/users/${userId}/roles/${roleId}`, { params: { reason } }, { ok: true });
}

// "全量替换用户角色" 高频组合操作：内部做 diff，前端拿到原始已选 + 新选，
// 自动算出增量 add / del 两次调用，UI 只关心结果。
export async function replaceUserRoles(userId, newRoleIds, reason = '更新用户角色') {
  const before = await getUserRoles(userId).catch(() => []);
  const beforeIds = new Set((before || []).map(r => r.roleId));
  const targetIds = new Set(newRoleIds || []);
  const toAdd = [...targetIds].filter(id => !beforeIds.has(id));
  const toDel = [...beforeIds].filter(id => !targetIds.has(id));
  if (toAdd.length) await bindUserRoles(userId, toAdd, reason);
  for (const id of toDel) await unbindUserRole(userId, id, reason);
  return { added: toAdd.length, removed: toDel.length };
}
