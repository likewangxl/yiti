import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { resetAuthorizationSnapshots } from './authorizationSnapshot';

const STORAGE_KEY = 'xanzc:user';

/**
 * 当前用户 store
 * - 持久化到 sessionStorage：刷新页面状态不丢，关闭浏览器后失效（与 yiti session 生命周期一致）
 * - 登录后由 LoginPage 写入；登出 / 401 由 http.js 清空
 */
export const useUserStore = defineStore('user', () => {
  const cached = sessionStorage.getItem(STORAGE_KEY);
  const user = ref(cached ? JSON.parse(cached) : null);

  const isLoggedIn = computed(() => !!user.value);
  const displayName = computed(() => user.value?.displayName || '未登录');
  const orgName = computed(() => user.value?.mainOrgName || '');
  // 用户全部已分配角色；取消会话角色切换后，权限由这些角色共同生效。
  const roles = computed(() => user.value?.roles || []);
  const roleSummary = computed(() => roles.value
    .map((role) => typeof role === 'string' ? role : (role.roleChName || role.roleCode || role.roleId))
    .filter(Boolean)
    .join('、'));
  // 兼容既有展示调用；语义已由“当前角色”改为“全部已分配角色摘要”。
  const roleName = computed(() => roleSummary.value);

  function hasRoleCode(...expectedCodes) {
    const expected = new Set(expectedCodes.flat().filter(Boolean));
    return roles.value.some((role) => {
      if (typeof role === 'string') return expected.has(role);
      return expected.has(role.roleCode) || expected.has(role.roleId);
    });
  }

  // 全角色并集语义下，只要任一已分配角色是 SYS_ADMIN 即按管理员展示。
  const isSystemAdmin = computed(() => {
    if (user.value?.isSystemAdmin === true) return true;
    return hasRoleCode('SYS_ADMIN');
  });

  function setUser(u) {
    // 登录（包括同一账号重新登录）和换用户都必须让旧菜单/资源快照及在途请求失效。
    resetAuthorizationSnapshots();
    user.value = u;
    if (u) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(u));
    else sessionStorage.removeItem(STORAGE_KEY);
  }

  function clear() {
    setUser(null);
  }

  return {
    user, isLoggedIn, displayName, orgName, roles, roleSummary, roleName,
    isSystemAdmin, hasRoleCode, setUser, clear
  };
});
