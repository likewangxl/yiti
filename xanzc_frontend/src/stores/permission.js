import { defineStore } from 'pinia';
import { onScopeDispose, ref } from 'vue';
import { getMyPermissions } from '@/api/auth';
import { matchesRequiredResource } from '@/utils/resourcePermission';
import { registerAuthorizationReset } from './authorizationSnapshot';

/**
 * 当前登录用户的权限快照。
 * 红色引擎布局与路由守卫共用此 store，保证同一时刻只发一个权限请求。
 */
export const usePermissionStore = defineStore('permission', () => {
  const resourceUrls = ref(new Set());
  const loaded = ref(false);
  const loading = ref(false);
  const isSystemAdmin = ref(false);
  let pendingLoad = null;
  let requestGeneration = 0;

  function clear() {
    requestGeneration += 1;
    pendingLoad = null;
    loading.value = false;
    resourceUrls.value = new Set();
    isSystemAdmin.value = false;
    loaded.value = false;
  }

  const unregisterReset = registerAuthorizationReset(clear);
  onScopeDispose(unregisterReset);

  async function load(force = false) {
    if (force) clear();
    if (loaded.value && !force) return;
    if (pendingLoad) return pendingLoad;
    const generation = ++requestGeneration;
    loading.value = true;
    pendingLoad = (async () => {
      try {
        const permission = await getMyPermissions();
        if (generation !== requestGeneration) return permission;
        resourceUrls.value = new Set(permission?.resourceUrls || permission?.resources || []);
        isSystemAdmin.value = permission?.isSystemAdmin === true;
        loaded.value = true;
        return permission;
      } catch (error) {
        if (generation !== requestGeneration) throw error;
        resourceUrls.value = new Set();
        isSystemAdmin.value = false;
        loaded.value = false;
        throw error;
      } finally {
        if (generation === requestGeneration) {
          loading.value = false;
          pendingLoad = null;
        }
      }
    })();
    return pendingLoad;
  }

  function canAccess(requiredResource) {
    if (!loaded.value) return false;
    if (isSystemAdmin.value) return true;
    return matchesRequiredResource(requiredResource, resourceUrls.value);
  }

  return { resourceUrls, loaded, loading, isSystemAdmin, load, canAccess, clear };
});
