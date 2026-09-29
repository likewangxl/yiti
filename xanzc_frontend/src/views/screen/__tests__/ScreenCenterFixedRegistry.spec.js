// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush, menuStore, permissionStore } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn(),
  menuStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    hasUrl: vi.fn()
  },
  permissionStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    canAccess: vi.fn()
  }
}));
vi.mock('@/api/screen', () => ({ listAvailableScreens }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => menuStore }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissionStore }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => ({ params: { template: 'branch-overview-v1' } })
}));

import ScreenCenter from '../ScreenCenter.vue';
import { useUserStore } from '@/stores/user';

const catalog = [
  {
    screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', viewLevel: 'PROVINCE', bizLine: 'COMMON',
    template: 'branch-overview-v1', dataMode: 'TEST'
  },
  {
    screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', viewLevel: 'BRANCH', bizLine: 'RETAIL',
    template: 'retail-overview-v1', dataMode: 'LIVE'
  },
  {
    screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', viewLevel: 'PROVINCE', bizLine: 'CORP',
    template: 'corporate-overview-v1', dataMode: 'LIVE'
  }
];

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  useUserStore().setUser({ empId: 'USER_A' });
  menuStore.loaded = true;
  menuStore.loading = false;
  menuStore.load.mockResolvedValue([]);
  menuStore.hasUrl.mockReturnValue(false);
  permissionStore.loaded = true;
  permissionStore.loading = false;
  permissionStore.load.mockResolvedValue([]);
  permissionStore.canAccess.mockReturnValue(true);
  listAvailableScreens.mockResolvedValue(catalog);
});
afterEach(() => wrapper?.unmount());

describe('ScreenCenter fixed code screens', () => {
  it('filters a legacy DEMO catalog item and tells the user the screen is not connected', async () => {
    listAvailableScreens.mockResolvedValue([{ ...catalog[2], dataMode: 'DEMO' }]);
    wrapper = mount(ScreenCenter); await flushPromises();
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(0);
    expect(wrapper.text()).toContain('尚未接入');
  });
  it('renders only registered templates with data mode labels and routes to protected pages', async () => {
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(3);
    expect(wrapper.text()).toContain('测试库数据');
    expect(wrapper.text()).toContain('已接入数据');
    expect(wrapper.text()).toContain('已接入数据');
    expect(wrapper.text()).toContain('统计口径与环境见来源说明');
    expect(wrapper.text()).not.toContain('旧发布屏');

    await wrapper.find('[data-screen-code="SCR_RETAIL_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      name: 'CodeScreenPage',
      params: { template: 'retail-overview-v1' }
    });

    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);

    await wrapper.find('[data-screen-code="SCR_PROVINCE"] button').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'CodeScreenPage', params: { template: 'branch-overview-v1' }
    });
  });

  it('目录缺少对公 LIVE 时不再派生支行卡片', async () => {
    listAvailableScreens.mockResolvedValue([{ ...catalog[2], dataMode: 'TEST' }]);
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);
  });

  it('目录缺少分行综合 TEST 时不再派生支行卡片', async () => {
    listAvailableScreens.mockResolvedValue([catalog[2]]);
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);
  });
});
