// @vitest-environment happy-dom
import { beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
vi.mock('@/api/http', () => ({ default: { get: vi.fn() } }));
vi.mock('@/api/auth', () => ({ getMyMenus: vi.fn(), getMyPermissions: vi.fn() }));
import http from '@/api/http';
import { getMyMenus, getMyPermissions } from '@/api/auth';
import router from '../index';
import { useUserStore } from '@/stores/user';
import { markLoginScreenShown, resetLoginScreenShown } from '@/utils/loginScreen';
beforeEach(async () => {
  sessionStorage.clear(); setActivePinia(createPinia()); vi.clearAllMocks();
  getMyMenus.mockResolvedValue([{ resourceUrl: '/workspace', menuName: '工作台' }]);
  getMyPermissions.mockResolvedValue({ resourceUrls: [] });
  http.get.mockResolvedValue({ empId: 'UIAS', roles: [] });
  await router.push('/login');
});
it('统一认证恢复新身份到工作台时进入首屏选择，已处理会话不重弹', async () => {
  await router.push('/workspace');
  expect(router.currentRoute.value.path).toBe('/login-landing');
  markLoginScreenShown(useUserStore().user); useUserStore().clear();
  await router.push('/workspace');
  expect(router.currentRoute.value.path).toBe('/workspace');
  resetLoginScreenShown();
});
it('恢复会话到业务深链接不强插首屏', async () => {
  getMyMenus.mockResolvedValue([{ resourceUrl: '/workspace', menuName: '工作台' }, { resourceUrl: '/system/roles', menuName: '角色' }]);
  await router.push('/system/roles');
  expect(router.currentRoute.value.path).toBe('/system/roles');
});
