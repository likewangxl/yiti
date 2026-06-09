import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

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
  // 用户全部已分配角色（角色下拉用）
  const roles = computed(() => user.value?.roles || []);
  // 当前激活角色ID：切换后为所切角色，否则回退主角色 / 第一个角色
  const activeRoleId = computed(() =>
    user.value?.activeRoleId || user.value?.primaryRoleId || user.value?.roles?.[0]?.roleId || '');
  // 当前角色名（顶栏显示），跟随激活角色
  const roleName = computed(() => {
    const rs = user.value?.roles || [];
    const r = rs.find(x => x.roleId === activeRoleId.value) || rs[0];
    return r?.roleChName || '';
  });
  // 是否系统管理员：当前激活角色为 SYS_ADMIN（与角色切换/后端 RBAC 口径一致）
  const isSystemAdmin = computed(() => {
    if (user.value?.isSystemAdmin === true) return true;
    const rs = user.value?.roles || [];
    const r = rs.find(x => x.roleId === activeRoleId.value) || rs[0];
    return (r?.roleCode || '') === 'SYS_ADMIN';
  });

  function setUser(u) {
    user.value = u;
    if (u) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(u));
    else sessionStorage.removeItem(STORAGE_KEY);
  }

  // 切换当前激活角色（写回持久化，配合页面刷新后菜单/权限按新角色重取）
  function setActiveRole(roleId) {
    if (!user.value) return;
    user.value = { ...user.value, activeRoleId: roleId };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(user.value));
  }

  function clear() {
    setUser(null);
  }

  return { user, isLoggedIn, displayName, orgName, roles, activeRoleId, roleName, isSystemAdmin, setUser, setActiveRole, clear };
});
