// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const routerReplace = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace })
}));
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue(undefined),
  getMyMenus: vi.fn()
}));

import { getMyMenus, logout } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import NoAccess from '../NoAccess.vue';

const stubs = {
  'el-button': {
    name: 'ElButton',
    props: ['loading'],
    emits: ['click'],
    template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>'
  }
};

let wrapper;
let locationReplace;

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  const pinia = createPinia();
  setActivePinia(pinia);
  locationReplace = vi.fn();
  Object.defineProperty(window, 'location', {
    value: { replace: locationReplace },
    writable: true,
    configurable: true
  });
  useUserStore().setUser({ empId: 'E001', displayName: '测试用户' });
  wrapper = mount(NoAccess, { global: { plugins: [pinia], stubs } });
});

afterEach(() => wrapper?.unmount());

describe('NoAccess 安全恢复入口', () => {
  it('提供可读主标题和权限恢复操作组', () => {
    expect(wrapper.find('main[aria-labelledby="no-access-title"]').exists()).toBe(true);
    expect(wrapper.find('h1#no-access-title').exists()).toBe(true);
    expect(wrapper.find('[aria-label="权限恢复操作"]').exists()).toBe(true);
  });

  it('说明权限可能未分配或加载失败，并可重试进入首个授权菜单', async () => {
    expect(wrapper.text()).toContain('权限信息加载失败');
    expect(wrapper.text()).toContain('重新加载权限');
    getMyMenus.mockResolvedValue([
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎' }
    ]);

    await wrapper.findAll('button').find((button) => button.text().includes('重新加载')).trigger('click');
    await flushPromises();
    expect(routerReplace).toHaveBeenCalledWith('/redengine/dashboard');
  });

  it('可安全退出并按 hash 地址整页返回平台登录页', async () => {
    await wrapper.findAll('button').find((button) => button.text().includes('退出')).trigger('click');
    await flushPromises();
    expect(logout).toHaveBeenCalled();
    expect(useUserStore().user).toBeNull();
    expect(locationReplace).toHaveBeenCalledWith('/#/login');
  });
});
