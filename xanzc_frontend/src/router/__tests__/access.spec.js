import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/api/auth', () => ({
  getMyMenus: vi.fn(),
  getMyPermissions: vi.fn()
}));
import { getMyMenus } from '@/api/auth';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import { resolveRouteAccess } from '../access';

beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
});

function menuStore({ has = true, reject = false } = {}) {
  return {
    load: reject ? vi.fn().mockRejectedValue(new Error('menu failed')) : vi.fn().mockResolvedValue(),
    hasUrl: vi.fn().mockReturnValue(has),
    resolveLandingPath: vi.fn().mockReturnValue('/redengine/dashboard')
  };
}

function permissionStore({ allowed = true, reject = false } = {}) {
  return {
    load: reject ? vi.fn().mockRejectedValue(new Error('permission failed')) : vi.fn().mockResolvedValue(),
    canAccess: vi.fn().mockReturnValue(allowed)
  };
}

describe('红色引擎路由授权', () => {
  const protectedRoute = {
    path: '/redengine/review',
    matched: [
      { meta: { requiredMenu: '/redengine/dashboard' } },
      { meta: { requiredResource: '/api/re/reviews/**' } }
    ]
  };

  it('缺少模块菜单或菜单加载失败时进入 no-access', async () => {
    expect(await resolveRouteAccess(protectedRoute, menuStore({ has: false }), permissionStore()))
      .toEqual({ path: '/no-access' });
    expect(await resolveRouteAccess(protectedRoute, menuStore({ reject: true }), permissionStore()))
      .toEqual({ path: '/no-access' });
  });

  it('拥有模块入口但缺子资源或权限加载失败时回红色工作台', async () => {
    expect(await resolveRouteAccess(protectedRoute, menuStore(), permissionStore({ allowed: false })))
      .toEqual({ path: '/redengine/dashboard' });
    expect(await resolveRouteAccess(protectedRoute, menuStore(), permissionStore({ reject: true })))
      .toEqual({ path: '/redengine/dashboard' });
  });

  it('菜单和资源均满足时放行', async () => {
    expect(await resolveRouteAccess(protectedRoute, menuStore(), permissionStore())).toBe(true);
  });

  it('UIAS 固定落到 workspace 但用户无该菜单时，进入首个授权叶子', async () => {
    const workspaceRoute = {
      path: '/workspace',
      matched: [{
        meta: {
          requiredMenu: '/workspace',
          fallbackToAuthorizedMenu: true
        }
      }]
    };
    const menus = menuStore({ has: false });
    expect(await resolveRouteAccess(workspaceRoute, menus, permissionStore()))
      .toEqual({ path: '/redengine/dashboard' });
    expect(menus.resolveLandingPath).toHaveBeenCalledWith();
  });

  it('使用真实菜单 store 时也能把 workspace 落点改投首个党建授权叶子', async () => {
    getMyMenus.mockResolvedValue([
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎' }
    ]);
    const workspaceRoute = {
      path: '/workspace',
      matched: [{ meta: { requiredMenu: '/workspace', fallbackToAuthorizedMenu: true } }]
    };
    expect(await resolveRouteAccess(workspaceRoute, useMenuStore(), usePermissionStore()))
      .toEqual({ path: '/redengine/dashboard' });
  });
});
