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
  const roleName = computed(() => user.value?.roles?.[0]?.roleChName || '');

  function setUser(u) {
    user.value = u;
    if (u) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(u));
    else sessionStorage.removeItem(STORAGE_KEY);
  }

  function clear() {
    setUser(null);
  }

  return { user, isLoggedIn, displayName, orgName, roleName, setUser, clear };
});
