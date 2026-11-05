import http from './http';

export const LOGIN_SCREEN_TARGETS = Object.freeze([
  'WORKSPACE', 'PERSONAL', 'BRANCH', 'PROVINCE', 'RETAIL', 'CORPORATE'
]);

export function getLoginScreenRules() {
  // 规则是首屏偏好；403/404 由登录首屏解析器回退到固定大屏授权检查，
  // 因此抑制该次请求的全局提示（401 仍由 http 拦截器强制处理）。
  return http.get('/api/sys/login-screens', { silent: true });
}

export function getRoleLoginScreen(roleId) {
  return http.get(`/api/admin/sys/login-screens/${encodeURIComponent(String(roleId))}`);
}

export function saveRoleLoginScreen(roleId, data) {
  return http.put(`/api/admin/sys/login-screens/${encodeURIComponent(String(roleId))}`, data);
}
