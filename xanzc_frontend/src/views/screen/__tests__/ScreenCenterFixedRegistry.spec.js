// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush, menuStore } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn(),
  menuStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    hasUrl: vi.fn()
  }
}));
vi.mock('@/api/screen', () => ({ listAvailableScreens }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => menuStore }));
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

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(4);
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

    const branchCard = wrapper.find('[data-screen-kind="branch-operating"]');
    expect(branchCard.exists()).toBe(true);
    expect(branchCard.find('.screen-card__mode-badge').text()).toBe('测试数据');
    expect(branchCard.find('.screen-card__description').text()).toContain('数据库测试场景');
    expect(branchCard.find('.screen-card__description').text()).toContain('系统存量');
    expect(branchCard.find('.screen-card__description').exists()).toBe(true);
    await branchCard.find('button.screen-card__open').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({ name: 'BranchOperatingPage' });
  });

  it('没有对公 LIVE 目录授权时不派生支行卡片', async () => {
    listAvailableScreens.mockResolvedValue([{ ...catalog[2], dataMode: 'TEST' }]);
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);
  });
});
