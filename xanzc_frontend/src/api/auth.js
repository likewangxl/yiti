import http, { API_BASE, USE_MOCK, call } from './http';

/**
 * 登录
 * 后端 POST /api/auth/login，返回 LoginRespDTO（empId/username/displayName/mainOrgCode/mainOrgName/roles/token）
 * yiti 会同时下发 SESSION cookie，axios withCredentials:true 会自动带上
 *
 * 注：登录走真实 http（不走 call() 的 mock fallback），失败要让用户看到。
 * mock 模式下伪造一份 user 直接返回，前端流程能跑通。
 */
export async function login(username, password) {
  if (USE_MOCK) {
    return {
      empId: 'admin', username, displayName: '系统管理员（mock）',
      mainOrgCode: 'HQ', mainOrgName: '总行',
      roles: [{ roleId: 'R_ADMIN', roleCode: 'SYS_ADMIN', roleChName: '系统管理员' }],
      token: 'mock-token'
    };
  }
  return http.post(API_BASE + '/auth/login', { username, password });
}

/**
 * 统一认证登录（开发期 mock）：仅传工号，不传密码。
 * 后端 POST /api/auth/uniauth/login，通过边车 11003 调 S120030044 查 UIAS 授权 + 建 session。
 * 生产应改为 UIAS 单点重定向流程（参考 xanpd 的 /login/redirect）。
 */
export async function uniAuthLogin(userDomainName) {
  if (USE_MOCK) {
    return {
      empId: userDomainName, username: userDomainName, displayName: 'UIAS 用户（mock）',
      mainOrgCode: 'HQ', mainOrgName: '总行',
      roles: [{ roleId: 'R_ADMIN', roleCode: 'SYS_ADMIN', roleChName: '系统管理员' }],
      token: 'mock-token'
    };
  }
  return http.post(API_BASE + '/auth/uniauth/login', { userDomainName });
}

/**
 * 登出：销毁 yiti session
 */
export function logout() {
  if (USE_MOCK) return Promise.resolve({ ok: true });
  return http.post(API_BASE + '/auth/logout');
}

/**
 * 取当前用户（用于页面刷新后恢复 store）
 * mock 模式下返回 mock 用户；真模式下走 call() 的 fallback 机制更稳
 */
export function getCurrentUser() {
  return call('get', '/auth/current-user', {}, {
    empId: 'mock', username: 'mock', displayName: '张三',
    mainOrgCode: '0010', mainOrgName: '南山支行',
    roles: [{ roleId: 'R_CM', roleCode: 'CM', roleChName: '客户经理' }]
  });
}

export function getMyPermissions() {
  return call('get', '/auth/permissions', {}, { resources: [], scopes: [] });
}
