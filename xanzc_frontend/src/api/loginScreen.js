import http from './http';

export const LOGIN_SCREEN_TARGETS = Object.freeze([
  'WORKSPACE', 'PERSONAL', 'BRANCH', 'PROVINCE', 'RETAIL', 'CORPORATE'
]);

export function getLoginScreenRules() {
  return http.get('/api/sys/login-screens');
}

export function getRoleLoginScreen(roleId) {
  return http.get(`/api/admin/sys/login-screens/${encodeURIComponent(String(roleId))}`);
}

export function saveRoleLoginScreen(roleId, data) {
  return http.put(`/api/admin/sys/login-screens/${encodeURIComponent(String(roleId))}`, data);
}
