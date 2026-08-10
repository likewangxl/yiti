/**
 * 校验路由声明的菜单入口与接口资源。
 * 返回 true 表示放行；返回路由对象表示 fail-close 后的安全落点。
 */
export async function resolveRouteAccess(to, menuStore, permissionStore) {
  const metas = to?.matched?.length
    ? to.matched.map((record) => record.meta || {})
    : [to?.meta || {}];
  const requiredMenus = [...new Set(metas.map((meta) => meta.requiredMenu).filter(Boolean))];
  const requiredResources = [...new Set(metas.map((meta) => meta.requiredResource).filter(Boolean))];
  const fallbackToAuthorizedMenu = metas.some((meta) => meta.fallbackToAuthorizedMenu === true);

  if (!requiredMenus.length && !requiredResources.length) return true;

  try {
    await menuStore.load();
  } catch (_) {
    return { path: '/no-access' };
  }
  if (requiredMenus.some((menuUrl) => !menuStore.hasUrl(menuUrl))) {
    if (fallbackToAuthorizedMenu) {
      const landingPath = menuStore.resolveLandingPath();
      return { path: landingPath === to?.path ? '/no-access' : landingPath };
    }
    return { path: '/no-access' };
  }

  if (!requiredResources.length) return true;
  try {
    await permissionStore.load();
  } catch (_) {
    return requiredMenus.length ? { path: '/redengine/dashboard' } : { path: '/no-access' };
  }
  if (requiredResources.some((resource) => !permissionStore.canAccess(resource))) {
    return requiredMenus.length ? { path: '/redengine/dashboard' } : { path: '/no-access' };
  }
  return true;
}

/** 路由没有声明菜单/资源要求时，无需实例化额外 Pinia store 或发权限请求。 */
export function hasRouteAccessRequirements(to) {
  const metas = to?.matched?.length
    ? to.matched.map((record) => record.meta || {})
    : [to?.meta || {}];
  return metas.some((meta) => meta.requiredMenu || meta.requiredResource);
}
