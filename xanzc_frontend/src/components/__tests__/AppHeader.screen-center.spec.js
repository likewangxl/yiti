// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { getMyPermissions } = vi.hoisted(() => ({ getMyPermissions: vi.fn() }));
vi.mock('@/api/auth', () => ({
  getMyPermissions,
  logout: vi.fn().mockResolvedValue(undefined)
}));
vi.mock('@/api/workspace', () => ({ getUnreadCount: vi.fn().mockResolvedValue(0) }));
vi.mock('@/api/users', () => ({ changeMyPassword: vi.fn().mockResolvedValue(undefined) }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}));

import AppHeader from '../AppHeader.vue';
import { useUserStore } from '@/stores/user';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  WorkspaceTabs: { name: 'WorkspaceTabs', template: '<nav aria-label="工作区页签" />' },
  'el-dropdown': { name: 'ElDropdown', template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem'),
  'el-badge': passthrough('ElBadge'),
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': { name: 'ElInput', template: '<input />' },
  'el-button': { name: 'ElButton', template: '<button><slot /></button>' }
};

let wrapper;
const routerPush = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  getMyPermissions.mockResolvedValue({ resourceUrls: ['/api/screen/view/*'] });
  setActivePinia(createPinia());
});
afterEach(() => wrapper?.unmount());

function mountHeader() {
  const pinia = createPinia();
  setActivePinia(pinia);
  useUserStore().setUser({ empId: 'USER_A', displayName: '测试用户', roles: [] });
  wrapper = mount(AppHeader, {
    global: {
      plugins: [pinia],
      stubs,
      mocks: { $router: { push: routerPush } }
    }
  });
  return wrapper;
}

describe('AppHeader 大屏中心快捷入口', () => {
  it('权限加载完成且可访问时显示入口并导航到 ScreenCenter', async () => {
    const header = mountHeader();
    await flushPromises();

    const shortcut = header.find('[data-testid="screen-center-shortcut"]');
    expect(shortcut.exists()).toBe(true);
    expect(shortcut.attributes('aria-label')).toBe('大屏中心');
    await shortcut.trigger('click');
    expect(routerPush).toHaveBeenCalledWith({ name: 'ScreenCenter' });
  });

  it('权限接口异常时隐藏入口并保持 fail-close', async () => {
    getMyPermissions.mockRejectedValueOnce(new Error('permission unavailable'));
    const header = mountHeader();
    await flushPromises();

    expect(header.find('[data-testid="screen-center-shortcut"]').exists()).toBe(false);
    expect(header.text()).not.toContain('大屏中心');
  });

  it('权限快照不包含大屏查看资源时隐藏入口', async () => {
    getMyPermissions.mockResolvedValueOnce({ resourceUrls: ['/api/report/read'] });
    const header = mountHeader();
    await flushPromises();

    expect(header.find('[data-testid="screen-center-shortcut"]').exists()).toBe(false);
  });
});
